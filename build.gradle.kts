import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    `java-library`
    `maven-publish`
}

group = "ai.desertant"
version = "0.3.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

// Java 8 bytecode for the widest Android compatibility (D8 dexes it for old runtimes).
// The library itself uses only long-available APIs; the effective floor is Android 5.0
// (API 21), set by kotlinx-coroutines and kotlinx-serialization.
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.test {
    useJUnitPlatform()
}

// Published via JitPack, which runs `publishToMavenLocal` against a git tag.
// Consumers resolve it as `com.github.Desert-Ant-Labs:emo-kotlin:<tag>`.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "emo"

            pom {
                name.set("Emo")
                description.set("On-device emoji suggestions from text — pure Kotlin, no native or network dependencies.")
                url.set("https://github.com/Desert-Ant-Labs/emo-kotlin")
                licenses {
                    license {
                        name.set("Desert Ant Labs Source-Available License v1.0")
                        url.set("https://github.com/Desert-Ant-Labs/emo-kotlin/blob/main/LICENSE.md")
                    }
                }
                scm {
                    url.set("https://github.com/Desert-Ant-Labs/emo-kotlin")
                    connection.set("scm:git:https://github.com/Desert-Ant-Labs/emo-kotlin.git")
                }
            }
        }
    }
}
