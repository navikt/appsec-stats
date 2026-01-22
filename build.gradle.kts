import com.expediagroup.graphql.plugin.gradle.config.GraphQLParserOptions
import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.expedia.graphql)
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
    implementation(libs.kotlin.stdlib)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.datetime)

    implementation(libs.expedia.graphql.ktor.client)

    implementation(libs.kafka.clients)

    implementation(libs.bundles.github.auth)

    implementation(libs.bundles.logging)

    implementation(libs.google.cloud.bigquery)

    implementation(platform(libs.junit.bom))
    implementation(libs.bundles.testing)
    testImplementation(libs.ktor.client.mock)
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
    parserOptions.assign(GraphQLParserOptions(maxTokens = 100000, maxCharacters = 8048576))
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
        gradleVersion = "9.3.0"
    }
}
