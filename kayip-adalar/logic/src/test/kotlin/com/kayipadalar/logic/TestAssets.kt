package com.kayipadalar.logic

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.data.GameJson
import com.kayipadalar.logic.level.AreaFile
import java.io.File

object TestAssets {
    val dir = File(System.getProperty("assets.dir") ?: "../assets")
    fun text(path: String) = File(dir, path).readText()
    val config: GameConfig by lazy { GameConfig.parse(text("data/rules.json")) }
    fun area(n: Int): AreaFile = GameJson.decodeFromString(AreaFile.serializer(), text("levels/area_%03d.json".format(n)))
}
