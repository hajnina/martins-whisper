plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nativeEnabled = providers.gradleProperty("martins.native").orNull?.lowercase() == "true"
val vulkanEnabled = providers.gradleProperty("martins.vulkan").orNull?.lowercase() == "true"
// NDK used only by native builds. CI pins the official r27; the aarch64 sandbox can
// override it to the community r30 package installed by scripts/bootstrap-build.sh --with-ndk.
val nativeNdkVersion = providers.gradleProperty("martins.ndkVersion").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "27.0.12077973"
// CMake is resolved from the SDK. CI reuses the runner's preinstalled CMake instead of
// downloading the default one; override only when you know the SDK copy exists.
val nativeCmakeVersion = providers.gradleProperty("martins.cmakeVersion").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "3.22.1"

android {
    namespace = "com.martins.whisper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.martins.whisper"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (nativeEnabled) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DGGML_VULKAN=${if (vulkanEnabled) "ON" else "OFF"}",
                        "-DGGML_NATIVE=OFF",
                        "-DGGML_OPENMP=OFF",
                        "-DWHISPER_BUILD_TESTS=OFF",
                        "-DWHISPER_BUILD_EXAMPLES=OFF",
                        "-DWHISPER_BUILD_SERVER=OFF",
                    )
                    cppFlags += listOf("-std=c++17")
                }
            }
        }
    }

    buildTypes {
        debug {
            if (nativeEnabled) {
                externalNativeBuild {
                    cmake {
                        arguments += "-DCMAKE_BUILD_TYPE=Release"
                    }
                }
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (nativeEnabled) {
                externalNativeBuild {
                    cmake {
                        arguments += "-DCMAKE_BUILD_TYPE=Release"
                    }
                }
            }
        }
    }

    if (nativeEnabled) {
        ndkVersion = nativeNdkVersion
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = nativeCmakeVersion
            }
        }
        packaging {
            jniLibs {
                useLegacyPackaging = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(testFixtures(project(":core")))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
