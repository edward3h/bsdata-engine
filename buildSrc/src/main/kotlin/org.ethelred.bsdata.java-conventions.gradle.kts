plugins {
    java
    id("com.diffplug.spotless")
    id("com.github.jakemarsden.git-hooks")
    id("org.checkerframework")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

gitHooks {
    setHooks(mapOf("pre-commit" to "build"))
}

val eisopVersion = "3.49.5-eisop1"

checkerFramework {
    checkers = listOf("org.checkerframework.checker.nullness.NullnessChecker")
    version = eisopVersion
    extraJavacArgs = listOf("-AskipFiles=.*/build/generated.*")
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.1")
    compileOnly("io.github.eisop:checker-qual:$eisopVersion")
    testCompileOnly("io.github.eisop:checker-qual:$eisopVersion")
    checkerQual("io.github.eisop:checker-qual:$eisopVersion")
    checkerFramework("io.github.eisop:checker:$eisopVersion")
}

spotless {
    format("misc") {
        target("*.md", ".gitignore")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        targetExclude("build/**")
        importOrder()
        removeUnusedImports()
        palantirJavaFormat()
        licenseHeader("/* (C) Edward Harman and contributors \$YEAR */").updateYearWithLatest(true)
    }
    kotlinGradle {
        ktlint()
    }
    json {
        target("*.json")
        simple()
    }
}
