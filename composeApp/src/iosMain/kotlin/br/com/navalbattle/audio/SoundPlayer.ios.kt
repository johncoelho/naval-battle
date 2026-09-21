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
import kotlin.random.Random

/**
 * O tiro na água é o som mais frequente da partida: três gravações diferentes,
 * sorteadas a cada disparo, evitam a sensação de repetição — mesmo critério do
 * `SoundPlayer.android.kt`. As demais têm um único arquivo.
 */
private val Sfx.paths: List<String>
    get() = when (this) {
        Sfx.LAUNCH -> listOf("sfx_launch.wav")
        Sfx.MISS -> listOf("sfx_miss1.wav", "sfx_miss2.wav", "sfx_miss3.wav")
        Sfx.HIT -> listOf("sfx_hit.wav")
        Sfx.SUNK -> listOf("sfx_sunk.wav")
        Sfx.ALARM -> listOf("sfx_alarm.wav")
        Sfx.ALARM_CRITICAL -> listOf("sfx_alarm_critical.wav")
    }.map { "files/$it" }

/**
 * Efeitos curtos de combate via `AVAudioPlayer`, um tocador por arquivo — carregado
 * uma vez em segundo plano e reaproveitado a cada `play()` (some não pode esperar
 * o disco). Os `.wav` são os mesmos do Android, empacotados uma vez só em
 * `commonMain/composeResources/files`.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
actual class SoundPlayer actual constructor() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val players = mutableMapOf<Sfx, List<AVAudioPlayer>>()

    init {
        scope.launch {
            for (sfx in Sfx.entries) {
                val loaded = mutableListOf<AVAudioPlayer>()
                for (path in sfx.paths) {
                    // um arquivo faltando ou corrompido no pacote não pode derrubar o app
                    // inteiro — sem esse efeito é ruim, travar na abertura é muito pior
                    try {
                        val data = Res.readBytes(path).toNSData()
                        AVAudioPlayer(data = data, error = null)?.let { player ->
                            player.prepareToPlay()
                            loaded.add(player)
                        }
                    } catch (e: Exception) {
                        // segue sem essa variante; as outras continuam tentando carregar
                    }
                }
                if (loaded.isNotEmpty()) players[sfx] = loaded
            }
        }
    }

    actual fun play(sfx: Sfx) {
        val variants = players[sfx] ?: return
        val player = variants[Random.nextInt(variants.size)]
        // reinicia do começo mesmo se já estiver tocando (tiros em sequência rápida)
        player.currentTime = 0.0
        player.play()
    }

    actual fun release() {
        players.values.forEach { list -> list.forEach { it.stop() } }
        players.clear()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
