plugins {
    alias(libs.plugins.spotless) apply false
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val googleJavaFormatVersion = catalog.findVersion("googleJavaFormat").get().requiredVersion

allprojects {
    group = "io.github.castroaj"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    dependencies {
        "testImplementation"(platform(catalog.findLibrary("junit-bom").get()))
        "testImplementation"(catalog.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(catalog.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    // Report only: coverage is never a gate (issue #19).
    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required = true
            html.required = true
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(googleJavaFormatVersion)
        }
    }
}
