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
val strictWelcomeBack = optionalBuildValue(
    "strict.debug.welcomeBack",
    "STRICT_DEBUG_WELCOME_BACK"
) ?: "REAL"
require(strictWelcomeBack in allowedProDiscoveryModes) {
    "strict.debug.welcomeBack must be REAL, ELIGIBLE, ACTIVE, or EXPIRED. " +
        "Found \"$strictWelcomeBack\"."
}
val strictWelcomeBackGapDays = optionalDebugSeconds(
    "strict.debug.welcomeBackGapDays",
    "STRICT_DEBUG_WELCOME_BACK_GAP_DAYS"
)
val strictWelcomeBackExpiresInSeconds = optionalDebugSeconds(
    "strict.debug.welcomeBackExpiresInSeconds",
    "STRICT_DEBUG_WELCOME_BACK_EXPIRES_IN_SECONDS"
)
val strictWelcomeBackWarningBeforeSeconds = optionalDebugSeconds(
    "strict.debug.welcomeBackWarningBeforeSeconds",
    "STRICT_DEBUG_WELCOME_BACK_WARNING_BEFORE_SECONDS"
)
val strictWelcomeBackCooldownSeconds = optionalDebugSeconds(
    "strict.debug.welcomeBackCooldownSeconds",
    "STRICT_DEBUG_WELCOME_BACK_COOLDOWN_SECONDS"
)
val strictBillingProductIds = optionalBuildValue(
    "strict.billing.productIds",
    "STRICT_BILLING_PRODUCT_IDS"
).orEmpty()
val allowedDebugBilling = setOf(
    "REAL",
    "AVAILABLE",
    "LOADING",
    "UNAVAILABLE",
    "CANCELED",
    "PENDING",
    "VERIFY_FAIL",
    "ACTIVE",
    "EXPIRED"
)
val strictDebugBilling = optionalBuildValue("strict.debug.billing", "STRICT_DEBUG_BILLING") ?: "REAL"
// Debug entitlement, Pro Discovery, and billing values never bypass Google sign-in.
require(strictDebugBilling in allowedDebugBilling) {
    "strict.debug.billing must be one of ${allowedDebugBilling.joinToString(", ")}. Found \"$strictDebugBilling\"."
}

