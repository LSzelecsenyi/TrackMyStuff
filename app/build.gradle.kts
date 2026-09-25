import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

fun releaseSigningValue(propertyName: String, envName: String): String? {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) {
        val props = Properties()
        localFile.reader(Charsets.UTF_8).use { props.load(it) }
        props.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

val releaseStoreFilePath = releaseSigningValue("mmm.release.storeFile", "MMM_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("mmm.release.storePassword", "MMM_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("mmm.release.keyAlias", "MMM_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("mmm.release.keyPassword", "MMM_RELEASE_KEY_PASSWORD")
val releaseSigningValueCount = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).count { it != null }
val hasCompleteReleaseSigning = releaseSigningValueCount == 4

android {
    namespace = "app.mymusclemap"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mymusclemap"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                val configuredStore = file(releaseStoreFilePath!!)
                storeFile = if (configuredStore.isAbsolute) {
                    configuredStore
                } else {
                    rootProject.file(releaseStoreFilePath)
                }
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
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

gradle.taskGraph.whenReady {
    val touchesReleasePackaging = gradle.taskGraph.allTasks.any { task ->
        val name = task.name
        name.contains("Release") && (
            name.startsWith("package") ||
                name.startsWith("bundle") ||
                name.startsWith("assemble") ||
                name.startsWith("sign")
            )
    }
    if (touchesReleasePackaging && releaseSigningValueCount in 1..3) {
        error(
            "Incomplete release signing configuration. Set all four values " +
                "(mmm.release.storeFile, mmm.release.storePassword, mmm.release.keyAlias, " +
                "mmm.release.keyPassword in gitignored local.properties, or MMM_RELEASE_STORE_FILE, " +
                "MMM_RELEASE_STORE_PASSWORD, MMM_RELEASE_KEY_ALIAS, MMM_RELEASE_KEY_PASSWORD). " +
                "Debug tasks do not require these values. If none are set, unsigned release " +
                "artifacts can still be compiled."
        )
    }
}
