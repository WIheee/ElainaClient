plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.kotlin.jvm) apply false            // 统一走 catalog
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow) apply false
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds both Android APK and Desktop JAR"
    dependsOn(":app:assembleRelease", ":desktop:shadowJar")
}

tasks.register("buildDebugAll") {
    group = "build"
    description = "Builds both Android Debug APK and Desktop JAR"
    dependsOn(":app:assembleDebug", ":desktop:shadowJar")
}