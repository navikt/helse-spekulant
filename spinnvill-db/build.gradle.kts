private val postgresqlVersion = "42.7.4"
private val hikariCPVersion = "5.1.0"
private val flywayCoreVersion = "10.17.1"
private val kotliqueryVersion = "1.9.0"
private val testcontainersPostgresqlVersion = "1.19.6"
private val micrometerVersion = "1.14.3"
private val exposedVersion = "0.53.0"

group = "no.nav.helse"

plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(project(":spinnvill-felles"))

    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    testImplementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")

    testFixturesImplementation("org.postgresql:postgresql:$postgresqlVersion")
    testFixturesImplementation("com.zaxxer:HikariCP:$hikariCPVersion")
    testFixturesImplementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    testFixturesImplementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testFixturesImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}

// testFixtures sammen med Kotlin er ikke 100% modent enda, som gjør at testFixtures ikke ut av boksen
// deler visibility med resten av modulen den ligger i.
// Denne workarounden løser dette problemet frem til JetBrains retter feilen.
// https://youtrack.jetbrains.com/issue/KT-34901/Gradle-testFixtures-dont-have-friendPaths-set#focus=Comments-27-3810442.0-0
kotlin.target.compilations.getByName("testFixtures") {
    associateWith(target.compilations.getByName("main"))
}

tasks {
    compileTestKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }
}