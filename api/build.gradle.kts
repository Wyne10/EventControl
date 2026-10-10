plugins {
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.35.0"
}

dependencies {
    compileOnly(libs.paperApi)
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.named("publish") {
    dependsOn("publishToMavenLocal")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(findProperty("centralGroup").toString(), "eventcontrol-api", version.toString())

    pom {
        name.set("EventControl API")
        description.set("Consumer and extension API for the EventControl Bukkit/Paper plugin, which runs timed server events on queues and resolves every event before it starts.")
        inceptionYear.set("2026")
        url.set("https://github.com/Wyne10/EventControl")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("Wyne10")
                name.set("Wyne")
                email.set("izmodenov1997@gmail.com")
                organization.set("BigTeam")
                organizationUrl.set("https://github.com/NeverMined-Entertainment")
            }
        }
        scm {
            url.set("https://github.com/Wyne10/EventControl")
            connection.set("scm:git:git://github.com/Wyne10/EventControl.git")
            developerConnection.set("scm:git:ssh://git@github.com/Wyne10/EventControl.git")
        }
    }
}
