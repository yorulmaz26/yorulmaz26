package com.yanlisrenk.server

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

class TimeServerTest {
    @Test fun zamanVeCanDogrulama() {
        val srv = TimeServer(0) { 1_000_000_000L }
        srv.start()
        try {
            val port = srv.server.address.port
            val c = URI("http://127.0.0.1:$port/time").toURL().openConnection() as HttpURLConnection
            assertEquals(200, c.responseCode)
            assertTrue(c.inputStream.readBytes().decodeToString().contains("1000000000"))
            val p = URI("http://127.0.0.1:$port/lives/verify").toURL().openConnection() as HttpURLConnection
            p.requestMethod = "POST"; p.doOutput = true
            p.outputStream.write("""{"lives":0,"regenRemaining":900000,"lastServerTime":${1_000_000_000L - 31 * 60_000L}}""".toByteArray())
            val body = p.inputStream.readBytes().decodeToString()
            assertTrue(body, body.contains("\"lives\":2"))
        } finally { srv.stop() }
    }
}
