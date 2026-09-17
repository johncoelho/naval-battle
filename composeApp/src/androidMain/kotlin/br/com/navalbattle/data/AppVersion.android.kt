package br.com.navalbattle.data

import br.com.navalbattle.BuildConfig

actual val appVersionLabel: String
    get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

