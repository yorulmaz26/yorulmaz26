package com.example.gezegenkacisi

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 2-4 telefon arasında yerel ağ (Wi-Fi ya da hotspot) bağlantısı. İnternet ya da sunucu gerekmez.
 *
 *  - Oda kuran telefon (oyuncu 0) bir TCP portu açar ve "buradayım" mesajını UDP ile ağa yayınlar.
 *  - Katılan telefonlar bu yayını dinler, gelen adrese TCP ile bağlanır. En fazla 3 kişi katılabilir.
 *  - Bağlantı yıldız biçimindedir: katılanlar yalnızca kurucuyla konuşur, kurucu mesajları diğerlerine iletir.
 *  - Mesajlar tek satırlık metinlerdir ("S|2|450|12|0|3" gibi). Oyun döngüsü [events] kuyruğunu her karede okur.
 *
 * Tüm ağ işleri arka plan iş parçacıklarında yapılır; oyun döngüsü hiçbir zaman beklemez.
 */
class NetLink(context: Context) {

    sealed class Event {
        /** Katılan telefon kurucuya bağlandı. */
        object Connected : Event()
        /** (Kurucu) [id] numaralı oyuncu odaya girdi. */
        class Joined(val id: Int) : Event()
        /** (Kurucu) [id] numaralı oyuncunun bağlantısı koptu. */
        class Left(val id: Int) : Event()
        /** [from]: mesajın geldiği bağlantı (katılanlar için her zaman 0, yani kurucu). */
        class Message(val from: Int, val parts: List<String>) : Event()
        class Failed(val reason: String) : Event()
        /** (Katılan) kurucuyla bağlantı koptu. */
        object Lost : Event()
    }

    companion object {
        private const val DISCOVERY_PORT = 47011
        private const val MAGIC = "GKACIS"
        const val PROTOCOL = 2
        const val MAX_PLAYERS = 4
        private const val READ_TIMEOUT_MS = 6000
        private const val HEARTBEAT_MS = 1000L
    }

    val events = ConcurrentLinkedQueue<Event>()

    @Volatile var isHost = false
        private set
    /** Katılan için: kurucuya bağlı mı. Kurucu için: oda açık mı. */
    @Volatile var connected = false
        private set
    /** Kurucu yeni oyuncu kabul ediyor mu (maç başlayınca kapatılır). */
    @Volatile var open = true
    /** Bu telefonun yerel IP adresi (ekranda gösterilir). */
    @Volatile var localAddress: String = ""
        private set

    @Volatile private var closed = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    private val conns = ConcurrentHashMap<Int, Conn>()

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    private val multicastLock = wifi?.createMulticastLock("gezegen-kacisi")?.apply { setReferenceCounted(false) }

    /** Tek bir TCP bağlantısı: kendi yazma kuyruğu ve okuma/yazma iş parçacıkları. */
    private inner class Conn(val id: Int, val socket: Socket) {
        val out = LinkedBlockingQueue<String>()
        @Volatile var alive = true

        fun start() {
            socket.tcpNoDelay = true
            socket.soTimeout = READ_TIMEOUT_MS
            thread(name = "gk-w$id", isDaemon = true) {
                try {
                    val w = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                    while (alive && !closed) {
                        val line = out.poll(HEARTBEAT_MS, TimeUnit.MILLISECONDS) ?: "P"
                        w.write(line)
                        w.write('\n'.code)
                        while (true) {
                            val more = out.poll() ?: break
                            w.write(more)
                            w.write('\n'.code)
                        }
                        w.flush()
                    }
                } catch (_: Exception) {
                }
                lost(this)
            }
            thread(name = "gk-r$id", isDaemon = true) {
                try {
                    val r = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    while (alive && !closed) {
                        val line = r.readLine() ?: break
                        if (line == "P" || line.isEmpty()) continue
                        // Kurucu, oyuncuların konum ve yanma mesajlarını burada hemen diğerlerine aktarır:
                        // kurucu uygulaması arka plandayken (oyun döngüsü durmuşken) de maç takılmaz
                        if (isHost && (line.startsWith("S|") || line.startsWith("D|"))) {
                            for (c in conns.values) if (c.id != id) c.out.offer(line)
                        }
                        events.add(Event.Message(id, line.split('|')))
                    }
                } catch (_: Exception) {
                }
                lost(this)
            }
        }

        fun close() {
            alive = false
            runCatching { socket.close() }
        }
    }

    // ---------------------------------------------------------------------
    // Oda kurma / katılma
    // ---------------------------------------------------------------------

