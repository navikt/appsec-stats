import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.graphql
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "2.3.11"
val logbackVersion = "1.5.6"
val logstashEncoderVersion = "7.4"
val bigQueryClientVersion = "2.39.1"

val expediaGraphQlVersion = "7.1.1"

val junitJupiterVersion = "5.10.2"

val mainClassName = "no.nav.security.MainKt"

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("com.expediagroup.graphql") version "7.1.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("com.expediagroup:graphql-kotlin-ktor-client:$expediaGraphQlVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.google.cloud:google-cloud-bigquery:$bigQueryClientVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

graphql {
    client {
        packageName = "no.nav.security"
        serializer = GraphQLSerializer.KOTLINX
        schemaFile = file("${project.projectDir}/src/main/resources/github.graphql")
        queryFiles = listOf(file("${project.projectDir}/src/main/resources/FetchGithubStatsQuery.graphql"), file("${project.projectDir}/src/main/resources/FetchGithubTeamsQuery.graphql"), file("${project.projectDir}/src/main/resources/FetchGithubRepositoriesQuery.graphql"))
        parserOptions {
            maxCharacters = 2048576
            maxTokens = 100000
        }
    }
}

val graphqlGenerateClient by tasks.getting(GraphQLGenerateClientTask::class) {
    packageName.set("no.nav.security")
    schemaFile.set(file("${project.projectDir}/src/main/resources/github.graphql"))
    serializer.set(GraphQLSerializer.KOTLINX)
}

tasks {
    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        manifest {
            attributes["Main-Class"] = mainClassName
        }
    }


    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.7"
    }
}
