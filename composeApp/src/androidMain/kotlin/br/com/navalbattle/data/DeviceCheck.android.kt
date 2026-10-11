package br.com.navalbattle.data

import android.provider.Settings
import android.util.Log
import br.com.navalbattle.audio.AudioContextHolder

actual fun isTestLabDevice(): Boolean = try {
    val value = Settings.System.getString(AudioContextHolder.appContext.contentResolver, "firebase.test.lab")
    // diagnóstico local; o que vale é o relato no servidor (device_checks)
    Log.i("NavalTestLab", "firebase.test.lab=$value")
    isTestLabFlag(value)
} catch (e: Exception) {
    false
}
