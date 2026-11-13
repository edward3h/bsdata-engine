pluginManagement {
    repositories {
        maven("https://teavm.org/maven/repository")
        gradlePluginPortal()
    }
}

rootProject.name = "bsdata-engine"

include("engine-core")
include("engine-web")
include("engine-jvm")
