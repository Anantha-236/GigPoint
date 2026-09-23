import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.gigpoint"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gigpoint"

        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf(
                "arm64-v8a"
            )
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }
    }

    /*
     * AGP 8 disables BuildConfig generation by default.
     *
     * BackendClient uses BuildConfig.DEBUG to allow localhost
     * connections only during debug builds, so BuildConfig must
     * explicitly be generated.
     */
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isDebuggable = true

            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DGGML_OPENMP=OFF"
                    )
                }
            }
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    ndkVersion =
        "28.2.13676358"

    externalNativeBuild {
        cmake {
            path =
                file(
                    "src/main/cpp/whisper/CMakeLists.txt"
                )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            JvmTarget.JVM_11
        )
    }
}

dependencies {
    implementation(
        libs.androidx.appcompat
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.material
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}