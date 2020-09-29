import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    idea
    java
    kotlin("jvm") version "1.3.21"
}

group = "com.javadeobfuscator.relvl"
version = "1.1.0-SNAPSHOT"
description = "Java deobfuscator"

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        // java-deobfuscator-repository
        url = uri("https://repo.samczsun.com/repository/java-deobfuscator")
    }
}

if (file(".build.local.gradle.kts").exists()) {
    println("Using .build.local.gradle.kts")
    apply(from = ".build.local.gradle.kts")
}

val asmVersion = "8.0.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
//    implementation(kotlin("reflect"))

    implementation("commons-cli:commons-cli:1.3.1")
    implementation("commons-io:commons-io:2.5")
    implementation("com.google.guava:guava:19.0")
    implementation("org.jooq:jool:0.9.9")
    implementation("com.google.code.gson:gson:2.6.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.9.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.1")
    implementation("org.slf4j:slf4j-api:1.8.0-alpha2")
    implementation("org.slf4j:slf4j-simple:1.8.0-alpha2")

    implementation("org.ow2.asm:asm:${asmVersion}")
    implementation("org.ow2.asm:asm-commons:${asmVersion}")
    implementation("org.ow2.asm:asm-util:${asmVersion}")
    implementation("org.ow2.asm:asm-tree:${asmVersion}")
    implementation("org.ow2.asm:asm-analysis:${asmVersion}")

    implementation("com.javadeobfuscator:javavm:3.0.0")

    testImplementation("junit:junit:4.12")

    compile(files("${System.getProperty("java.home")}/../lib/tools.jar"))
}


configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

val versionString = project.version.toString().replace("-SNAPSHOT", "")

tasks {
    this.processResources {
        filesMatching(mutableListOf("main.properties")) {
            filter(
                    ReplaceTokens::class,
                    "tokens" to mapOf(
                            "VERSION" to versionString
                    ),
                    "beginToken" to "$" + "{",
                    "endToken" to "}"
            )
        }
    }

    this.assemble {
        dependsOn(processResources)
    }

    this.clean {
        delete("./deobfuscator.jar")
        delete("./deobfuscator-full.jar")
    }

    this.compileJava {
        options.encoding = "UTF-8"
        options.isFork = true
        options.forkOptions.executable = "javac"
        options.compilerArgs.add("-XDignore.symbol.file=true")
    }

    this.compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    this.jar {
        archiveBaseName.set("${project.name}-full")
        manifest {
            attributes["Implementation-Title"] = "Gradle Fat Jar File"
            attributes["Implementation-Version"] = versionString
            attributes["Main-Class"] = "com.javadeobfuscator.deobfuscator.DeobfuscatorMain"
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        doLast {
            copy {
                from(jar)
                into("${projectDir}/")
                rename { fileName -> fileName.replace("-${project.version}", "") }
            }
        }
    }
}