import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val productionSigningEnvironment = listOf(
    "EDUFLOW_KEYSTORE_PATH",
    "EDUFLOW_KEYSTORE_PASSWORD",
    "EDUFLOW_KEY_ALIAS",
    "EDUFLOW_KEY_PASSWORD"
).associateWith { System.getenv(it).orEmpty() }
val missingProductionSigningEnvironment = productionSigningEnvironment
    .filterValues { it.isBlank() }
    .keys
val productionSigningConfigured = missingProductionSigningEnvironment.isEmpty()
val productionArtifactRequested = gradle.startParameter.taskNames.any { taskName ->
    when (taskName.substringAfterLast(':').lowercase()) {
        "assemblerelease", "bundlerelease", "packagerelease" -> true
        else -> false
    }
}
val productionKeystoreFile = productionSigningEnvironment["EDUFLOW_KEYSTORE_PATH"]
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }

if (productionArtifactRequested) {
    if (!productionSigningConfigured) {
        throw GradleException(
            buildString {
                appendLine("Production signing is not configured.")
                appendLine("Missing environment variables:")
                missingProductionSigningEnvironment.forEach(::appendLine)
            }
        )
    }
    requireNotNull(productionKeystoreFile).let { keystore ->
        require(keystore.isAbsolute) {
            "EDUFLOW_KEYSTORE_PATH must be an absolute path outside the repository."
        }
        require(!keystore.toPath().normalize().startsWith(projectDir.toPath().normalize())) {
            "EDUFLOW_KEYSTORE_PATH must point outside the repository."
        }
        require(keystore.isFile && keystore.canRead()) {
            "The production keystore path is not a readable file: ${keystore.absolutePath}"
        }
    }
}

android {
    namespace = "com.eduflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eduflow.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (productionSigningConfigured) {
                signingConfig = signingConfigs.create("production") {
                    storeFile = productionKeystoreFile
                    storePassword = productionSigningEnvironment["EDUFLOW_KEYSTORE_PASSWORD"]
                    keyAlias = productionSigningEnvironment["EDUFLOW_KEY_ALIAS"]
                    keyPassword = productionSigningEnvironment["EDUFLOW_KEY_PASSWORD"]
                }
            }
        }
        create("qaRelease") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-qa"
            // Local performance-comparison build only: debug-signed, same package, never production signing.
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    kapt("androidx.room:room-compiler:2.6.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.room:room-testing:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
