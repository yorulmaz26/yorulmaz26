package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.model.sum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    @Test fun `toplam kuruş cinsinden kayıpsız`() {
        assertEquals(Kurus(35050), listOf(Kurus(25050), Kurus(10000)).sum())
    }

    @Test fun `TRY biçimlendirme`() {
        val text = Kurus(125050).formatTry()
        assertTrue(text.contains("1.250,50"), text)
        assertTrue(text.contains("₺") || text.contains("TL"), text)
    }

    @Test fun `kullanıcı girişi ayrıştırma`() {
        assertEquals(Kurus(25000), Kurus.parseTry("250"))
        assertEquals(Kurus(25050), Kurus.parseTry("250,5"))
        assertEquals(Kurus(125050), Kurus.parseTry("1.250,50"))
        assertEquals(Kurus(125050), Kurus.parseTry("₺ 1250,50"))
        assertNull(Kurus.parseTry("12,345"))
        assertNull(Kurus.parseTry("abc"))
        assertNull(Kurus.parseTry(""))
        assertNull(Kurus.parseTry("1.25,00"))
    }

    @Test(expected = ArithmeticException::class)
    fun `taşma algılanır`() {
        Kurus(Long.MAX_VALUE) + Kurus(1)
    }
}
