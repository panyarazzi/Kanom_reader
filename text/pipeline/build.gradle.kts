plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.icu4j)
    implementation(project(":text:replacement"))
    testImplementation(libs.junit)
}
