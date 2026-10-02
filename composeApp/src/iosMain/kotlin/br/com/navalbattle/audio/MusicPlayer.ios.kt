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

private fun Music.path(): String = "files/" + when (this) {
    Music.THEME_1 -> "music_theme.m4a"
    Music.THEME_2 -> "music_theme_2.m4a"
    Music.THEME_3 -> "music_theme_3.m4a"
    Music.BATTLE -> "music_battle.m4a"
    Music.BATTLE_INTENSE -> "music_battle_intense.m4a"
}

private const val SEA_PATH = "files/ambient_sea.m4a"
private const val SEA_VOLUME = 0.28f

private fun Music.volume(): Float = when (this) {
    Music.THEME_1, Music.THEME_2, Music.THEME_3 -> 0.55f
    // no combate a trilha recua para os tiros e alarmes ficarem à frente
    Music.BATTLE, Music.BATTLE_INTENSE -> 0.17f
}

/**
 * Trilha em laço via `AVAudioPlayer`, um tocador por faixa — carregados uma vez em
 * segundo plano e trocados na hora. Os `.m4a` vêm dos mesmos `.ogg` do Android,
 * convertidos porque o `AVAudioPlayer` não lê Ogg Vorbis.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
actual class MusicPlayer actual constructor() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val players = mutableMapOf<Music, AVAudioPlayer>()
    private var current: Music? = null
    private var sea: AVAudioPlayer? = null
    private var seaWanted = false
    private var seaOn = false

    init {
        scope.launch {
            try {
                AVAudioPlayer(data = Res.readBytes(SEA_PATH).toNSData(), error = null)?.let { player ->
                    player.numberOfLoops = -1
                    player.volume = SEA_VOLUME
                    player.prepareToPlay()
                    sea = player
                    // pedido feito antes de o arquivo terminar de carregar
                    if (seaWanted) ambient(true)
                }
            } catch (e: Exception) {
                // sem a camada do mar o combate segue só com a música
            }
            for (track in Music.entries) {
                // um arquivo faltando ou corrompido no pacote não pode derrubar o app
                // inteiro — sem música é ruim, travar na abertura é muito pior
                try {
                    val data = Res.readBytes(track.path()).toNSData()
                    AVAudioPlayer(data = data, error = null)?.let { player ->
                        player.numberOfLoops = -1
                        player.volume = track.volume()
                        player.prepareToPlay()
                        players[track] = player
                    }
                } catch (e: Exception) {
                    // segue sem essa faixa; as outras continuam tentando carregar
                }
            }
        }
    }

    actual fun play(track: Music) {
        // o estado de "tocando" é o nosso próprio, não o do AVAudioPlayer — evita
        // depender de mais um nome de propriedade incerto no binding
        if (current == track) return
        // entre as duas faixas de combate, a nova segue do mesmo ponto do compasso
        val resumeAt = if (current?.isBattle() == true && track.isBattle()) players[current]?.currentTime ?: 0.0 else 0.0
        players[current]?.stop()
        val player = players[track] ?: return
        current = track
        player.currentTime = resumeAt
        player.play()
    }

    actual fun ambient(on: Boolean) {
        seaWanted = on
        val player = sea ?: return
        if (on && !seaOn) {
            player.currentTime = 0.0
            player.play()
        } else if (!on && seaOn) {
            player.stop()
        }
        seaOn = on
    }

    actual fun stop() {
        players[current]?.stop()
        current = null
        ambient(false)
    }

    actual fun release() {
        players.values.forEach { it.stop() }
        players.clear()
        current = null
        ambient(false)
        sea = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
