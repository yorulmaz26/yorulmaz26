package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.link.DeepLink
import com.kuaforumcepte.domain.link.DeepLinks
import com.kuaforumcepte.domain.qr.QrCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksAndQrTest {
    private val host = "kuaforumcepte.web.app"

    @Test fun `salon bağlantısı üretimi ve ayrıştırma`() {
        val url = DeepLinks.salonUrl(host, "482913")
        assertEquals("https://kuaforumcepte.web.app/s/482913", url)
        assertEquals(DeepLink.Salon("482913"), DeepLinks.parse(url, host))
        assertEquals(DeepLink.Salon("482913"), DeepLinks.parse("https://KUAFORUMCEPTE.web.app/s/482913/", host))
        assertEquals(DeepLink.Salon("482913"), DeepLinks.parse("kuaforumcepte://salon/482913", host))
        assertEquals(DeepLink.Appointment("ap_1"), DeepLinks.parse(DeepLinks.appointmentUri("ap_1"), host))
    }

    @Test fun `yabancı alan adı ve bozuk kod reddedilir`() {
        assertNull(DeepLinks.parse("https://evil.example/s/482913", host))
        assertNull(DeepLinks.parse("http://kuaforumcepte.web.app/s/482913", host))
        assertNull(DeepLinks.parse("https://kuaforumcepte.web.app/s/012345", host))
        assertNull(DeepLinks.parse("https://kuaforumcepte.web.app/x/482913", host))
        assertNull(DeepLinks.parse("kuaforumcepte://salon/abc", host))
        assertNull(DeepLinks.parse("kuaforumcepte://appointment/../../x", host))
        assertNull(DeepLinks.parse("not a uri ::", host))
        assertNull(DeepLinks.parse(null, host))
    }

    @Test fun `QR üretilir ve geri okunabilir`() {
        val url = DeepLinks.salonUrl(host, "482913")
        val img = QrCodes.encode(url, size = 512)
        assertEquals(512 * 512, img.pixels.size)
        assertTrue(QrCodes.verify(img, url))
        assertFalse(QrCodes.verify(img, "https://baska"))
    }

    @Test fun `bozulmuş QR doğrulamadan geçmez`() {
        val img = QrCodes.encode("https://kuaforumcepte.web.app/s/482913", size = 256)
        val blank = QrCodes.QrImage(img.size, IntArray(img.pixels.size) { -1 })
        assertNull(QrCodes.decode(blank))
    }
}
