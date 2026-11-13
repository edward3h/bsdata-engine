plugins {
    application
    id("org.ethelred.bsdata.java-conventions")
}

dependencies {
    implementation(project(":engine-core"))
}

application {
    mainClass = "org.ethelred.bsdata.jvm.Main"
}
