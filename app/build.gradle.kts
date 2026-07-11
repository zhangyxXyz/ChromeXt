import java.util.Properties

plugins {
  id("com.android.application")
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

  buildFeatures { buildConfig = true }

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
  compileOnly("androidx.annotation:annotation:1.10.0")
  compileOnly("io.github.libxposed:api:102.0.0")
  implementation("io.github.libxposed:service:102.0.0")
}
