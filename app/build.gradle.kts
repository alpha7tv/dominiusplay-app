plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Número da versão vem do CI (número da execução); localmente fica 1.
val appVersionCode: Int = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "top.dominiusplay.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.dominiusplay.app"
        minSdk = 21
        targetSdk = 34
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
    }

    // Duas edições do mesmo código: "iptv" (Dominius Play) e "express" (Dominius Play P2P EXPRESS, abertura rápida de canais)
    flavorDimensions += "edicao"
    productFlavors {
        create("iptv") {
            dimension = "edicao"
            buildConfigField("String", "APP_TYPE", "\"iptv\"")
            buildConfigField("boolean", "EXPRESS", "false")
            resValue("string", "app_name", "Dominius Play")
            buildConfigField("String", "BRAND", "\"DOMINIUS PLAY\"")
            buildConfigField("String", "C_BG", "\"#070A12\"")
            buildConfigField("String", "C_CARD", "\"#0F1628\"")
            buildConfigField("String", "C_CARD2", "\"#151E36\"")
            buildConfigField("String", "C_BORDER", "\"#1E2842\"")
            buildConfigField("String", "C_TEXT", "\"#EAEEF7\"")
            buildConfigField("String", "C_MUTED", "\"#8E98B0\"")
            buildConfigField("String", "C_ACCENT", "\"#FF7A2F\"")
            buildConfigField("String", "C_YELLOW", "\"#FFC94D\"")
            buildConfigField("String", "C_PINK", "\"#FF4D6D\"")
            buildConfigField("String", "C_ON_ACCENT", "\"#1A0E05\"")
        }
        create("express") {
            dimension = "edicao"
            applicationIdSuffix = ".express"
            buildConfigField("String", "APP_TYPE", "\"express\"")
            buildConfigField("boolean", "EXPRESS", "true")
            resValue("string", "app_name", "Dominius Play P2P EXPRESS")
            buildConfigField("String", "BRAND", "\"DOMINIUS PLAY\"")
            buildConfigField("String", "C_BG", "\"#070A12\"")
            buildConfigField("String", "C_CARD", "\"#0F1628\"")
            buildConfigField("String", "C_CARD2", "\"#151E36\"")
            buildConfigField("String", "C_BORDER", "\"#1E2842\"")
            buildConfigField("String", "C_TEXT", "\"#EAEEF7\"")
            buildConfigField("String", "C_MUTED", "\"#8E98B0\"")
            buildConfigField("String", "C_ACCENT", "\"#FF7A2F\"")
            buildConfigField("String", "C_YELLOW", "\"#FFC94D\"")
            buildConfigField("String", "C_PINK", "\"#FF4D6D\"")
            buildConfigField("String", "C_ON_ACCENT", "\"#1A0E05\"")
        }
        // Cine Pulse: mesmo aplicativo e mesmo painel (dominiusplay.top), com outro nome, logo e tema preto e vermelho
        create("cinepulse") {
            dimension = "edicao"
            applicationId = "br.com.cinepulse.app"
            buildConfigField("String", "APP_TYPE", "\"cinepulse\"")
            buildConfigField("boolean", "EXPRESS", "false")
            resValue("string", "app_name", "Cine Pulse")
            buildConfigField("String", "BRAND", "\"CINE PULSE\"")
            buildConfigField("String", "C_BG", "\"#050505\"")
            buildConfigField("String", "C_CARD", "\"#101010\"")
            buildConfigField("String", "C_CARD2", "\"#181818\"")
            buildConfigField("String", "C_BORDER", "\"#2B2B2B\"")
            buildConfigField("String", "C_TEXT", "\"#F5F5F5\"")
            buildConfigField("String", "C_MUTED", "\"#9A9A9A\"")
            buildConfigField("String", "C_ACCENT", "\"#E50914\"")
            buildConfigField("String", "C_YELLOW", "\"#FF5A5F\"")
            buildConfigField("String", "C_PINK", "\"#FF2D55\"")
            buildConfigField("String", "C_ON_ACCENT", "\"#FFFFFF\"")
        }
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null && file(ksFile).exists()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    // transmissão para Smart TV / Chromecast
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
}
