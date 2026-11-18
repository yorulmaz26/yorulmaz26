package com.yanlisrenk.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.yanlisrenk.logic.life.LifeManager
import com.yanlisrenk.logic.life.LifeState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress

/**
 * Yanlış Renk küçük arka ucu (bağımlılıksız, JDK HttpServer).
 *
 *  GET  /time                → {"now": epochMillis}            Güvenilir zaman kaynağı
 *  POST /lives/verify        → sunucu tarafında can yenileme hesabı
 *  POST /purchase/verify     → Google Play satın alma doğrulaması için yer tutucu (501)
 *  GET  /health              → "ok"
 *
 * Üretimde HTTPS arkasında (ör. Cloud Run / ters vekil) çalıştırılmalıdır.
 * İstemci adresi android tarafında BuildConfig/manifest yerine `TrustedTimeFetcher.backendUrl` ile verilir.
 */
@Serializable data class TimeResponse(val now: Long)
@Serializable data class LivesRequest(val lives: Int, val regenRemaining: Long, val lastServerTime: Long)
@Serializable data class LivesResponse(val lives: Int, val regenRemaining: Long, val serverTime: Long)

object LivesAuthority {
    /** İstemcinin son doğrulanmış sunucu zamanından bu yana geçen süreyi uygular. */
    fun verify(req: LivesRequest, now: Long): LivesResponse {
        val st = LifeState(req.lives.coerceIn(0, 4), req.regenRemaining.coerceIn(1, LifeManager.REGEN_MS))
        val elapsed = (now - req.lastServerTime).coerceAtLeast(0)
        LifeManager(st).credit(elapsed)
        return LivesResponse(st.lives, st.regenRemaining, now)
    }
}

class TimeServer(port: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)

    init {
        server.createContext("/time") { ex -> respond(ex, 200, json.encodeToString(TimeResponse.serializer(), TimeResponse(clock()))) }
        server.createContext("/health") { ex -> respond(ex, 200, "ok", "text/plain") }
        server.createContext("/lives/verify") { ex ->
            if (ex.requestMethod != "POST") return@createContext respond(ex, 405, "{}")
            val req = runCatching { json.decodeFromString(LivesRequest.serializer(), ex.requestBody.readBytes().decodeToString()) }.getOrNull()
                ?: return@createContext respond(ex, 400, """{"error":"geçersiz istek"}""")
            respond(ex, 200, json.encodeToString(LivesResponse.serializer(), LivesAuthority.verify(req, clock())))
        }
        server.createContext("/purchase/verify") { ex ->
            respond(ex, 501, """{"error":"Google Play Developer API kimlik bilgileri yapılandırılmadı"}""")
        }
    }

    private fun respond(ex: HttpExchange, code: Int, body: String, type: String = "application/json") {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "$type; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    fun start() = server.start()
    fun stop() = server.stop(0)
}

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: System.getenv("PORT")?.toIntOrNull() ?: 8080
    TimeServer(port).start()
    println("Yanlış Renk zaman sunucusu :$port")
}
