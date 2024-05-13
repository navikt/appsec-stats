import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "2.3.11"
val logbackVersion = "1.5.6"
val logstashEncoderVersion = "7.4"
val bigQueryClientVersion = "2.39.1"

val junitJupiterVersion = "5.10.2"

val mainClassName = "no.nav.security.MainKt"

plugins {
   kotlin("jvm") version "1.9.24"
   kotlin("plugin.serialization") version "1.9.24"
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

   implementation("ch.qos.logback:logback-classic:$logbackVersion")
   implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

   implementation("com.google.cloud:google-cloud-bigquery:$bigQueryClientVersion")

   testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
   testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
   testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
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
