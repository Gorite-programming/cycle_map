plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":routing-core"))
    implementation("org.openstreetmap.osmosis:osmosis-pbf:0.48.3")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

application {
    mainClass.set("com.gorite.cyclemap.importer.OsmImporterKt")
}
