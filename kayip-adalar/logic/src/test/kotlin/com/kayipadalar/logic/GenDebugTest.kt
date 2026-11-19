package com.kayipadalar.logic

import com.kayipadalar.logic.level.*
import org.junit.Test

class GenDebugTest {
    @Test fun debug() {
        val id = System.getProperty("debugLevel")?.toInt() ?: return
        val gen = LevelGenerator(TestAssets.config)
        val r = runCatching { gen.generateMain(id) }
        println("SONUÇ: ${r.isSuccess} ${r.exceptionOrNull()?.message}")
        println("RED: " + gen.rejections.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
    }
}
