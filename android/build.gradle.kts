buildscript {
    dependencies {
        // Compose compiler plugin needs KGP 2.3.x; AGP built-in Kotlin defaults to 2.2.10+.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

