import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.playPublisher)
}

kotlin {
    // expect/actual de classe segue Beta no Kotlin 2.3 e o projeto usa o padr�o em todo o data/ e audio/
    // (README, contratos); o flag s� silencia o aviso que repetia em cada declara��o
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // efeitos e trilha empacotados uma vez só (composeResources/files) e lidos
            // dos dois lados — no iOS é o que falta pra áudio de verdade existir
            implementation(libs.compose.resources)
        }
        // testes de unidade da lógica de jogo (game/), rodam no JVM e no simulador iOS
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
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
            // pedido de avaliação dentro do jogo (#45; stack aprovada pelo John em 25/10): Play
            // In-App Review, só no Android; a lib traz as próprias regras de R8
            implementation(libs.play.review.ktx)
            // compra de dobrões pela Google Play (#15; stack aprovada pelo John em 17/10):
            // só a biblioteca oficial de billing
            implementation(libs.play.billing)
            // anúncio premiado opcional (#29; stack aprovada pelo John em 18/10): SDK do
            // Google Mobile Ads (AdMob) e o formulário de consentimento (UMP). Só inicia
            // depois do primeiro toque num botão de anúncio (RewardedAds.android.kt)
            implementation(libs.play.services.ads)
            implementation(libs.ump)
            // faixa etária da loja (#49; stack aprovada pelo John em 25/10): Play Age Signals,
            // beta, só para escolher o tipo de anúncio (ECA Digital). A idade não sai do aparelho
            implementation(libs.play.age.signals)
            // conquistas e placar do Google Play Games (#48; stack aprovada pelo John em 25/10).
            // Inerte enquanto pgsAppId e res/values/games-ids.xml estiverem vazios (GameServices.supported)
            implementation(libs.play.games)
            // push de verdade (convites, amizade) pelo Firebase Cloud Messaging; a
            // configuração vem do google-services.json (ver PushMessaging.android.kt)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
        }
    }
}

// O plugin do Google Services só entra quando o google-services.json está no módulo:
// sem ele o app compila igual e o push fica desligado (PushMessaging devolve nulo).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
        versionCode = 111
        versionName = "0.42.2"
        // Play Games Services (#48): app_id do console (gradle.properties, vazio = recurso desligado).
        // Não é segredo; vira a meta-data APP_ID do manifesto
        resValue("string", "game_services_project_id", (findProperty("pgsAppId") as String?).orEmpty())
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
            // Faixa etária falsa para o QA ver o caminho de menor sem conta supervisionada (#49):
            // ./gradlew assembleDebug -PageFake=teen (child, teen, adult, unknown). Só no debug e
            // na prévia .preview; o release abaixo fixa vazio.
            buildConfigField("String", "AGE_FAKE", "\"${(findProperty("ageFake") as String?).orEmpty()}\"")
            // Avaliação falsa para o QA (#45): ./gradlew assembleDebug -PreviewFake=true usa o
            // FakeReviewManager (abre o fluxo sem a Play). O release fixa false.
            buildConfigField("boolean", "REVIEW_FAKE", "${(findProperty("reviewFake") as String?) == "true"}")
        }
        getByName("release") {
            buildConfigField("String", "AGE_FAKE", "\"\"")
            buildConfigField("boolean", "REVIEW_FAKE", "false")
            // R8 (#37): encolhe e ofusca o código e remove recurso sem uso. As regras de keep
            // estão em proguard-rules.pro; o mapa de desofuscação vai dentro do .aab e a Play
            // o extrai sozinha no envio. R8 quebra só no release: testar sempre o APK minificado
            // (docs/BUILD.md, -PqaSign) antes de publicar
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else if (findProperty("qaSign") != null) {
                // Só para o QA instalar o release minificado no emulador: assina com a chave de
                // depuração quando não há keystore de release (-PqaSign). O CI tem o keystore e
                // nunca passa por aqui; sem a opção o release continua sem assinatura
                signingConfig = signingConfigs.getByName("debug")
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
