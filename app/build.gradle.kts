import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

fun optionalBuildValue(propertyName: String, envName: String): String? {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) {
        val props = Properties()
        localFile.reader(Charsets.UTF_8).use { props.load(it) }
        props.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun requireReleaseStrictApiUrl(url: String) {
    val normalized = url.trim().lowercase()
    require(normalized.startsWith("https://")) { "Release Strict API URL must use HTTPS." }
    require(!normalized.startsWith("http://")) { "Release Strict API URL must not use cleartext HTTP." }
    require(!normalized.contains("localhost")) { "Release Strict API URL must not target localhost." }
    require(!normalized.contains("127.0.0.1")) { "Release Strict API URL must not target loopback." }
    require(!normalized.contains("10.0.2.2")) { "Release Strict API URL must not target the emulator host alias." }
    require(!normalized.contains("puff")) { "Release Strict API URL must not target Puff." }
}

fun requireDebugStrictApiUrl(url: String) {
    val normalized = url.trim().lowercase()
    require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
        "Debug Strict API URL must be an HTTP(S) URL."
    }
    require(!normalized.contains("puff")) { "Debug Strict API URL must not target Puff." }
}

val strictReleaseApiUrl = "https://api.strictworkout.eu"
requireReleaseStrictApiUrl(strictReleaseApiUrl)
val strictDebugApiUrl = (optionalBuildValue("strict.api.baseUrl", "STRICT_API_BASE_URL")
    ?: "http://127.0.0.1:8082").trim().trimEnd('/')
requireDebugStrictApiUrl(strictDebugApiUrl)
val strictGoogleServerClientId = optionalBuildValue(
    "strict.google.serverClientId",
    "STRICT_GOOGLE_SERVER_CLIENT_ID"
).orEmpty()
val allowedEntitlementOverrides = setOf("AUTO", "FOUNDER", "NON_FOUNDER")
val strictEntitlementOverride = optionalBuildValue(
    "strict.entitlementOverride",
    "STRICT_ENTITLEMENT_OVERRIDE"
) ?: "AUTO"
require(strictEntitlementOverride in allowedEntitlementOverrides) {
    "strict.entitlementOverride must be AUTO, FOUNDER, or NON_FOUNDER. " +
        "Found \"$strictEntitlementOverride\"."
}
val allowedProDiscoveryModes = setOf("REAL", "ELIGIBLE", "ACTIVE", "EXPIRED")
val strictProDiscovery = optionalBuildValue(
    "strict.debug.proDiscovery",
    "STRICT_DEBUG_PRO_DISCOVERY"
) ?: "REAL"
require(strictProDiscovery in allowedProDiscoveryModes) {
    "strict.debug.proDiscovery must be REAL, ELIGIBLE, ACTIVE, or EXPIRED. " +
        "Found \"$strictProDiscovery\"."
}

fun optionalDebugSeconds(propertyName: String, envName: String): String {
    val raw = optionalBuildValue(propertyName, envName) ?: return ""
    require(raw.toLongOrNull()?.let { it >= 0 } == true) {
        "$propertyName must be a non-negative integer. Found \"$raw\"."
    }
    return raw
}

val strictProDiscoveryExpiresInSeconds = optionalDebugSeconds(
    "strict.debug.proDiscoveryExpiresInSeconds",
    "STRICT_DEBUG_PRO_DISCOVERY_EXPIRES_IN_SECONDS"
)
val strictProDiscoveryWarningBeforeSeconds = optionalDebugSeconds(
    "strict.debug.proDiscoveryWarningBeforeSeconds",
    "STRICT_DEBUG_PRO_DISCOVERY_WARNING_BEFORE_SECONDS"
)

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
        applicationId = "com.strictworkout.app"
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
            buildConfigField("String", "STRICT_API_BASE_URL", buildConfigString(strictReleaseApiUrl))
            buildConfigField("String", "STRICT_GOOGLE_SERVER_CLIENT_ID", buildConfigString(strictGoogleServerClientId))
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "STRICT_API_BASE_URL", buildConfigString(strictDebugApiUrl))
            buildConfigField("String", "STRICT_GOOGLE_SERVER_CLIENT_ID", buildConfigString(strictGoogleServerClientId))
            buildConfigField(
                "String",
                "STRICT_ENTITLEMENT_OVERRIDE",
                buildConfigString(strictEntitlementOverride)
            )
            buildConfigField("String", "STRICT_PRO_DISCOVERY", buildConfigString(strictProDiscovery))
            buildConfigField(
                "String",
                "STRICT_PRO_DISCOVERY_EXPIRES_IN_SECONDS",
                buildConfigString(strictProDiscoveryExpiresInSeconds)
            )
            buildConfigField(
                "String",
                "STRICT_PRO_DISCOVERY_WARNING_BEFORE_SECONDS",
                buildConfigString(strictProDiscoveryWarningBeforeSeconds)
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2048m"
            }
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

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}

// Forwards the development-only demo backup writer flag into unit tests.
// Production builds never read this property.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Release unit tests exist to execute the production Founder-rule selection.
    // Other release Robolectric layout tests are not part of this project's supported run.
    if (name == "testReleaseUnitTest") {
        filter {
            includeTestsMatching("app.mymusclemap.FounderProgramRuleSelectionTest")
            includeTestsMatching("app.mymusclemap.EntitlementOverrideSelectionReleaseTest")
            includeTestsMatching("app.mymusclemap.ProDiscoveryDebugSelectionReleaseTest")
        }
    }
    systemProperty("demo.backup.write", (findProperty("demo.backup.write") ?: "false").toString())
    findProperty("demo.referenceDate")?.let { value ->
        systemProperty("demo.referenceDate", value.toString())
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
    implementation(libs.androidx.core.splashscreen)
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

    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.okhttp.mockwebserver)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.health.connect.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
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
