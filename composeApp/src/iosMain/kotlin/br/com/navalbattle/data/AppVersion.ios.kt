package br.com.navalbattle.data

import platform.Foundation.NSBundle

actual val appVersionLabel: String
    get() {
        val info = NSBundle.mainBundle.infoDictionary
        val shortVersion = info?.get("CFBundleShortVersionString") as? String ?: "?"
        val build = info?.get("CFBundleVersion") as? String ?: "?"
        return "$shortVersion ($build)"
    }

actual val appVersionName: String
    get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "0"

actual val platformName: String = "ios"

