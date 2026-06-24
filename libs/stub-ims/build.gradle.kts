plugins {
    `java-library`
}

java {
    val javaVersion: String by project
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

dependencies {
    api(project(":libs:ims-soap"))
    implementation(project(":libs:domain"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
