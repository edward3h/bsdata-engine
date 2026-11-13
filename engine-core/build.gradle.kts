plugins {
    `java-library`
    id("org.ethelred.bsdata.java-conventions")
    id("org.teavm.library") version "0.16.0"
}

dependencies {
    api("com.eclipsesource.minimal-json:minimal-json:0.9.5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
