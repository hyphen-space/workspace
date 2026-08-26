import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val nativeAccessArg = "--enable-native-access=ALL-UNNAMED"

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
    jacoco
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "ca.tantalum"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

application {
    mainClass.set("ca.tantalum.wgkeys.MainKt")
    applicationDefaultJvmArgs = listOf(nativeAccessArg)
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("io.ktor:ktor-server-auth:3.5.2")
    implementation("io.ktor:ktor-server-body-limit:3.5.2")
    implementation("io.ktor:ktor-server-cio:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-server-rate-limit:3.5.2")
    implementation("io.ktor:ktor-server-status-pages:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("io.ktor:ktor-server-test-host:3.5.2")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(nativeAccessArg)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("25")
}

tasks.check {
    dependsOn(tasks.ktlintCheck, tasks.detekt)
}
