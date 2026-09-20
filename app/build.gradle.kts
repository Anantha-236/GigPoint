plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gigpoint"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.gigpoint"

        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        /*
         * DhwaniMitra currently targets modern
         * 64-bit Android devices.
         *
         * This reduces APK size because we only
         * package ARM64 Whisper/GGML libraries.
         */
        ndk {
            abiFilters += listOf(
                "arm64-v8a"
            )
        }

        /*
         * Whisper should always be compiled
         * using optimized native code.
         */
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }
    }

    buildTypes {

        debug {

            /*
             * Keep Whisper/GGML optimized even
             * while the Android application itself
             * is a debug build.
             */
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

            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    /*
     * NDK r28+ generates 16 KB aligned
     * native libraries by default.
     *
     * Required for modern Android devices
     * and Google Play 16 KB page-size support.
     */
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path =
                file(
                    "src/main/cpp/whisper/CMakeLists.txt"
                )
        }
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