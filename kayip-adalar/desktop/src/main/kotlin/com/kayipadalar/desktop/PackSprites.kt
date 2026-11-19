package com.kayipadalar.desktop

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker

/** assets/game altındaki PNG'leri assets/atlas/game.atlas dosyasına paketler (bölge adı = "klasör/dosya"). */
fun main(args: Array<String>) {
    val input = args.getOrElse(0) { "assets/game" }
    val output = args.getOrElse(1) { "assets/atlas" }
    val s = TexturePacker.Settings().apply {
        maxWidth = 2048; maxHeight = 2048
        paddingX = 4; paddingY = 4
        duplicatePadding = true
        edgePadding = true
        combineSubdirectories = true
        flattenPaths = false
        useIndexes = false
        filterMin = Texture.TextureFilter.MipMapLinearLinear
        filterMag = Texture.TextureFilter.Linear
        stripWhitespaceX = true; stripWhitespaceY = true
    }
    TexturePacker.process(s, input, output, "game")
}
