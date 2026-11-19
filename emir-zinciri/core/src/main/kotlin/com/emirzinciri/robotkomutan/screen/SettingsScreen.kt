package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.ui.Theme

/** Ayarlar: ses, görünüm, animasyon hızı, profil adı, ilerlemeyi sıfırlama ve lisanslar. */
class SettingsScreen(game: EmirZinciriGame) : BaseScreen(game) {
    private val body = Table()

    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        head.add(ui.label("Ayarlar", assets.fontTitle, Theme.ink)).padLeft(16f).left().growX()
        root.add(head).growX().pad(16f).row()
        root.add(body).growX().pad(0f, 16f, 16f, 16f).row()
        build()
    }

    private fun build() {
        body.clear()
        val s = services.save.settings
        body.add(row("Ses efektleri", if (s.sound) "Açık" else "Kapalı", s.sound) {
            s.sound = !s.sound; assets.soundOn = s.sound; save()
        }).growX().padBottom(12f).row()
        body.add(row("Müzik", if (s.music) "Açık" else "Kapalı", s.music) {
            s.music = !s.music; assets.musicOn = s.music; save()
        }).growX().padBottom(12f).row()
        body.add(row("Zemin deseni", if (s.showGrid) "Açık" else "Kapalı", s.showGrid) { s.showGrid = !s.showGrid; save() }).growX().padBottom(12f).row()
        body.add(row("Animasyon hızı", "${s.animSpeed}×", true) {
            s.animSpeed = when (s.animSpeed) { 1 -> 2; 2 -> 4; else -> 1 }; save()
        }).growX().padBottom(12f).row()
        body.add(row("Komutan adı", services.save.profileName, true) { askName() }).growX().padBottom(12f).row()
        body.add(ui.label("Simülasyon sonucu animasyon hızından etkilenmez.",
            assets.fontSmall, Theme.inkFaint, wrap = true)).growX().padTop(4f).padBottom(18f).row()
        body.add(ui.button("İlerlemeyi sıfırla", Theme.white, Theme.red, "trash") { confirmReset() }).growX().padBottom(18f).row()
        val about = ui.panel(Theme.panelAlt, 24, 0f)
        about.pad(18f)
        about.add(ui.label(EmirZinciriGame.TITLE, assets.fontBold, Theme.ink)).left().row()
        about.add(ui.label("Sürüm 1.0.0 · Tamamen çevrimdışı oynanır, hesap gerekmez.", assets.fontSmall, Theme.inkSoft, wrap = true)).growX().left().row()
        about.add(ui.label("Yazı tipi: Nunito (SIL Open Font License 1.1). Oyun altyapısı: libGDX (Apache 2.0). Müzik ve sesler oyun için sentezlendi.", assets.fontSmall, Theme.inkSoft, wrap = true))
            .growX().left().padTop(6f)
        body.add(about).growX()
    }

    private fun row(title: String, value: String, on: Boolean, onClick: () -> Unit): Table {
        val t = ui.panel(Theme.white, 22, 4f)
        t.pad(18f, 22f, 18f, 22f)
        t.add(ui.label(title, assets.fontBold, Theme.ink)).left().growX()
        val v = Table()
        v.background = assets.panel(if (on) Theme.turquoise else Theme.line, 16, 0f)
        v.add(ui.label(value, assets.fontBold, if (on) Theme.white else Theme.inkSoft)).pad(4f, 14f, 4f, 14f)
        t.add(v)
        ui.onTap(t) { onClick(); build() }
        return t
    }

    private fun save() = services.persist()

    private fun askName() {
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String) {
                val name = text.trim().take(20)
                if (name.isNotEmpty()) Gdx.app.postRunnable { services.save.profileName = name; save(); build() }
            }
            override fun canceled() {}
        }, "Komutan adı", services.save.profileName, "Adın")
    }

    private fun confirmReset() {
        ui.dialog(stage, "Emin misin?", "Bütün bölüm ilerlemen, yıldızların, kaynakların ve kayıtlı komut zincirlerin silinecek. Bu işlem geri alınamaz.",
            "Vazgeç" to {}, "Sil" to {
                ui.dialog(stage, "Son onay", "İlerleme kalıcı olarak silinsin mi?", "Hayır" to {}, "Evet, sil" to {
                    services.resetProgress(); assets.soundOn = services.save.settings.sound; build()
                }, color = Theme.red)
            }, color = Theme.red)
    }

    override fun onBack() = game.go(MenuScreen(game))

}
