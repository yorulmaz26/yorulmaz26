package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.Align
import com.emirzinciri.robotkomutan.EmirZinciriGame
import com.emirzinciri.robotkomutan.logic.editor.Brush
import com.emirzinciri.robotkomutan.logic.editor.EditorModel
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.sim.Solver
import com.emirzinciri.robotkomutan.ui.Theme

/** Kendi Fabrikanı Tasarla: harita editörü. Haritalar yerel kayda yazılır (çevrimiçi paylaşım için LevelDef JSON biçimi hazırdır). */
class EditorScreen(game: EmirZinciriGame, private val model: EditorModel = EditorModel()) : BaseScreen(game) {
    private var brush = Brush.WALL
    private val brushes = Table()
    private val status = ui.label("", assets.fontSmall, Theme.inkSoft, wrap = true, align = Align.center)
    private val sizeLabel = ui.label("", assets.fontBold, Theme.ink)
    private val nameLabel = ui.label("", assets.fontTitle, Theme.ink)
    private val grid = GridView()
    private var solving = false

    init {
        root.top()
        val head = Table()
        head.add(ui.iconButton("back", size = 72f) { onBack() }).size(72f)
        nameLabel.setEllipsis(true)
        head.add(nameLabel).padLeft(14f).left().growX().minWidth(0f)
        head.add(ui.iconButton("book", Theme.white, Theme.ink, 64f) { showSaved() }).size(64f)
        root.add(head).growX().pad(16f, 16f, 6f, 16f).row()
        val size = Table()
        size.add(ui.label("Boyut", assets.fontSmall, Theme.inkSoft)).padRight(8f)
        size.add(ui.iconButton("minus", Theme.white, Theme.ink, 52f) { resize(-1) }).size(52f)
        size.add(sizeLabel).pad(0f, 10f, 0f, 10f)
        size.add(ui.iconButton("plus", Theme.white, Theme.ink, 52f) { resize(1) }).size(52f)
        size.add().growX()
        size.add(ui.button("Ad", Theme.white, Theme.ink, null, assets.fontSmall, 16) { askName() }.also { it.pad(6f, 14f, 6f, 14f) })
        root.add(size).growX().pad(0f, 16f, 6f, 16f).row()
        root.add(grid).grow().pad(8f).row()
        root.add(status).growX().pad(0f, 20f, 4f, 20f).height(52f).row()
        val panel = ui.panel(Theme.white, 28, 8f)
        panel.pad(12f)
        panel.add(brushes).growX().row()
        val actions = Table()
        actions.add(ui.button("Çözülebilir mi?", Theme.purple, Theme.white, "hint", assets.fontSmall, 18) { checkSolvable() }.also { it.pad(10f) }).growX().uniformX().pad(4f)
        actions.add(ui.button("Kaydet", Theme.turquoise, Theme.white, "check", assets.fontSmall, 18) { save() }.also { it.pad(10f) }).growX().uniformX().pad(4f)
        actions.add(ui.button("Test et", Theme.green, Theme.white, "play", assets.fontSmall, 18) { test() }.also { it.pad(10f) }).growX().uniformX().pad(4f)
        panel.add(actions).growX().padTop(8f)
        root.add(panel).growX()
        buildBrushes(); refresh()
        if (model.robots.isEmpty() && model.grid.none { 'E' in it }) status.setText("Fırça seç, ızgaraya dokun veya sürükle. Aynı hücreye tekrar dokunmak robot/bant/lazer yönünü çevirir.")
    }

    private fun buildBrushes() {
        brushes.clear()
        val per = 6
        Brush.entries.forEachIndexed { i, b ->
            val sel = b == brush
            val btn = ui.button(b.title, if (sel) Theme.ink else Theme.panelAlt, if (sel) Theme.white else Theme.ink, null, assets.fontSmall, 16) { brush = b; buildBrushes() }
            btn.pad(6f, 4f, 6f, 4f)
            brushes.add(btn).growX().uniformX().pad(3f)
            if ((i + 1) % per == 0) brushes.row()
        }
    }

    private fun refresh() {
        sizeLabel.setText("${model.width}×${model.height}")
        nameLabel.setText(model.name)
    }

