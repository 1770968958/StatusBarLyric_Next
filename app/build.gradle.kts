@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
}

val buildTime = providers.gradleProperty("BUILD_TIME")
    .orElse(providers.environmentVariable("BUILD_TIME"))
    .orElse("0")
    .get()
val localProperties = Properties()
if (rootProject.file("local.properties").canRead()) {
    localProperties.load(rootProject.file("local.properties").inputStream())
}

android {
    namespace = "statusbar.lyric"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    flavorDimensions += "runtime"

    productFlavors {
        create("legacy") {
            dimension = "runtime"
        }
        create("api101") {
            dimension = "runtime"
        }
    }

    defaultConfig {
        applicationId = "statusbar.lyric"
        minSdk = 30
        targetSdk = 36
        versionCode = 102
        versionName = "1.0.3"
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
        buildConfigField("int", "COMPOSE_CONFIG_VERSION", "1")
        setProperty("archivesBaseName", "StatusBarLyric_Next-$versionName($versionCode)")
    }
    val config = localProperties.getProperty("androidStoreFile")?.let {
        signingConfigs.create("config") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("androidStorePassword")
            keyAlias = localProperties.getProperty("androidKeyAlias")
            keyPassword = localProperties.getProperty("androidKeyPassword")
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    buildTypes {
        all {
            signingConfig = config ?: signingConfigs["debug"]
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    aaptOptions.cruncherEnabled = false
    buildFeatures.buildConfig = true
    dependenciesInfo.includeInApk = false
    kotlin.jvmToolchain(21)
    packaging.resources.excludes += setOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1"
    )
    packaging.resources.pickFirsts += setOf(
        "META-INF/LICENSE*",
        "META-INF/NOTICE*"
    )
}

dependencies {
    // Legacy and API 101 runtime APIs are kept out of the main dependency set.
    add("legacyCompileOnly", libs.xposed)
    add("legacyImplementation", libs.ezXHelper)
    add("api101CompileOnly", libs.libxposed.api)
    add("api101Implementation", libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)

    implementation(libs.haze)
    implementation(libs.miuix)

    implementation(libs.superlyricapi)

    debugImplementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
}
