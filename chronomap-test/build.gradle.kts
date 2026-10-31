dependencies {
    api(project(":chronomap"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.castroaj.chronomap.test")
    }
}
