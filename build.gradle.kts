plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

repositories { mavenCentral() }

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// Bytecode for Java 17 (and Android), built and tested on whichever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        allWarningsAsErrors = true
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

sourceSets {
    // Runnable examples: ./gradlew runExample -Pexample=status
    create("examples") {
        compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

tasks.register<JavaExec>("runExample") {
    description = "Runs examples/<example>.kt (-Pexample=status|chat|events)."
    val name = (project.findProperty("example") as String?) ?: "status"
    mainClass = "examples." + name.replaceFirstChar(Char::uppercase) + "Kt"
    classpath = sourceSets["examples"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    // The live test runs only with ZEROCLAW_URL and ZEROCLAW_TOKEN set.
    environment("ZEROCLAW_URL", System.getenv("ZEROCLAW_URL") ?: "")
    environment("ZEROCLAW_TOKEN", System.getenv("ZEROCLAW_TOKEN") ?: "")
}

mavenPublishing {
    publishToMavenCentral()
    // Releases are signed in CI (ORG_GRADLE_PROJECT_signingInMemoryKey);
    // local and JitPack builds publish unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates(group.toString(), "zeroclaw-api-client", version.toString())
    pom {
        name = "zeroclaw-api-client"
        description = "Kotlin client for the ZeroClaw gateway API"
        url = "https://github.com/RustLangLatam/zeroclaw-api-client-kotlin"
        inceptionYear = "2026"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "RustLangLatam"
                name = "RustLangLatam"
                email = "rust.lang.latam@gmail.com"
            }
        }
        scm {
            url = "https://github.com/RustLangLatam/zeroclaw-api-client-kotlin"
            connection = "scm:git:https://github.com/RustLangLatam/zeroclaw-api-client-kotlin.git"
        }
    }
}
