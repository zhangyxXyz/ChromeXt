import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.ncorti.ktfmt.gradle")
}

val keystoreProperties =
    Properties().apply {
      val file = rootProject.file("keystore.properties")
      if (file.isFile) {
        file.inputStream().use(::load)
      }
    }

fun signingValue(name: String): String? =
    System.getenv(name)
        ?: keystoreProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull

val releaseSigningStoreFile = signingValue("CHROMEXT_SIGNING_STORE_FILE")
val releaseSigningStorePassword = signingValue("CHROMEXT_KEYSTORE_PASSWORD")
val releaseSigningKeyAlias = signingValue("CHROMEXT_KEY_ALIAS")
val releaseSigningKeyPassword = signingValue("CHROMEXT_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
            releaseSigningStoreFile,
            releaseSigningStorePassword,
            releaseSigningKeyAlias,
            releaseSigningKeyPassword,
        )
        .all { !it.isNullOrBlank() }

android {
  compileSdk = 37
  namespace = "org.matrix.chromext"

  defaultConfig {
    applicationId = "org.matrix.chromext"
    minSdk = 26
    targetSdk = 37
    versionCode = 19
    versionName = System.getenv("VERSION_NAME") ?: "3.8.10"
  }

  buildFeatures {
    aidl = true
    buildConfig = true
    compose = true
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseSigningStoreFile!!)
        storePassword = releaseSigningStorePassword
        keyAlias = releaseSigningKeyAlias
        keyPassword = releaseSigningKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isShrinkResources = true
      isMinifyEnabled = true
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles("proguard-rules.pro")
    }
  }

  androidResources {
    additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x42")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  lint {
    disable +=
        listOf(
            "Internationalization",
            "UnsafeIntentLaunch",
            "SetJavaScriptEnabled",
            "UnspecifiedRegisterReceiverFlag",
            "Usability:Icons",
            "ObsoleteSdkInt",
        )
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

  compileOnly("androidx.annotation:annotation:1.10.0")
  compileOnly("io.github.libxposed:api:102.0.0")
  implementation("io.github.libxposed:service:102.0.0")
  implementation(composeBom)
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("com.materialkolor:material-kolor:2.0.0")
  implementation("io.github.kyant0:backdrop:1.0.0")
  implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.38.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("net.lingala.zip4j:zip4j:2.11.6")
  implementation("androidx.documentfile:documentfile:1.1.0")
  debugImplementation("androidx.compose.ui:ui-tooling")
  testImplementation("junit:junit:4.13.2")
}
