plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    // The shadow plugin has been commented out as jpackage is the recommended method for JavaFX distribution.
    // id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.breachinthecontainment.launcher_client.Main")
    // You can define a name for the application that will be used by jpackage
    applicationName = "Breach In The Containment - Launcher"
}

javafx {
    version = "20"
    // Ensure you include all JavaFX modules your application uses.
    // javafx.controls is already there, adding graphics and base as a precaution.
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.base")

    // Configuration for jlink (creates a custom runtime image)
    jlink {
        // Options to optimize the size of the runtime image
        options = listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
        // You can add other options if necessary, e.g., --bind-services
    }

    // Configuration for jpackage (creates the native installer)
    jpackage {
        // Application name (can be the same as applicationName or different)
        appName = "BreachLauncher"
        // Installer title
        appVersion = "1.0" // Set an appropriate version
        vendor = "Breach In The Containment" // Your organization's name
        // Path to the application icon (must be a .icns file for macOS, .ico for Windows)
        // For now, we're relying on the default icon or the one you configured in the JavaFX code.
        // If you have an .icns file, you can specify it here:
        // mac {
        //     icon = "src/main/resources/icon.icns" // Ensure this path is correct
        // }
        // linux {
        //     icon = "src/main/resources/icon.png" // For Linux, often a PNG
        // }
        // win {
        //     icon = "src/main/resources/icon.ico" // For Windows, often an ICO
        // }
    }
}

dependencies {
    implementation("org.openjfx:javafx-controls:20")
    implementation("org.openjfx:javafx-graphics:20")
    implementation("org.openjfx:javafx-base:20")
    implementation("org.json:json:20231013")
}

// The shadowJar and build tasks are no longer necessary in this context for distribution
// because jpackage handles the creation of the executable.
// tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
//     archiveBaseName.set("breach_launcher")
//     archiveClassifier.set("")
//     archiveVersion.set("")
//     manifest {
//         attributes["Main-Class"] = "org.breachinthecontainment.launcher_client.Main"
//     }
// }
// tasks.build {
//     // dependsOn(tasks.shadowJar)
// }

// To create the native installer, you will run:
// ./gradlew jpackage
// The generated files will be found in the 'build/jpackage' folder.
