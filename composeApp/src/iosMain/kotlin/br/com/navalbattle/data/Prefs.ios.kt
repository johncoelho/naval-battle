package br.com.navalbattle.data

import platform.Foundation.NSUserDefaults

/** Preferências do comandante guardadas com `NSUserDefaults`, o equivalente do iOS
 * ao `SharedPreferences` do Android. */
actual class Prefs actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    actual fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key).toInt() else default

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
    }

    actual fun clear() {
        defaults.dictionaryRepresentation().keys.forEach { key ->
            (key as? String)?.let { defaults.removeObjectForKey(it) }
        }
    }
}
