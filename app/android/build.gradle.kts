import hdsender.signing.ReleaseSigning
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The release key: decrypted from keystore-backup/masjid-clock-key.enc with MASJIDCLOCK_KEY_PASSPHRASE
// (environment or the git-ignored app/.env), or else read from keystore.properties. See README.
val releaseSigning: ReleaseSigning.Result? = ReleaseSigning.load(rootProject.projectDir, System.getenv())
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (releaseRequested) {
    if (releaseSigning == null) {
        logger.warn("Masjid Clock: no release key. Set MASJIDCLOCK_KEY_PASSPHRASE in app/.env. The release APK is unsigned.")
    } else {
        logger.lifecycle("Masjid Clock: release key from ${releaseSigning.source}")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.masjidclock.wfsender"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cyanwarelabs.masjidclock.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "0.3.1"
    }

    signingConfigs {
        releaseSigning?.let { key ->
            create("release") {
                storeFile = key.storeFile
                storePassword = key.storePassword
                keyAlias = key.keyAlias
                keyPassword = key.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        // java.time on Android 7 (minSdk 24).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The APK file name: masjid-clock-<versionName>.apk, and masjid-clock-<versionName>-debug.apk for debug.
    applicationVariants.all {
        val suffix = if (buildType.name == "release") "" else "-${buildType.name}"
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "masjid-clock-${versionName}$suffix.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
}

tasks.register("encryptReleaseKey") {
    group = "signing"
    description = "Encrypts keystore.properties and its keystore into keystore-backup/masjid-clock-key.enc."
    doLast {
        val file = ReleaseSigning.encrypt(rootProject.projectDir, System.getenv())
        println("Wrote $file. Commit this file. Keep MASJIDCLOCK_KEY_PASSPHRASE in Bitwarden.")
    }
}
