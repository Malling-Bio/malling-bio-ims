rootProject.name = "malling-bio-ims"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(":apps:orchestrator")
include(":libs:domain")
include(":libs:ims-soap")
include(":libs:stub-ims")
include(":libs:spl-parser")
