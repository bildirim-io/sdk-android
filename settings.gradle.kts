pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bildirim-android"
include(":bildirim")
// Örnek uygulama yalnız google-services.json varsa derlemeye katılır — kütüphane
// tek başına (CI, katkıcı) o dosya olmadan derlensin diye.
if (file("ornek-uygulama/google-services.json").exists()) {
    include(":ornek-uygulama")
} else {
    logger.lifecycle("ornek-uygulama atlandı: ornek-uygulama/google-services.json yok")
}
