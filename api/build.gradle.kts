plugins {
    id("java-library")
    id("maven-publish")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.adventureApi)
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(16)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = 16
}

publishing {
    repositories {
        maven {
            url = uri(providers.gradleProperty("myMavenRepoWriteUrl").getOrElse(""))
            credentials {
                username = providers.gradleProperty("myMavenRepoWriteUsername").orNull
                password = providers.gradleProperty("myMavenRepoWritePassword").orNull
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group.toString()
            artifactId = "EventControl-api"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}

// Nothing published here may name a type the plugin relocates into its shadow
// package: a relocated type in a published signature is unusable to consumers.
val forbiddenApiPackages = listOf(
    "me.wyne.wutils",
    "me.lucko",
    "com.google.inject",
    "com.google.common",
    "dev.jorel",
    "dev.vankka",
    "xyz.xenondevs",
    "me.clip"
)

val checkApiPurity by tasks.registering {
    description = "Fails if the published API references a relocated or plugin-provided type."
    val sources = fileTree("src/main/java") { include("**/*.java") }
    inputs.files(sources)
    outputs.upToDateWhen { true }
    doLast {
        val offences = sources.flatMap { file ->
            file.readLines().withIndex().mapNotNull { (index, line) ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) null
                else forbiddenApiPackages.firstOrNull { trimmed.startsWith("import $it.") }
                    ?.let { "${file.relativeTo(projectDir)}:${index + 1} imports $it" }
            }
        }
        if (offences.isNotEmpty())
            throw GradleException("Forbidden imports in the published API:\n" + offences.joinToString("\n") { "  $it" })
    }
}

tasks.named("check") { dependsOn(checkApiPurity) }
