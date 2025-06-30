plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
}

var buildFull = properties["BuildFull"].toString() == "true"
var libraries = listOf<String>()
libraries += "org.postgresql:postgresql:42.7.2"
libraries += "mysql:mysql-connector-java:8.0.33"
libraries += "net.kyori:adventure-platform-bukkit:4.3.3"
libraries += "com.zaxxer:HikariCP:6.2.1"
libraries += "com.alibaba:fastjson:2.0.31"
libraries += "org.seleniumhq.selenium:selenium-java:4.9.0"

group = "cn.lunadeer"
version = "1.0-alpha.4"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://oss.sonatype.org/content/groups/public")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://repo.extendedclip.com/releases/")
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
        compileOnly("me.clip:placeholderapi:2.11.6")
        if (!buildFull) {
            libraries.forEach {
                compileOnly(it)
            }
        } else {
            libraries.forEach {
                implementation(it)
            }
        }
    }

    tasks.processResources {
        outputs.upToDateWhen { false }
        // replace @version@ in plugin.yml with project version
        filesMatching("**/plugin.yml") {
            filter {
                it.replace("@version@", rootProject.version.toString())
            }
            if (!buildFull) {
                var libs = "libraries: ["
                libraries.forEach {
                    libs += "$it,"
                }
                filter {
                    it.replace("libraries: [ ]", libs.substring(0, libs.length - 1) + "]")
                }
            }
        }
    }

    tasks.shadowJar {
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        dependsOn(tasks.withType<ProcessResources>())
        // add -lite to the end of the file name if BuildLite is true or -full if BuildLite is false
        archiveFileName.set("${project.name}-${project.version}${if (buildFull) "-full" else "-lite"}.jar")
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.register("Clean&Build") { // <<<< RUN THIS TASK TO BUILD PLUGIN
    dependsOn(tasks.clean)
    dependsOn(tasks.shadowJar)
}
