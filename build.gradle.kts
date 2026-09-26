buildscript {
    dependencies {
        // AGP 9 built-in Kotlin defaults can lag dependency metadata.
        // FairSwap pins KGP explicitly so Supabase Kotlin 3.8.0 (Kotlin 2.4 metadata)
        // is compiled with a compatible Kotlin compiler.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