fun releaseSigningValue(propertyNames: List<String>, envName: String): String? {
    System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    for (fileName in listOf("keystore.properties", "local.properties")) {
        val secretFile = rootProject.file(fileName)
        if (!secretFile.isFile) continue
        val props = Properties()
        secretFile.reader(Charsets.UTF_8).use { props.load(it) }
        for (propertyName in propertyNames) {
            props.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return null
}

val releaseStoreFilePath = releaseSigningValue(
    listOf("storeFile", "mmm.release.storeFile"),
    "MMM_RELEASE_STORE_FILE"
)
val releaseStorePassword = releaseSigningValue(
    listOf("storePassword", "mmm.release.storePassword"),
    "MMM_RELEASE_STORE_PASSWORD"
)
val releaseKeyAlias = releaseSigningValue(
    listOf("keyAlias", "mmm.release.keyAlias"),
    "MMM_RELEASE_KEY_ALIAS"
)
val releaseKeyPassword = releaseSigningValue(
    listOf("keyPassword", "mmm.release.keyPassword"),
    "MMM_RELEASE_KEY_PASSWORD"
)
val releaseSigningMissing = buildList {
    if (releaseStoreFilePath == null) {
        add("store file (keystore.properties storeFile, local.properties mmm.release.storeFile, or MMM_RELEASE_STORE_FILE)")
    }
    if (releaseStorePassword == null) {
        add("store password (storePassword, mmm.release.storePassword, or MMM_RELEASE_STORE_PASSWORD)")
    }
    if (releaseKeyAlias == null) {
        add("key alias (keyAlias, mmm.release.keyAlias, or MMM_RELEASE_KEY_ALIAS)")
    }
    if (releaseKeyPassword == null) {
        add("key password (keyPassword, mmm.release.keyPassword, or MMM_RELEASE_KEY_PASSWORD)")
    }
}
val hasCompleteReleaseSigning = releaseSigningMissing.isEmpty()
val releaseStoreFile = releaseStoreFilePath?.let { path ->
    val configuredStore = file(path)
    if (configuredStore.isAbsolute) configuredStore else rootProject.file(path)
}

fun releaseSigningFailure(): String? {
    if (!hasCompleteReleaseSigning) {
        return buildString {
            appendLine("Release signing is required for a Play upload artifact and is not configured.")
            appendLine("Missing:")
            releaseSigningMissing.forEach { appendLine("- $it") }
            appendLine("Put the values in gitignored keystore.properties, or export the MMM_RELEASE_* variables.")
            appendLine("local.properties mmm.release.* is also accepted. Passwords are not printed.")
            appendLine("The debug keystore is not used for release, and an unsigned AAB or APK is not a Play upload.")
            append("See docs/android-release-signing.md. This build does not create a keystore.")
        }
    }
    val store = releaseStoreFile
    if (store == null || !store.isFile) {
        return "Release keystore file was not found at ${store ?: "(no path)"}. " +
            "The path is configured, but the file is not there. This build does not create a keystore. " +
            "See docs/android-release-signing.md."
    }
    return null
}

android {
    namespace = "app.mymusclemap"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.strictworkout.app"
        minSdk = 26
        targetSdk = 37
        // First Play upload. versionCode must increase for every later upload. Do not lower it.
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningFailure() == null) {
            create("release") {
                storeFile = releaseStoreFile
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
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "STRICT_API_BASE_URL", buildConfigString(strictReleaseApiUrl))
            buildConfigField("String", "STRICT_GOOGLE_SERVER_CLIENT_ID", buildConfigString(strictGoogleServerClientId))
            buildConfigField("String", "STRICT_BILLING_PRODUCT_IDS", buildConfigString(strictBillingProductIds))
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "STRICT_API_BASE_URL", buildConfigString(strictDebugApiUrl))
            buildConfigField("String", "STRICT_GOOGLE_SERVER_CLIENT_ID", buildConfigString(strictGoogleServerClientId))
            buildConfigField("String", "STRICT_BILLING_PRODUCT_IDS", buildConfigString(strictBillingProductIds))
            buildConfigField("String", "STRICT_DEBUG_BILLING", buildConfigString(strictDebugBilling))
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
            buildConfigField("String", "STRICT_WELCOME_BACK", buildConfigString(strictWelcomeBack))
            buildConfigField(
                "String",
                "STRICT_WELCOME_BACK_GAP_DAYS",
                buildConfigString(strictWelcomeBackGapDays)
            )
            buildConfigField(
                "String",
                "STRICT_WELCOME_BACK_EXPIRES_IN_SECONDS",
                buildConfigString(strictWelcomeBackExpiresInSeconds)
            )
            buildConfigField(
                "String",
                "STRICT_WELCOME_BACK_WARNING_BEFORE_SECONDS",
                buildConfigString(strictWelcomeBackWarningBeforeSeconds)
            )
            buildConfigField(
                "String",
                "STRICT_WELCOME_BACK_COOLDOWN_SECONDS",
                buildConfigString(strictWelcomeBackCooldownSeconds)
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
        // Release packaging runs lint. Warnings stay warnings.
        // Do not add a baseline that hides the existing warning set.
        checkReleaseBuilds = true
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
            includeTestsMatching("app.mymusclemap.WelcomeBackDebugSelectionReleaseTest")
            includeTestsMatching("app.mymusclemap.BillingGatewaySelectionReleaseTest")
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
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.appcompat)
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
    implementation(libs.billing)
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

fun requestsSignedReleaseArtifact(name: String): Boolean {
    return name == "assembleRelease" ||
        name == "bundleRelease" ||
        name == "packageRelease" ||
        name == "packageReleaseBundle" ||
        name == "signReleaseBundle" ||
        (name.startsWith("sign") && name.contains("Release"))
}

tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "Checks upload-key signing without printing secrets or creating a keystore."
    doLast {
        val failure = releaseSigningFailure()
        if (failure != null) {
            error(failure)
        }
        logger.lifecycle(
            "Release signing is configured. The keystore file exists and the alias and passwords are set. " +
                "Secret values are not printed."
        )
    }
}

gradle.taskGraph.whenReady {
    val touchesSignedRelease = gradle.taskGraph.allTasks.any { requestsSignedReleaseArtifact(it.name) }
    if (!touchesSignedRelease) return@whenReady
    val problems = mutableListOf<String>()
    releaseSigningFailure()?.let { problems += it }
    if (strictGoogleServerClientId.isBlank()) {
        problems += "Release packaging requires the Web OAuth client ID. Set " +
            "strict.google.serverClientId in gitignored local.properties, or " +
            "STRICT_GOOGLE_SERVER_CLIENT_ID. A release without it cannot sign anyone in. " +
            "Debug builds still compile and fail closed at sign-in. The client ID is not printed."
    }
    if (problems.isNotEmpty()) {
        error(problems.joinToString(separator = "\n\n"))
    }
}
