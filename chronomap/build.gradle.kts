tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.castroaj.chronomap")
    }
}

// Core never depends on a framework (ADR 0007).
val checkNoSpring = tasks.register("checkNoSpring") {
    group = "verification"
    description = "Fails if core sources reference org.springframework.* or Spring is on its classpath."
    val sources = fileTree("src/main/java") { include("**/*.java") }
    val runtimeClasspath = configurations.named("runtimeClasspath")
    inputs.files(sources)
    doLast {
        val offenders =
            sources.files.filter { f ->
                f.useLines { lines ->
                    lines.any { it.contains("org.springframework.") }
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Core must not reference org.springframework.*: " + offenders.joinToString { it.name })
        }
        val spring =
            runtimeClasspath.get().incoming.resolutionResult.allComponents
                .map { it.moduleVersion }
                .filter { it != null && it.group.startsWith("org.springframework") }
        if (spring.isNotEmpty()) {
            throw GradleException("Core must not depend on Spring: $spring")
        }
    }
}

tasks.check { dependsOn(checkNoSpring) }
