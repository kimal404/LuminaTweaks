import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val gitCommit = runCatching {
    val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
    proc.inputStream.bufferedReader().readText().trim()
}.getOrDefault("0ed5ba6")

val ciRun = System.getenv("GITHUB_RUN_NUMBER") ?: "119"
val dynamicBuildInfo = "ci$ciRun-$gitCommit"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    namespace = "com.luminatweaks"
    compileSdk = 35

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        buildConfigField("String", "BUILD_INFO", "\"$dynamicBuildInfo\"")
        applicationId = "com.luminatweaks"
        minSdk = 26
        targetSdk = 35
        versionCode = 230
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.6.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")
}
