package com.emirzinciri.robotkomutan

import com.badlogic.gdx.Gdx
import com.emirzinciri.robotkomutan.logic.level.LevelCatalog
import com.emirzinciri.robotkomutan.logic.progress.FileSaveStorage
import com.emirzinciri.robotkomutan.logic.progress.Progression
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import com.emirzinciri.robotkomutan.logic.progress.SaveManager
import com.emirzinciri.robotkomutan.platform.Platform

/** Oyunun kalıcı durumu ve içerik kataloğu. */
class Services(val platform: Platform) {
    val catalog: LevelCatalog = loadCatalog()
    private val manager = SaveManager(FileSaveStorage(platform.saveDir()))
    var save: SaveData
        private set
    var progression: Progression
        private set
    /** Kayıt kurtarıldıysa kullanıcıya gösterilecek mesaj. */
    var loadMessage: String? = null

    init {
        val r = manager.load()
        save = r.data
        loadMessage = r.message
        if (save.createdAtMs == 0L) save.createdAtMs = platform.clock.wallMs()
        progression = Progression(save, platform.clock)
        progression.lives.update()
    }

    fun persist() {
        progression.lives.update()
        runCatching { manager.save(save) }.onFailure { Gdx.app.error("Kayıt", "Kaydedilemedi", it) }
    }

    /** Bütün ilerlemeyi siler (Ayarlar'dan onayla). */
    fun resetProgress() {
        val settings = save.settings
        save = SaveData(settings = settings, createdAtMs = platform.clock.wallMs())
        progression = Progression(save, platform.clock)
        progression.lives.update()
        persist()
    }

    /** Oyuncunun oynayabileceği ilk tamamlanmamış bölüm. */
    fun nextLevelId(): Int = catalog.ids.firstOrNull { !progression.isCompleted(it) } ?: catalog.maxId

    private fun loadCatalog(): LevelCatalog {
        val texts = ArrayList<String>()
        for (area in 1..100) {
            val f = Gdx.files.internal("levels/" + LevelCatalog.areaFileName(area))
            if (!f.exists()) break
            texts += f.readString("UTF-8")
        }
        return LevelCatalog.fromAreaFiles(texts)
    }
}
