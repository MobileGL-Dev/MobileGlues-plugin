plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.capslock800000.optimizedmg"
    compileSdk = 36

    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.capslock800000.optimizedmg"
        minSdk = 26
        targetSdk = 36
        versionCode = 1350
        versionName = "1.3.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("OMGK") ?: project.findProperty("OMGK") as String?
            keyAlias = System.getenv("OMGK") ?: project.findProperty("OMGK") as String?
            keyPassword = System.getenv("OMGK") ?: project.findProperty("OMGK") as String?
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
            resValue("string","app_name","OptimizedMG")

            manifestPlaceholders["des"] = "OptimizedMG (OpenGL 4.0, 1.17+)"
            manifestPlaceholders["renderer"] = "OptimizedMG:liboptimizedmg.so:liboptimizedmg.so"

            manifestPlaceholders["minMCVer"] = "1.17"
            manifestPlaceholders["maxMCVer"] = "" //为空则不限制 No restriction if empty

            manifestPlaceholders["boatEnv"] = mutableMapOf<String,String>().apply {
                put("LIBGL_ES", "3")
            }.run {
                var env = ""
                forEach { (key, value) ->
                    env += "$key=$value:"
                }
                env.dropLast(1)
            }
            manifestPlaceholders["pojavEnv"] = mutableMapOf<String,String>().apply {
                put("LIBGL_ES", "3")
                put("POJAV_RENDERER", "opengles3")
				put("POJAVEXEC_EGL", "liboptimizedmg.so")
				put("LIBGL_EGL", "liboptimizedmg.so")
            }.run {
                var env = ""
                forEach { (key, value) ->
                    env += "$key=$value:"
                }
                env.dropLast(1)
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
    implementation(libs.gson)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.google.material)
    implementation(project(":MobileGlues"))
}
