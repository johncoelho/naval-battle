package br.com.navalbattle.audio

import br.com.navalbattle.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

private val Sfx.path: String
    get() = "files/" + when (this) {
        Sfx.LAUNCH -> "sfx_launch.wav"
        Sfx.MISS -> "sfx_miss.wav"
        Sfx.HIT -> "sfx_hit.wav"
        Sfx.SUNK -> "sfx_sunk.wav"
        Sfx.ALARM -> "sfx_alarm.wav"
        Sfx.ALARM_CRITICAL -> "sfx_alarm_critical.wav"
    }

/**
 * Efeitos curtos de combate via `AVAudioPlayer`, um tocador por efeito — carregado
 * uma vez em segundo plano e reaproveitado a cada `play()` (some não pode esperar
 * o disco). Os `.wav` são os mesmos do Android, empacotados uma vez só em
 * `commonMain/composeResources/files`.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
actual class SoundPlayer actual constructor() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val players = mutableMapOf<Sfx, AVAudioPlayer>()

    init {
        scope.launch {
            for (sfx in Sfx.entries) {
                // um arquivo faltando ou corrompido no pacote não pode derrubar o app
                // inteiro — sem esse efeito é ruim, travar na abertura é muito pior
                try {
                    val data = Res.readBytes(sfx.path).toNSData()
                    AVAudioPlayer(data = data, error = null)?.let { player ->
                        player.prepareToPlay()
                        players[sfx] = player
                    }
                } catch (e: Exception) {
                    // segue sem esse efeito; os outros continuam tentando carregar
                }
            }
        }
    }

    actual fun play(sfx: Sfx) {
        val player = players[sfx] ?: return
        // reinicia do começo mesmo se já estiver tocando (tiros em sequência rápida)
        player.currentTime = 0.0
        player.play()
    }

    actual fun release() {
        players.values.forEach { it.stop() }
        players.clear()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
