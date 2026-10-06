pluginManagement {
    repositories {
        google()
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

rootProject.name = "FairSwap-Android"
include(":app")
include(":core:network")
include(":core:designsystem")
include(":core:auth")
include(":feature:auth")
include(":core:discovery")
include(":feature:discovery")
include(":feature:listingdetail")
include(":feature:sellerprofile")
include(":core:favourites")
include(":feature:favourites")
include(":core:account")
include(":feature:account")
include(":core:sellerlistings")
include(":feature:sellerlistings")
include(":core:communication")
include(":feature:communication")
