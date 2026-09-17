package br.com.navalbattle.data

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.audio.AudioContextHolder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo

private const val PACKAGE_NAME = "aigamesfactory.navalbattleclassic"

actual suspend fun checkUpdateAvailable(): Boolean {
    return try {
        val info = AppUpdateManagerFactory.create(AudioContextHolder.appContext).requestAppUpdateInfo()
        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
    } catch (e: Exception) {
        // sem Play Services, sem instalação via Play Store (ex: build de depuração
        // instalado direto), ou qualquer outra falha de rede — sem alarde, sem versão
        // nova avisada é melhor que travar o app por causa de uma checagem opcional
        false
    }
}

actual fun openStoreListing() {
    val context = ActivityHolder.current ?: AudioContextHolder.appContext
    // sem Activity em mãos (ex: chamado fora de uma tela ativa), precisa da flag para
    // abrir a partir do Context de aplicação; com Activity ela não atrapalha em nada
    val newTaskFlag = if (ActivityHolder.current == null) Intent.FLAG_ACTIVITY_NEW_TASK else 0
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME"))
                .addFlags(newTaskFlag)
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME"))
                .addFlags(newTaskFlag)
        )
    }
}
