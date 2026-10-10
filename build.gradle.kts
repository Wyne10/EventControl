import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import org.codehaus.plexus.util.Os

plugins {
    kotlin("jvm") version "2.4.20"
    alias(libs.plugins.shadow)
    alias(libs.plugins.runPaper)
    alias(libs.plugins.pluginYml)
}

kotlin {
    jvmToolchain(16)
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.placeholderApi)
    compileOnly(libs.commandApi)

    implementation(project(":api"))

    implementation(libs.guice)
    implementation(libs.enhancedLegacy)
    implementation(libs.adventureMini)
    implementation(libs.adventureBukkit)
    implementation(libs.adventurePlain)

    implementation(libs.wutilsConfig)
    implementation(libs.wutilsConfigurables)
    implementation(libs.wutilsI18nKotlin)
    implementation(libs.wutilsCommonKotlin)
    implementation(libs.wutilsJson)
}

tasks {
    val isDebug = findProperty("debug")?.toString()?.toBoolean() ?: false

    shadowJar {
        archiveBaseName.set(findProperty("name").toString())
        archiveClassifier.set("")
        minimize {
            exclude(project(":api"))
        }
        if (!isDebug) {
            relocate("com.google.inject", "me.wyne.eventcontrol.shadow.google.guice")
            relocate("com.google.common", "me.wyne.eventcontrol.shadow.google.common")
            relocate("net.kyori", "me.wyne.eventcontrol.shadow.net.kyori")
            relocate("dev.vankka", "me.wyne.eventcontrol.shadow.dev.vankka")
            relocate("me.wyne.wutils", "me.wyne.eventcontrol.shadow.wutils")
        }
    }

    runServer {
        val minecraftVersion: String = if (Os.isFamily(Os.FAMILY_WINDOWS) || isDebug) "1.19.4" else "1.16.5"
        val viaVersion = "5.12.0"
        val commandApiVersion = "9.4.2"
        downloadPlugins {
            github("PlaceholderAPI", "PlaceholderAPI", "2.12.3", "PlaceholderAPI-2.12.3.jar")
            github("dmulloy2", "ProtocolLib", "5.4.0", "ProtocolLib.jar")
            github("ViaVersion", "ViaVersion", viaVersion, "ViaVersion-$viaVersion.jar")
            github("ViaVersion", "ViaBackwards", viaVersion, "ViaBackwards-$viaVersion.jar")
            github("CommandAPI", "CommandAPI", commandApiVersion, "CommandAPI-$commandApiVersion.jar")
        }
        runDirectory(layout.projectDirectory.dir("run-$minecraftVersion").asFile)
        minecraftVersion(minecraftVersion)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-DPaper.IgnoreJavaVersion=true")
}

bukkit {
    name = findProperty("name").toString()
    version = getVersion().toString()
    website = findProperty("website").toString()
    author = findProperty("author").toString()
    main = "me.wyne.eventcontrol.EventControl"
    apiVersion = "1.16"
    softDepend = listOf("PlaceholderAPI", "CommandAPI")
    permissions {
        register("eventcontrol.*") {
            children = listOf(
                "eventcontrol.reload",
                "eventcontrol.queue.view",
                "eventcontrol.queue.control",
                "eventcontrol.poll.manage"
            )
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("eventcontrol.reload") {
            description = "Allows to reload plugin"
        }
        register("eventcontrol.queue.view") {
            description = "Allows to inspect event queues"
        }
        register("eventcontrol.queue.control") {
            description = "Allows to start, stop, skip and request events"
        }
        register("eventcontrol.poll.manage") {
            description = "Allows to open and close event polls"
        }
        register("eventcontrol.event") {
            description = "Allows to see upcoming events and vote in polls"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
    }
}
