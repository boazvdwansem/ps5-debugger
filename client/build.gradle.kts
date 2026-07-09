import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionCatalog

val libs: VersionCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("com.android.application") version "8.5.2"
}

group = "com.osr.ps5debugger"
version = "1.0.2"

kotlin {
    jvmToolchain(21)

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        compilations.create("mobile") {
            defaultSourceSet {
                dependencies {
                    implementation(project.dependencies.extensions.getByType(org.jetbrains.compose.ComposePlugin.Dependencies::class.java).desktop.currentOs)
                    implementation(libs.findLibrary("compose.ui").get())
                    implementation(libs.findLibrary("compose.runtime").get())
                }
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17) // Use JVM 17 compile target for Android devices
        }
    }

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.findLibrary("compose.runtime").get())
                implementation(libs.findLibrary("compose.foundation").get())
                implementation(libs.findLibrary("compose.material3").get())
                implementation(libs.findLibrary("compose.ui").get())
                implementation(libs.findLibrary("compose.resources").get())
                implementation(libs.findLibrary("compose.icons").get())
            }
        }

        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.findLibrary("compose.ui").get())
                implementation(libs.findLibrary("compose.runtime").get())
                implementation(libs.findLibrary("kotlinx.coroutines.swing").get())
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.findLibrary("androidx-activity-compose").get())
                implementation(libs.findLibrary("compose.ui").get())
                implementation(libs.findLibrary("compose.runtime").get())
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.osr.ps5debugger"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.osr.ps5debugger"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.osr.ps5debugger.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "PS5Debugger"
            packageVersion = "1.0.2"
        }
    }
}
