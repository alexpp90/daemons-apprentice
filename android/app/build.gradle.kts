import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * The release key lives outside every repository: a properties file named by
 * DA_KEYSTORE_PROPERTIES, or ~/.config/daemons-apprentice/keystore.properties, with
 * storeFile, storePassword, keyAlias and keyPassword. Without it, release builds use the debug key.
 */
val releaseKey: Properties? = (System.getenv("DA_KEYSTORE_PROPERTIES")
    ?: "${System.getProperty("user.home")}/.config/daemons-apprentice/keystore.properties")
    .let { file(it) }.takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "io.github.alexpp90.daemonsapprentice"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "io.github.alexpp90.daemonsapprentice"
        minSdk = 29
        targetSdk = 37
        versionCode = 10
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=none" } }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    signingConfigs {
        releaseKey?.let { k ->
            create("release") {
                storeFile = file(k.getProperty("storeFile"))
                storePassword = k.getProperty("storePassword")
                keyAlias = k.getProperty("keyAlias")
                keyPassword = k.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without the release key (see above) the release build is signed with the debug key,
            // so that it can still be installed for testing.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    testOptions { animationsDisabled = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    // Android 17 removed a method older Espresso versions look up.
    androidTestImplementation(libs.androidx.test.espresso.core)
}
