package br.com.navalbattle.data

import android.content.Context
import android.content.SharedPreferences
import br.com.navalbattle.audio.AudioContextHolder

actual class Prefs actual constructor() {

    private val store: SharedPreferences =
        AudioContextHolder.appContext.getSharedPreferences("naval_battle", Context.MODE_PRIVATE)

    actual fun getString(key: String, default: String): String =
        store.getString(key, default) ?: default

    actual fun getInt(key: String, default: Int): Int = store.getInt(key, default)

    actual fun putString(key: String, value: String) {
        store.edit().putString(key, value).apply()
    }

    actual fun putInt(key: String, value: Int) {
        store.edit().putInt(key, value).apply()
    }

    actual fun clear() {
        store.edit().clear().apply()
    }
}
