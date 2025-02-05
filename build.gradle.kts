private val logbackVersion = "1.5.7"
private val logstashVersion = "8.0"
private val mockkVersion = "1.13.12"
private val junitJupiterVersion = "5.11.3"

plugins {
    kotlin("jvm") version "2.1.10"
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    repositories {
        val githubPassword: String? by project
        mavenCentral()
        /* ihht. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
            så plasseres github-maven-repo (med autentisering) før nav-mirror slik at github actions kan anvende førstnevnte.
            Det er fordi nav-mirroret kjører i Google Cloud og da ville man ellers fått unødvendige utgifter til datatrafikk mellom Google Cloud og GitHub
         */
        maven {
            url = uri("https://maven.pkg.github.com/navikt/maven-release")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    dependencies {
        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion") {
            exclude("com.fasterxml.jackson.core")
            exclude("com.fasterxml.jackson.dataformat")
        }

        testImplementation(kotlin("test"))
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    }

    tasks {
        kotlin {
            jvmToolchain(21)
        }

        test {
            useJUnitPlatform()
        }
    }
}

tasks {
    jar {
        enabled = false
    }
}