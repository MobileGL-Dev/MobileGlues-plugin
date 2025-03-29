plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fcl.plugin.mobileglues"
    compileSdk = 35

    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.fcl.plugin.mobileglues"
        minSdk = 26
        targetSdk = 35
        versionCode = 1220
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: project.findProperty("SIGNING_STORE_PASSWORD") as String?
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: project.findProperty("SIGNING_KEY_ALIAS") as String?
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: project.findProperty("SIGNING_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        configureEach {
            resValue("string","app_name","MobileGlues")

            manifestPlaceholders["des"] = "MobileGlues (OpenGL 4.0, 1.17+)"
            manifestPlaceholders["renderer"] = "MobileGlues:libmobileglues.so:libEGL.so"

            manifestPlaceholders["boatEnv"] = mutableMapOf<String,String>().apply {
                put("LIBGL_ES", "3")
                put("DLOPEN", "libspirv-cross-c-shared.so,libshaderconv.so")
            }.run {
                var env = ""
                forEach { (key, value) ->
                    env += "$key=$value:"
                }
                env.dropLast(1)
            }
            manifestPlaceholders["pojavEnv"] = mutableMapOf<String,String>().apply {
                put("LIBGL_ES", "3")
                put("DLOPEN", "libspirv-cross-c-shared.so,libshaderconv.so")
                put("POJAV_RENDERER", "opengles3")
            }.run {
                var env = ""
                forEach { (key, value) ->
                    env += "$key=$value:"
                }
                env.dropLast(1)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.documentfile)
    implementation(libs.gson)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.google.material)
    implementation(project(":MobileGlues"))
}