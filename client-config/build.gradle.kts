plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    api(project(":api-gateway-consumer-role-poc"))
    api(project(":pylon-lite-webclient"))
    api(project(":pylon-lite-okhttp3"))
    api(project(":client-contract"))
    api("io.github.openfeign:feign-core:10.12")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(testFixtures(project(":pylon-lite")))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
