plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("application")
}

application {
    mainClass.set("no.nav.security.MainKt")
    applicationName = "app"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.datetime)

    implementation(libs.kafka.clients)

    implementation(libs.bundles.github.auth)

    implementation(libs.bundles.logging)

    implementation(libs.google.cloud.bigquery)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }
}
