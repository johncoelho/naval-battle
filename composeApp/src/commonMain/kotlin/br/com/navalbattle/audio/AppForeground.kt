package br.com.navalbattle.audio

import androidx.compose.runtime.mutableStateOf

/**
 * Se o app está em primeiro plano. A trilha para sozinha quando o comandante
 * minimiza ou troca de app (ver `MainActivity.onPause`/`onResume`) e retoma ao
 * voltar — sem isso a música seguia tocando por cima de outro app aberto.
 */
object AppForeground {
    var active by mutableStateOf(true)
}
