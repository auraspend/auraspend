import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

// Release signing credentials live in secrets.properties (gitignored). The file is optional:
// without it the release build still configures, but is explicitly reported as UNSIGNED so a
// green "assembleFreeRelease" can never be mistaken for a shippable artifact again.
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { stream -> load(stream) }
    }
}
fun secret(key: String): String? = secrets.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
val releaseStorePath = secret("KEYSTORE_PATH")
val hasReleaseSigning = releaseStorePath != null &&
    secret("KEYSTORE_PASSWORD") != null &&
    secret("KEY_ALIAS") != null &&
    secret("KEY_PASSWORD") != null

android {
    namespace = "com.awbuilds.auraspend"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.awbuilds.auraspend"
        minSdk = 30
        targetSdk = 37
        // 0.1.3 — categorisation fixes measured against a real inbox (handoff
        // 0029), on top of the visible queue/notification flow (0028).
        // versionCode must strictly increase for every store upload; Play
        // rejects an update that reuses one, so it is not optional the way
        // the name is.
        versionCode = 4
        versionName = "0.1.3"

        // True in dev/prod; the `fdroid` flavor flips it to false (ADR 0012).
        // Screens read it to hide the Drive entry points rather than offer
        // buttons that lead nowhere.
        buildConfigField("boolean", "DRIVE_BACKUP", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // Native ABI filter. ONNX Runtime's prebuilt .so is ~95% of the app's
        // footprint, so the ABI list is the single biggest size lever there is.
        // Measured 2026-09-29, native payload per ABI:
        //
        //   arm64-v8a     17.5 MB   real consumer hardware
        //   armeabi-v7a   12.7 MB   32-bit low-end hardware still ships
        //   x86           20.6 MB   dropped — no device or current emulator
        //   x86_64        21.0 MB   dropped — no Chromebooks, no emulators
        //
        // Play's 64-bit requirement is met by arm64-v8a. The cost is 21 MB of
        // native payload, which takes the release AAB from 35.4 MB to 20.5 MB.
        //
        // **ChromeOS is not a supported form factor.** Dropping x86_64 makes the
        // app uninstallable on Chromebooks. That is deliberate, not an
        // oversight — see the `ChromeOsAbiSupport` suppression in `lint` below,
        // which is the place to change your mind.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Environments (ADR 0010, ADR 0012). `dev` exists so a development build
    // can sit next to the store build on one device: it gets a `.dev`
    // application id and its own "AuraSpend Dev" label. `prod` is the Play
    // build and the GitHub-release APK. `fdroid` is the F-Droid build: the
    // same package id as prod, but compiled without the non-free Google
    // dependencies — Drive backup does not exist there (ADR 0012) and every
    // other feature is identical.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // "AuraSpend Dev" label lives in app/src/dev/res (both locales).
        }
        create("prod") {
            dimension = "env"
        }
        create("fdroid") {
            dimension = "env"
            buildConfigField("boolean", "DRIVE_BACKUP", "false")
        }
    }

    // The Google-Drive implementation lives in `src/gms`, wired into dev and
    // prod only (ADR 0012): the `fdroid` flavor compiles its own no-op
    // `DriveSyncManager` from `src/fdroid` and carries no Google Play Services
    // in its runtime classpath, which F-Droid's inclusion policy requires.
    // Both directory sets are needed: AGP 9's built-in Kotlin compiles from its
    // own `kotlin` source dirs, and a `java.srcDir` alone does not reach it
    // (found by the fdroid flavor building green while dev lost the class).
    sourceSets {
        getByName("dev") {
            java.srcDir("src/gms/java")
            kotlin.srcDir("src/gms/java")
        }
        getByName("prod") {
            java.srcDir("src/gms/java")
            kotlin.srcDir("src/gms/java")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Keep debug builds fast: no shrinking, but still use the same dex pipeline.
            isPseudoLocalesEnabled = false
        }
        release {
            // Full R8: shrink + optimize + obfuscate + resource shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Previously this type had no signingConfig at all, so `assembleProdRelease`
            // produced an uninstallable APK. Now it is signed whenever credentials exist.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "AuraSpend: no release keystore in secrets.properties " +
                        "(KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD) — " +
                        "release builds will be UNSIGNED."
                )
                null
            }
        }
    }
    // Static analysis. There was no lint configuration at all, which is how the
    // scoped-storage CSV crash and the unguarded enum valueOf calls slipped through
    // a "green" build. The 100+ pre-existing issues are pinned in
    // app/lint-baseline.xml; anything new fails the build.
    // Prints the accuracy floor for the golden corpus (regex only, no on-device AI).
    // Any runtime swap is judged against this number, so it has to be cheap to read.
    tasks.register("classificationBaseline") {
        group = "verification"
        description = "Run the golden SMS corpus through the regex path and print per-field accuracy."
        dependsOn("testProdDebugUnitTest")
        doLast {
            logger.lifecycle("See the REGEX-ONLY BASELINE block in the test output for :app:testProdDebugUnitTest")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        checkReleaseBuilds = true
        explainIssues = true
        xmlReport = true
        htmlReport = true
        baseline = file("lint-baseline.xml")

        // Version-nag checks duplicate Dependabot (which opens PRs weekly for
        // Gradle deps and monthly for actions) and their messages embed exact
        // version numbers — so every upstream release re-breaks the baseline
        // ("...9.8.0" becomes "...9.8.1"). Report them as information instead
        // of errors; actionable updates stay Dependabot's job.
        informational += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")

        // AuraSpend does not support ChromeOS, by decision rather than oversight.
        // Chromebooks run Android apps on x86_64, so removing that ABI from
        // `abiFilters` (which saves 21 MB of ONNX Runtime native payload and
        // takes the release AAB from 35.4 MB to 20.5 MB) also makes the app
        // uninstallable there. Lint flags that combination by default; the
        // check is disabled deliberately rather than worked around.
        //
        // To change this: add "x86_64" back to `abiFilters` and delete this line.
        // Do not suppress any other check to recover the size.
        disable += "ChromeOsAbiSupport"
    }

    // Unit tests exercise JVM-only logic; unmocked android.framework calls (e.g. Log) no-op.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric renders real resources (fonts, colors) on the JVM.
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Built-in Kotlin (AGP 9): jvmTarget defaults to compileOptions.targetCompatibility.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// Room schema JSON. `exportSchema = true` on AppDatabase exports nothing without
// this: Room writes app/schemas/<version>.json, which is what makes a schema
// change reviewable in a diff and what MigrationTestHelper reads. The files are
// meant to be committed — they are the migration-test fixtures.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose — versions are aligned by the BOM (Material 3 1.4.0, Compose UI 1.12.1).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // androidx core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // viewModel() + ViewModelStoreOwner-scoped factories (survives rotation).
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Foreground/background process detection for background classification.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Google Sign-In & Drive — dev/prod only (ADR 0012): F-Droid's inclusion
    // policy forbids non-free dependencies, so the `fdroid` flavor carries
    // none of these and ships a no-op DriveSyncManager instead.
    "devImplementation"(libs.play.services.auth)
    "prodImplementation"(libs.play.services.auth)
    "devImplementation"(libs.google.api.services.drive)
    "prodImplementation"(libs.google.api.services.drive)
    "devImplementation"(libs.google.api.client)
    "prodImplementation"(libs.google.api.client)
    "devImplementation"(libs.google.api.client.android)
    "prodImplementation"(libs.google.api.client.android)
    "devImplementation"(libs.google.http.client.gson)
    "prodImplementation"(libs.google.http.client.gson)
    // Guava arrives transitively through google-api-client / google-http-client
    // and is attributed on that basis (LicenseScreen, docs/licenses). It was
    // declared directly here with no com.google.common import anywhere in
    // app/src; the declaration did not even control the version that shipped,
    // because the client libraries' requirement wins version resolution.

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Installs baseline profiles at first boot when present (startup performance).
    implementation(libs.androidx.profileinstaller)

    // On-device inference. The encoder runs on ONNX Runtime Mobile; the model
    // itself is downloaded separately (22 MB, SHA-256 verified) rather than
    // shipped in the APK, so users who never opt in pay nothing for it.
    implementation(libs.onnxruntime.android)
    implementation(libs.okhttp)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // Room PagingSource integration + collectAsLazyPagingItems (Activity list).
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.compose)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // `runTest` + `Dispatchers.setMain` for ViewModel tests. The suite had no
    // ViewModel coverage at all, not because the logic is untestable but because
    // there was no way to own the main dispatcher.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    // The instrumentation runner, declared because nothing else provides it any
    // more. testInstrumentationRunner above names AndroidJUnitRunner, which used
    // to arrive transitively through espresso-core — a dependency neither
    // androidTest file imports a single class from.
    androidTestImplementation(libs.androidx.test.runner)

    // JVM tests (Robolectric + Compose UI test): swipe gestures, design-system guard.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Compose previews / inspection in debug builds only.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
