import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	maven {
		name = "Ladysnake Mods"
		url = uri("https://maven.ladysnake.org/releases")
	}
	maven {
		name = "Modrinth Maven"
		url = uri("https://api.modrinth.com/maven")
	}
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("boundbyfate-core") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings("net.fabricmc:yarn:${providers.gradleProperty("yarn_mappings").get()}:v2")
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// Satin API for custom post-process shaders with mod namespace support
	modImplementation("org.ladysnake:satin:1.14.0")

	// Kool — 3D engine used for NPC model rendering and animations
	// Custom build (0mods) stripped of unnecessary dependencies for Minecraft mod use
	implementation(files("libs/kool-core-desktop-0.18.0-0mods-SNAPSHOT.jar"))

	// playerAnimator — player animation library for custom ability/interaction animations
	// Packed into the jar so players don't need to install it separately
	include(modImplementation("dev.kosmx.player-anim:player-animation-lib-fabric:1.0.2-rc1+1.20"))

	// ETF — Entity Texture Features (required by EMF)
	modImplementation("maven.modrinth:entitytexturefeatures:7.1-fabric-1.20.1")

	// EMF — Entity Model Features (reads JEM/JPM files for player animations)
	modImplementation("maven.modrinth:entity-model-features:3.2.2-fabric-1.20.1")
}

tasks.processResources {
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 17
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
	inputs.property("projectName", project.name)

	from("LICENSE") {
		rename { "${it}_${project.name}" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
