plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
val generatedBalance = layout.buildDirectory.dir("generated/balance")
val generateBalance by tasks.registering {
    inputs.file(rootProject.file("balance/weapons.json"))
    outputs.dir(generatedBalance)
    doLast {
        val literal = rootProject.file("balance/weapons.json").readText()
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val output = generatedBalance.get().file("com/powerfulhimchan/mapexit/core/BundledBalance.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("package com.powerfulhimchan.mapexit.core\ninternal const val BALANCE_JSON = \"$literal\"\n")
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generatedBalance) }
tasks.named("compileKotlin") { dependsOn(generateBalance) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
