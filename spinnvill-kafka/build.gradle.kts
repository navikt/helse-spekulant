private val rapidsAndRiversVersion = "2023101613431697456627.0cdd93eb696f"

group = "no.nav.helse"

dependencies {
    implementation(project(":spinnvill-felles"))

    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

}
