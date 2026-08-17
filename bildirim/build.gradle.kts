plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

android {
    namespace = "io.bildirim.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Yalnız enstrümantasyon testleri (APK'ya girmez, kütüphaneyle yayınlanmaz):
    // Robolectric'in göstermediği davranışlar — kanal gerçekten oluştu mu, bildirim gerçekten
    // durum çubuğuna düştü mü, izin akışı.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

// Yayın (Faz E): Sonatype Central Portal "bundle" yükleme akışı — scripts/release-android.sh
// Yerel Maven deposu (build/maven-repo) üretilir, imzalanır, zip'lenir ve Portal'a yüklenir.
publishing {
    repositories {
        maven {
            name = "bundle"
            url = uri(layout.buildDirectory.dir("maven-repo"))
        }
    }
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
                developers { developer { id.set("bildirim"); name.set("Bildirim"); email.set("destek@bildirim.io") } }
                scm {
                    url.set("https://github.com/bildirim-io/sdk-android")
                    connection.set("scm:git:https://github.com/bildirim-io/sdk-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/bildirim-io/sdk-android.git")
                }
            }
        }
    }
}

// İmza yalnız anahtar verildiyse (CI/yayın makinesi). Geliştirici makinesinde build kırılmaz.
// SIGNING_KEY: ASCII-armored özel anahtar (gpg --armor --export-secret-keys), SIGNING_PASSWORD: parolası.
signing {
    val key = System.getenv("SIGNING_KEY") ?: findProperty("signing.key") as String?
    val pass = System.getenv("SIGNING_PASSWORD") ?: findProperty("signing.password") as String?
    isRequired = key != null
    if (key != null) {
        useInMemoryPgpKeys(key, pass ?: "")
        sign(publishing.publications)
    }
}
