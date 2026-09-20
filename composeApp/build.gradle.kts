import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.playPublisher)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        // "ComposeApp" é o nome do framework que o projeto Xcode (iosApp/) importa
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // efeitos e trilha empacotados uma vez só (composeResources/files) e lidos
            // dos dois lados — no iOS é o que falta pra áudio de verdade existir
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            // login com o Google via Credential Manager — precisa do Client ID
            // "Web application" cadastrado em GoogleAuthConfig e do provedor Google
            // ligado no Supabase (ver docs/BUILD.md)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.id)
            // avisa quando uma versão mais nova já está publicada na faixa de teste do
            // comandante — mesma API que a própria Play Store usa, sem precisar de push
            implementation(libs.play.app.update.ktx)
        }
    }
}

compose.resources {
    // nome fixo, em vez do padrão {group}.{module}.generated.resources — o
    // projeto não define "group" nenhum, e um pacote começando com ponto quebraria
    packageOfResClass = "br.com.navalbattle.generated.resources"
}

android {
    namespace = "br.com.navalbattle"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    // gera BuildConfig.VERSION_NAME/VERSION_CODE — usado em AppVersion.android.kt pra
    // mostrar a versão instalada na tela, sem duplicar o número em lugar nenhum
    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        // id publicado na Play Console (registrado como "Naval Battle Classic" /
        // AI Games Factory) — diferente do namespace interno do código, que
        // continua br.com.navalbattle; não precisa bater com o pacote Kotlin
        applicationId = "aigamesfactory.navalbattleclassic"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 27
        versionName = "0.6.6"
    }

    /**
     * Chave de depuração fixa, versionada no repositório. Sem ela cada build do CI
     * assina o APK com uma chave nova, o Android recusa a instalação por cima e o
     * comandante perde o progresso ao desinstalar para atualizar.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/naval-debug.keystore")
            storePassword = "navalbattle"
            keyAlias = "navalbattle"
            keyPassword = "navalbattle"
        }
        // Chave de release: nunca versionada. O CI decodifica o keystore de um
        // secret em ANDROID_KEYSTORE_PATH antes do build; localmente, sem essas
        // variáveis de ambiente, o bloco abaixo não roda e o build de release
        // simplesmente fica sem assinatura (assembleRelease ainda funciona para
        // inspecionar o APK, só não é instalável nem publicável).
        val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                // sem isso o AGP assume JKS por padrão; o keystore gerado (JDK 17,
                // keytool sem -storetype) é PKCS12, e a leitura como JKS falha com
                // um erro de padding que parece senha errada mas não é
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Publica o .aab de release direto na faixa de testes fechados via Play Developer
// API — sem isso, cada release precisava ser baixada e enviada manualmente pelo
// navegador. O CI decodifica a service account de um secret em
// PLAY_PUBLISHER_CREDENTIALS_PATH antes de rodar ":composeApp:publishBundle";
// localmente, sem essa variável, o plugin fica configurado mas nenhuma task de
// publish roda (build normal não é afetado). Ver docs/BUILD.md.
play {
    serviceAccountCredentials.set(
        file(System.getenv("PLAY_PUBLISHER_CREDENTIALS_PATH") ?: "play-publisher-credentials.json")
    )
    track.set("alpha")
    defaultToAppBundles.set(true)
}
