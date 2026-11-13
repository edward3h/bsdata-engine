plugins {
    java
    id("org.ethelred.bsdata.java-conventions")
    id("org.teavm") version "0.16.0"
}

repositories {
    maven("https://teavm.org/maven/repository")
}

dependencies {
    implementation(project(":engine-core"))
    implementation(teavm.libs.jso)
    implementation(teavm.libs.jsoApis)
}

teavm {
    all {
        mainClass = "org.ethelred.bsdata.web.Main"
    }
    js {
        targetFileName = "engine.js"
        sourceMap = true
    }
}
