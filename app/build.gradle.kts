import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ── Auto-versioning ──
val versionFile = rootProject.file("version.properties")
val vc_props = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}
val vCode = vc_props.getProperty("version.code", "1").toInt()
val vMajor = vCode / 100
val vMinor = (vCode / 10) % 10
val vPatch = vCode % 10
val vName = "$vMajor.$vMinor.$vPatch"

android {
    namespace = "com.example.wifishare"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wifishare"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        resourceConfigurations += listOf("en", "ar")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

// ── Auto-bump on successful build ──
afterEvaluate {
    tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
        doLast {
            val newCode = vCode + 1
            versionFile.writeText("version.code=$newCode\n")
            val nM = newCode / 100
            val nm = (newCode / 10) % 10
            val np = newCode % 10
            println("📦 Version bumped: $vName ($vCode) → $nM.$nm.$np ($newCode)")
        }
    }
}
