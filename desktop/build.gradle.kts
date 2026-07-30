import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))

                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sqlite.jdbc)
                // sqlite-jdbc pulls in an SLF4J API; bind it to a no-op so the
                // app doesn't print "failed to load class" noise on start.
                implementation(libs.slf4j.nop)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.dorybrain.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "DoryBrain"
            packageVersion = "1.0.0"

            // jlink strips the JDK down to what's declared here. Without
            // java.sql the packaged app starts and then dies on
            // NoClassDefFoundError: java/sql/DriverManager the moment
            // SqliteNoteStore opens the database — it compiles and runs fine
            // from Gradle, so this only shows up in the distributable.
            modules("java.sql", "java.naming")
        }
    }
}
