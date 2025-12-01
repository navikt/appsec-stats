import com.expediagroup.graphql.plugin.gradle.config.GraphQLParserOptions
import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask

val ktorVersion = "3.3.3"
val logbackVersion = "1.5.21"
val logstashEncoderVersion = "9.0"
val bigQueryClientVersion = "2.56.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val jwtVersion = "4.5.0"
val bouncyCastleVersion = "1.83"

val expediaGraphQlVersion = "8.8.1"

val junitVersion = "6.0.1"

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.expediagroup.graphql") version "8.8.1"
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
    schemaFile.set(file("${project.projectDir}/src/main/resources/github/schema.graphqls"))
    queryFiles.from(
        "${project.projectDir}/src/main/resources/github/FetchGithubRepositoriesQuery.graphql",
        "${project.projectDir}/src/main/resources/github/FetchGithubVulnerabilitiesQuery.graphql",
    )
    parserOptions.assign(GraphQLParserOptions(maxTokens = 100000, maxCharacters = 8048576))
    serializer.set(GraphQLSerializer.KOTLINX)
}

val graphqlGenerateOtherClient by tasks.creating(GraphQLGenerateClientTask::class) {
    packageName.set("no.nav.security")
    schemaFile.set(file("${project.projectDir}/src/main/resources/nais/schema.graphqls"))
    queryFiles.from(
        file("${project.projectDir}/src/main/resources/nais/TeamStatsQuery.graphql"),
        file("${project.projectDir}/src/main/resources/nais/EnvironmentsQuery.graphql"),
        file("${project.projectDir}/src/main/resources/nais/DeploymentsQuery.graphql"),
        file("${project.projectDir}/src/main/resources/nais/RepoVulnerabilityQuery.graphql")
    )
    serializer.set(GraphQLSerializer.KOTLINX)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
        }
    }

    withType<Wrapper> {
        gradleVersion = "9.0.0"
    }
}
