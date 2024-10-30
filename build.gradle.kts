import com.expediagroup.graphql.plugin.gradle.config.GraphQLParserOptions
import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "3.0.0"
val logbackVersion = "1.5.12"
val logstashEncoderVersion = "8.0"
val bigQueryClientVersion = "2.43.2"
val kotlinxDatetimeVersion = "0.6.1"

val expediaGraphQlVersion = "8.2.1"

val junitJupiterVersion = "5.11.3"

val mainClassName = "no.nav.security.MainKt"

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.expediagroup.graphql") version "8.2.1"
    id("com.gradleup.shadow") version "8.3.3"
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
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

    implementation("com.expediagroup:graphql-kotlin-ktor-client:$expediaGraphQlVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.google.cloud:google-cloud-bigquery:$bigQueryClientVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
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
    queryFiles.from(listOf(
        file("${project.projectDir}/src/main/resources/nais/AdminsQuery.graphql"),
        file("${project.projectDir}/src/main/resources/nais/TeamStatsQuery.graphql")
    ))
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
}
