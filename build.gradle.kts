import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.2.3")
        testFramework(TestFrameworkType.Platform)
        testBundledPlugins("com.intellij.modules.ultimate")

        // Necesario para la tarea verifyPlugin
        pluginVerifier()
    }
}

// Fuera de "dependencies": configuración del plugin
intellijPlatform {
    signing {
        certificateChainFile = file("C:/Users/leona/draken-keys/chain.crt")
        privateKeyFile = file("C:/Users/leona/draken-keys/private_encrypted.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}