plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.lisplanner.shared"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            implementation(libs.cmp.material3.navigation.suite)
            implementation(libs.cmp.adaptive)
            implementation(libs.cmp.icons.extended)
            implementation(libs.cmp.lifecycle.viewmodel.compose)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime.ktx)
            // Lectura de .mpp (solo Android). Se excluyen las dependencias de MPXJ para otros formatos.
            implementation("net.sf.mpxj:mpxj:${libs.versions.mpxj.get()}") {
                exclude(group = "org.xerial", module = "sqlite-jdbc")
                exclude(group = "com.jgoodies")
                exclude(group = "jakarta.xml.bind")
                exclude(group = "org.glassfish.jaxb")
                exclude(group = "com.sun.activation")
                exclude(group = "com.healthmarketscience.jackcess")
                exclude(group = "org.jsoup")
                exclude(group = "com.fasterxml.jackson.core")
            }
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.mpxj)
        }
    }
}
