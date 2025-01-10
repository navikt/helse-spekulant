private val rapidsAndRiversVersion = "2025010715371736260653.d465d681c420"
private val rapidsAndRiversTestVersion = "2025.01.07-15.40-2be88c00"

group = "no.nav.helse"

dependencies {
    implementation(project(":spinnvill-db"))
    implementation(project(":spinnvill-avviksvurdering"))
    implementation(project(":spinnvill-felles"))

    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    testImplementation(testFixtures(project(":spinnvill-db")))
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$rapidsAndRiversTestVersion")
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")
        manifest {
            attributes["Main-Class"] = "no.nav.helse.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }
        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists()) it.copyTo(file)
            }
        }
    }
}
