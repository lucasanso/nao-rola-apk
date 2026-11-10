import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: detection, classification, blocking policy, session machine and stats math.
// It must never depend on Android so that every rule is testable on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}