    private fun resize(d: Int) {
        val n = (model.width + d).coerceIn(4, 10)
        model.resize(n, (model.height + d).coerceIn(4, 10)); refresh()
    }

    private fun askName() {
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String) { val t = text.trim().take(24); if (t.isNotEmpty()) Gdx.app.postRunnable { model.name = t; refresh() } }
            override fun canceled() {}
        }, "Harita adı", model.name, "")
    }

    private fun checkSolvable() {
        model.validate()?.let { status.setText(it); return }
        if (solving) return
        solving = true
        status.setText("Çözücü arıyor…")
        val def = model.toLevel(30_000)
        Thread {
            val r = runCatching { Solver(LevelData(def), maxNodes = 300_000).solve(24) }.getOrNull()
            Gdx.app.postRunnable {
                solving = false
                status.setText(if (r == null) "24 kart içinde döngüsüz çözüm bulunamadı (ya da arama sınırı aşıldı). Haritayı kolaylaştır ya da kendin dene."
                else "Çözülebilir! En kısa döngüsüz çözüm ${r.cards.size} kart.")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun currentId(): Int {
        val list = services.save.meta.customLevels
        return list.firstOrNull { it.name == model.name }?.id ?: (30_000 + (list.maxOfOrNull { it.id - 30_000 } ?: 0) + 1)
    }

    private fun save() {
        model.validate()?.let { status.setText("Kaydedilmedi: $it"); return }
        val def = model.toLevel(currentId())
        val list = services.save.meta.customLevels
        list.removeAll { it.id == def.id }
        list += def
        services.persist()
        assets.play("star", 0.6f)
        status.setText("'${model.name}' kaydedildi (${list.size} harita).")
    }

    private fun test() {
        model.validate()?.let { status.setText(it); return }
        game.go(GameScreen(game, model.toLevel(currentId()), mode = PlayMode.CUSTOM))
    }

    private fun showSaved() {
        val list = services.save.meta.customLevels
        val c = ui.panel()
        c.pad(26f)
        c.add(ui.label("Haritalarım", assets.fontTitle, Theme.ink)).row()
        var ov: Table? = null
        if (list.isEmpty()) c.add(ui.label("Henüz kayıtlı harita yok.", assets.font, Theme.inkSoft)).padTop(10f).row()
        for (d in list.toList()) {
            val row = Table()
            row.add(ui.label(d.name, assets.fontBold, Theme.ink)).left().growX()
            row.add(ui.button("Aç", Theme.turquoise, Theme.white, null, assets.fontSmall, 16) { ov?.let { ui.close(it) }; game.go(EditorScreen(game, EditorModel.from(d))) }
                .also { it.pad(6f, 12f, 6f, 12f) }).padRight(6f)
            row.add(ui.button("Sil", Theme.white, Theme.red, null, assets.fontSmall, 16) {
                list.removeAll { it.id == d.id }; services.persist(); ov?.let { ui.close(it) }; showSaved()
            }.also { it.pad(6f, 12f, 6f, 12f) })
            c.add(row).growX().padTop(8f).row()
        }
        c.add(ui.button("Yeni harita", Theme.green, Theme.white, "plus") { ov?.let { ui.close(it) }; game.go(EditorScreen(game)) }).growX().padTop(16f)
        ov = ui.modal(stage, c)
    }

    override fun onBack() = game.go(MenuScreen(game))
    override fun dispose() { grid.dispose(); super.dispose() }

    /** Üstten görünümlü düzenleme ızgarası. */
    private inner class GridView : Widget() {
        private val shapes = ShapeRenderer()
        private var lastCell: Pair<Int, Int>? = null

        init {
            addListener(object : InputListener() {
                override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                    lastCell = null; paintAt(x, y, first = true); return true
                }
                override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) = paintAt(x, y, first = false)
            })
        }

        private fun cellSize() = minOf(width / model.width, height / model.height)
        private fun originX() = (width - cellSize() * model.width) / 2
        private fun originY() = (height - cellSize() * model.height) / 2

        private fun paintAt(lx: Float, ly: Float, first: Boolean) {
            val cs = cellSize()
            val gx = ((lx - originX()) / cs).toInt()
            val gy = model.height - 1 - ((ly - originY()) / cs).toInt()
            if (gx !in 0 until model.width || gy !in 0 until model.height) return
            val cell = gx to gy
            // Sürüklerken yalnızca zemin türü fırçaları boyar; yön çeviren fırçalar dokunuşta çalışır.
            val rotating = brush == Brush.ROBOT || brush == Brush.PATROL || brush == Brush.CONVEYOR || brush == Brush.LASER
            if (cell == lastCell || (!first && rotating)) return
            lastCell = cell
            model.paint(gx, gy, brush)
            assets.play("click", 0.3f)
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            validate()
            val cs = cellSize(); val ox = x + originX(); val oy = y + originY()
            batch.end()
            Gdx.gl.glEnable(GL20.GL_BLEND)
            shapes.projectionMatrix = batch.projectionMatrix
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            for (gy in 0 until model.height) for (gx in 0 until model.width) {
                val c = model.grid[gy][gx]
                val px = ox + gx * cs; val py = oy + (model.height - 1 - gy) * cs
                shapes.color = colorOf(c)
                shapes.rect(px + 2, py + 2, cs - 4, cs - 4)
                if (c == 'B') { shapes.color = Theme.boxColor; shapes.rect(px + cs * 0.2f, py + cs * 0.2f, cs * 0.6f, cs * 0.6f) }
            }
            fun tri(px: Float, py: Float, d: Dir, col: Color) {
                val cx = px + cs / 2; val cy = py + cs / 2; val r = cs * 0.32f
                val (dx, dy) = d.dx.toFloat() to -d.dy.toFloat()
                shapes.color = col
                shapes.triangle(cx + dx * r, cy + dy * r, cx - dx * r * 0.6f - dy * r * 0.7f, cy - dy * r * 0.6f + dx * r * 0.7f,
                    cx - dx * r * 0.6f + dy * r * 0.7f, cy - dy * r * 0.6f - dx * r * 0.7f)
            }
            model.robots.entries.forEachIndexed { i, (p, d) ->
                val px = ox + p.first * cs; val py = oy + (model.height - 1 - p.second) * cs
                shapes.color = Theme.robotColors[i]; shapes.circle(px + cs / 2, py + cs / 2, cs * 0.38f, 20)
                tri(px, py, d, Theme.white)
            }
            for ((p, d) in model.patrols) {
                val px = ox + p.first * cs; val py = oy + (model.height - 1 - p.second) * cs
                shapes.color = Theme.rgb(0xD9485F); shapes.rect(px + cs * 0.14f, py + cs * 0.14f, cs * 0.72f, cs * 0.72f)
                tri(px, py, d, Theme.yellow)
            }
            shapes.end()
            batch.begin()
            val f = assets.fontSmall
            for (gy in 0 until model.height) for (gx in 0 until model.width) {
                val label = when (model.grid[gy][gx]) {
                    '>' -> "→"; '<' -> "←"; '^' -> "↑"; 'v' -> "↓"; 'E' -> "Ç"; 'p' -> "P"; 's' -> "A"; 'D' -> "K"; 'c' -> "+"; 'Q' -> "Ş"; 'x' -> "H"
                    'L' -> listOf("↑", "→", "↓", "←")[model.laserDir[gx to gy] ?: 1]; 'o' -> "o"; else -> null
                } ?: continue
                f.color = Theme.ink
                f.draw(batch, label, ox + gx * cs + cs / 2 - 7f, oy + (model.height - 1 - gy) * cs + cs / 2 + 9f)
            }
        }

        private fun colorOf(c: Char): Color = when (c) {
            '#' -> Theme.wallTop; ' ' -> Theme.sky; 'o' -> Theme.pitColor; 'E' -> Theme.exitColor; 'x' -> Theme.targetColor
            'p' -> Theme.plateColor; 's' -> Theme.purple; 'D' -> Theme.doorColor; 'c' -> Theme.energyColor; 'Q' -> Theme.mix(Theme.energyColor, Theme.white, 0.5f)
            '>', '<', '^', 'v' -> Theme.rgb(0x9AA8C2); 'L' -> Theme.red
            else -> Theme.floorTop
        }

        fun dispose() = shapes.dispose()
    }
}
