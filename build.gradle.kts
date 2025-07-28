plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.breachinthecontainment.launcher_client.Main")
}

javafx {
    version = "20"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation("org.openjfx:javafx-controls:20")
    // Add the JSON library dependency
    implementation("org.json:json:20231013") // You can check Maven Central for the latest version
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("breach_launcher")
    archiveClassifier.set("") // no "-all" suffix
    archiveVersion.set("")

    manifest {
        attributes["Main-Class"] = "org.breachinthecontainment.launcher_client.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.named("startScripts") {
    dependsOn(tasks.shadowJar)
}
