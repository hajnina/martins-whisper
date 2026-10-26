plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation(testFixtures(project))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
