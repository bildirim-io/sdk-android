plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

android {
    namespace = "io.bildirim.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Sürüm SABİTLENMEZ: müşteri kendi firebase-bom / firebase-messaging sürümünü ekler.
    // compileOnly olduğu için POM'a bağımlılık olarak yazılmaz; sürüm çakışması riski müşteriye taşınmaz.
    compileOnly(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = property("POM_ARTIFACT_ID") as String
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Bildirim Android SDK")
                description.set("Bildirim (bildirim.io) push bildirim SDK'sı — Android")
                url.set("https://bildirim.io")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                scm { url.set("https://github.com/bildirim-io/sdk-android") }
            }
        }
    }
}