    fun host() {
        isHost = true
        connected = true
        localAddress = findLocalAddress()
        thread(name = "gk-host", isDaemon = true) {
            try {
                val ss = ServerSocket(0)
                server = ss
                val port = ss.localPort
                thread(name = "gk-beacon", isDaemon = true) { beacon(port) }
                while (!closed) {
                    val s = ss.accept()
                    val id = (1 until MAX_PLAYERS).firstOrNull { it !in conns.keys }
                    if (!open || id == null || closed) {
                        runCatching { s.close() }
                        continue
                    }
                    val c = Conn(id, s)
                    conns[id] = c
                    c.start()
                    c.out.offer("W|$id") // oyuncu numarası
                    events.add(Event.Joined(id))
                }
            } catch (e: Exception) {
                if (!closed) events.add(Event.Failed("Oda kurulamadı"))
            }
        }
    }

    fun join() {
        isHost = false
        localAddress = findLocalAddress()
        runCatching { multicastLock?.acquire() }
        thread(name = "gk-join", isDaemon = true) {
            try {
                val ds = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                udp = ds
                val buf = ByteArray(256)
                while (!closed && !connected) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        ds.receive(pkt)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val parts = String(pkt.data, 0, pkt.length).split('|')
                    if (parts.size < 3 || parts[0] != MAGIC || parts[1] != PROTOCOL.toString()) continue
                    val port = parts[2].toIntOrNull() ?: continue
                    try {
                        val s = Socket()
                        s.connect(InetSocketAddress(pkt.address, port), 4000)
                        runCatching { ds.close() }
                        udp = null
                        if (closed) {
                            runCatching { s.close() }
                            return@thread
                        }
                        val c = Conn(0, s)
                        conns[0] = c
                        connected = true
                        c.start()
                        events.add(Event.Connected)
                        return@thread
                    } catch (_: Exception) {
                        // Oda dolu ya da ulaşılamıyor; başka bir yayın gelirse yeniden denenir
                    }
                }
            } catch (e: Exception) {
                if (!closed) events.add(Event.Failed("Ağ dinlenemiyor"))
            } finally {
                runCatching { multicastLock?.release() }
            }
        }
    }

    /** Oda açık ve dolu değilken her 0,6 saniyede bir kendini duyurur. */
    private fun beacon(port: Int) {
        val ds = try {
            DatagramSocket().apply { broadcast = true }
        } catch (e: Exception) {
            return
        }
        val msg = "$MAGIC|$PROTOCOL|$port".toByteArray()
        try {
            while (!closed) {
                if (open && conns.size < MAX_PLAYERS - 1) {
                    for (addr in broadcastAddresses()) {
                        runCatching { ds.send(DatagramPacket(msg, msg.size, addr, DISCOVERY_PORT)) }
                    }
                }
                Thread.sleep(600)
            }
        } catch (_: InterruptedException) {
        } finally {
            ds.close()
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { out.add(it) }
                }
            }
        }
        return out
    }

    private fun findLocalAddress(): String {
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress ?: ""
                }
            }
        }
        return ""
    }

    // ---------------------------------------------------------------------
    // Mesajlaşma
    // ---------------------------------------------------------------------

    private fun lost(c: Conn) {
        if (conns.remove(c.id, c)) {
            c.close()
            if (closed) return
            if (isHost) {
                events.add(Event.Left(c.id))
            } else {
                connected = false
                events.add(Event.Lost)
            }
        }
    }

    /** Kurucu: herkese gönderir. Katılan: kurucuya gönderir. */
    fun send(vararg parts: Any) {
        val line = parts.joinToString("|")
        for (c in conns.values) c.out.offer(line)
    }

    /** Kurucu: [except] dışındaki herkese hazır bir satırı iletir (katılanlar arası aktarma). */
    fun relay(except: Int, parts: List<String>) {
        if (!isHost) return
        val line = parts.joinToString("|")
        for (c in conns.values) if (c.id != except) c.out.offer(line)
    }

    /** Bağlantıyı kapatır. Bundan sonra olay üretilmez. */
    fun close() {
        if (closed) return
        val hadPeers = conns.isNotEmpty()
        for (c in conns.values) c.out.offer("BYE")
        if (hadPeers) Thread.sleep(30)
        closed = true
        connected = false
        runCatching { server?.close() }
        runCatching { udp?.close() }
        for (c in conns.values) c.close()
        conns.clear()
        runCatching { multicastLock?.release() }
        events.clear()
    }
}
