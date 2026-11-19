package com.zamaninefendisi.game

import com.badlogic.gdx.Gdx
import com.zamaninefendisi.logic.save.SaveStorage

/** Kayıtları uygulamanın özel yerel depolamasına yazar. Önce geçici dosyaya yazıp sonra taşır (atomik). */
class GdxSaveStorage(private val externalDir: String? = null) : SaveStorage {
    private fun file(name: String) = if (externalDir != null) Gdx.files.external(externalDir + name) else Gdx.files.local(name)

    override fun read(name: String): String? {
        val f = file(name)
        return if (f.exists()) try { f.readString("UTF-8") } catch (e: Exception) { null } else null
    }

    override fun write(name: String, content: String) {
        val tmp = file("$name.tmp")
        tmp.writeString(content, false, "UTF-8")
        val target = file(name)
        if (!tmp.file().renameTo(target.file())) {
            target.writeString(content, false, "UTF-8")
            tmp.delete()
        }
    }
}
