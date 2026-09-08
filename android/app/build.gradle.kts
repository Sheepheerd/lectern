import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing comes from keystore.properties for local builds and from the
 * environment in CI. Both paths use the same key, so an APK built here and one
 * built by Actions install over each other.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(environment)

android {
    namespace = "com.lectern"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lectern"
        minSdk = 26
        targetSdk = 36
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 101
        versionName = (findProperty("appVersion") as String?) ?: "0.1.1"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Unsigned when no key is configured, so a clone still builds.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    sourceSets["main"].kotlin.directories.add("src/main/kotlin")
    sourceSets["test"].kotlin.directories.add("src/test/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Text extraction: PDF pages + outline, EPUB XHTML.
    implementation(libs.pdfbox.android)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
