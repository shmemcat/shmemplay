plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.serialization.json)
}

sourceSets {
    test {
        resources.srcDir("../contract-fixtures")
    }
}

tasks.test {
    useJUnit()
}
