import com.expediagroup.graphql.plugin.gradle.config.GraphQLParserOptions
import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask

val ktorVersion = "3.2.0"
val logbackVersion = "1.5.18"
val logstashEncoderVersion = "8.1"
val bigQueryClientVersion = "2.51.0"
val kotlinxDatetimeVersion = "0.6.2"
val jwtVersion = "4.5.0"
val bouncyCastleVersion = "1.81"

val expediaGraphQlVersion = "8.8.0"

val junitVersion = "5.13.1"

val mainClassName = "no.nav.security.MainKt"

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.expediagroup.graphql") version "8.8.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

    implementation("com.expediagroup:graphql-kotlin-ktor-client:$expediaGraphQlVersion")

    // GitHub App authentication dependencies
    implementation("com.auth0:java-jwt:$jwtVersion")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.google.cloud:google-cloud-bigquery:$bigQueryClientVersion")

    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val graphqlGenerateClient by tasks.getting(GraphQLGenerateClientTask::class) {
    packageName.set("no.nav.security")
    schemaFile.set(file("${project.projectDir}/src/main/resources/github/schema.graphql"))
    queryFiles.from("${project.projectDir}/src/main/resources/github/FetchGithubRepositoriesQuery.graphql")
    parserOptions.assign(GraphQLParserOptions(maxTokens = 100000, maxCharacters = 2048576))
    serializer.set(GraphQLSerializer.KOTLINX)
}

val graphqlGenerateOtherClient by tasks.creating(GraphQLGenerateClientTask::class) {
    packageName.set("no.nav.security")
    schemaFile.set(file("${project.projectDir}/src/main/resources/nais/schema.graphql"))
    queryFiles.from(file("${project.projectDir}/src/main/resources/nais/TeamStatsQuery.graphql"))
    serializer.set(GraphQLSerializer.KOTLINX)
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClassName
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.14.2"
    }
}
