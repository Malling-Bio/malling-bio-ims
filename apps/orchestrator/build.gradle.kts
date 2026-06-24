plugins {
    java
    alias(libs.plugins.quarkus)
}

java {
    val javaVersion: String by project
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))

    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.arc)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.config.yaml)
    implementation(libs.quarkus.scheduler)

    implementation(project(":libs:domain"))
    implementation(project(":libs:ims-soap"))
    implementation(project(":libs:stub-ims"))
    implementation(project(":libs:spl-parser"))

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("quarkusDev") {
    notCompatibleWithConfigurationCache("Quarkus dev mode is not compatible with Gradle configuration cache")
}
