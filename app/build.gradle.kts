plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.secrets)
}

/*
 * Release signing is supplied externally so keystores and passwords are never committed.
 * All four values are required before release builds are signed — each one may be provided
 * either as a Gradle property (e.g. in ~/.gradle/gradle.properties or via -P) or as an
 * environment variable of the same name:
 *
 *   RELEASE_STORE_FILE      path to the release keystore (absolute, or project-root relative)
 *   RELEASE_STORE_PASSWORD  keystore password
 *   RELEASE_KEY_ALIAS       key alias
 *   RELEASE_KEY_PASSWORD    key password
 *
 * While any value is missing, release builds are assembled unsigned. Debug builds and unit
 * tests use the standard Android debug signing configuration (the AGP-managed
 * ~/.android/debug.keystore), so they need no keystore in the repository.
 */
fun releaseSigningValue(name: String): String =
    (providers.gradleProperty(name).orNull ?: System.getenv(name)).orEmpty()

val releaseStoreFile = releaseSigningValue("RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it.isNotBlank() }

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.studyvault.krsvpt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
