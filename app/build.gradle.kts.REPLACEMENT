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

        // Start with modern physical Android devices only.
        // This keeps native output smaller and matches Wi-Fi-debug testing.
        ndk {
            abiFilters += listOf(
                "arm64-v8a"
            )
        }

        externalNativeBuild {
            cmake {
                arguments +=
                    "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    buildTypes {
        debug {
            // An unoptimized native Whisper build is dramatically slower.
            externalNativeBuild {
                cmake {
                    arguments +=
                        "-DCMAKE_BUILD_TYPE=Release"
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

    // Same NDK revision used by the official whisper.cpp Android example.
    ndkVersion = "25.2.9519653"

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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}
