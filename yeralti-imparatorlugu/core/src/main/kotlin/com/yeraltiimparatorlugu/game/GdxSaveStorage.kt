package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.yeraltiimparatorlugu.sim.SaveStorage

/** Uygulamanın özel yerel klasöründe kayıt (Android: dahili depolama, masaüstü: çalışma klasörü). */
class GdxSaveStorage(private val dir: String = "save") : SaveStorage {
    private fun f(name: String): FileHandle = Gdx.files.local("$dir/$name")
    override fun read(name: String): String? = f(name).takeIf { it.exists() }?.readString("UTF-8")
    override fun write(name: String, data: String) = f(name).writeString(data, false, "UTF-8")
    override fun exists(name: String) = f(name).exists()
    override fun move(from: String, to: String) = f(from).moveTo(f(to))
    override fun delete(name: String) { f(name).delete() }
}
