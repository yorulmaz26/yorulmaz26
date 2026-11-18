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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * İki telefon arasında yerel ağ (Wi-Fi ya da hotspot) bağlantısı. İnternet ya da sunucu gerekmez.
 *
 *  - Oda kuran telefon bir TCP portu açar ve "buradayım" mesajını UDP ile ağa yayınlar.
 *  - Katılan telefon bu yayını dinler, gelen adrese TCP ile bağlanır.
 *  - Mesajlar tek satırlık metinlerdir ("S|450|12|0|3|1" gibi). Oyun döngüsü [events] kuyruğunu her karede okur.
 *
 * Tüm ağ işleri arka plan iş parçacıklarında yapılır; oyun döngüsü hiçbir zaman beklemez.
 */
class NetLink(context: Context) {

    sealed class Event {
        object Connected : Event()
        class Message(val parts: List<String>) : Event()
        class Failed(val reason: String) : Event()
        object Disconnected : Event()
    }

    companion object {
        private const val DISCOVERY_PORT = 47011
        private const val MAGIC = "GKACIS"
        const val PROTOCOL = 1
        private const val READ_TIMEOUT_MS = 6000
        private const val HEARTBEAT_MS = 1000L
    }

    val events = ConcurrentLinkedQueue<Event>()

    @Volatile var isHost = false
        private set
    @Volatile var connected = false
        private set
    /** Oda kuran telefonun yerel IP adresi (ekranda gösterilir). */
    @Volatile var localAddress: String = ""
        private set

    @Volatile private var closed = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var udp: DatagramSocket? = null
    private val outQueue = LinkedBlockingQueue<String>()

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
    private val multicastLock = wifi?.createMulticastLock("gezegen-kacisi")?.apply { setReferenceCounted(false) }

    // ---------------------------------------------------------------------
    // Oda kurma / katılma
    // ---------------------------------------------------------------------

    fun host() {
        isHost = true
        localAddress = findLocalAddress()
        thread(name = "gk-host", isDaemon = true) {
            try {
                val ss = ServerSocket(0)
                server = ss
                val port = ss.localPort
                thread(name = "gk-beacon", isDaemon = true) { beacon(port) }
                val s = ss.accept()
                runCatching { ss.close() }
                server = null
                onSocket(s)
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
                        onSocket(s)
                        return@thread
                    } catch (_: Exception) {
                        // Başka bir yayın gelirse yeniden denenir
                    }
                }
            } catch (e: Exception) {
                if (!closed) events.add(Event.Failed("Ağ dinlenemiyor"))
            } finally {
                runCatching { multicastLock?.release() }
            }
        }
    }

    /** Oda kuran telefon, katılan bağlanana kadar her 0,6 saniyede bir kendini duyurur. */
    private fun beacon(port: Int) {
        val ds = try {
            DatagramSocket().apply { broadcast = true }
        } catch (e: Exception) {
            return
        }
        val msg = "$MAGIC|$PROTOCOL|$port".toByteArray()
        try {
            while (!closed && !connected) {
                for (addr in broadcastAddresses()) {
                    runCatching { ds.send(DatagramPacket(msg, msg.size, addr, DISCOVERY_PORT)) }
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
    // Bağlantı
    // ---------------------------------------------------------------------

    private fun onSocket(s: Socket) {
        if (closed) {
            runCatching { s.close() }
            return
        }
        s.tcpNoDelay = true
        s.soTimeout = READ_TIMEOUT_MS
        socket = s
        connected = true
        events.add(Event.Connected)

        thread(name = "gk-writer", isDaemon = true) {
            try {
                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                while (!closed) {
                    val line = outQueue.poll(HEARTBEAT_MS, TimeUnit.MILLISECONDS) ?: "P"
                    w.write(line)
                    w.write('\n'.code)
                    // Kuyrukta bekleyenleri de aynı paketle gönder
                    while (true) {
                        val more = outQueue.poll() ?: break
                        w.write(more)
                        w.write('\n'.code)
                    }
                    w.flush()
                }
            } catch (_: Exception) {
                lost()
            }
        }
        thread(name = "gk-reader", isDaemon = true) {
            try {
                val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (!closed) {
                    val line = r.readLine() ?: break
                    if (line == "P" || line.isEmpty()) continue
                    events.add(Event.Message(line.split('|')))
                }
            } catch (_: Exception) {
            }
            lost()
        }
    }

    private fun lost() {
        if (closed) return
        closed = true
        connected = false
        runCatching { socket?.close() }
        events.add(Event.Disconnected)
    }

    fun send(vararg parts: Any) {
        if (!connected) return
        outQueue.offer(parts.joinToString("|"))
    }

    /** Bağlantıyı kapatır. Bundan sonra olay üretilmez. */
    fun close() {
        if (connected) {
            // Karşı tarafa nazikçe haber ver; ulaşmazsa zaman aşımıyla anlar
            outQueue.offer("BYE")
            Thread.sleep(30)
        }
        closed = true
        connected = false
        runCatching { server?.close() }
        runCatching { socket?.close() }
        runCatching { udp?.close() }
        runCatching { multicastLock?.release() }
        events.clear()
    }
}
