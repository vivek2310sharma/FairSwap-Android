import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun configValue(name: String): String =
    providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

fun stringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.fairswap.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fairswap.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            manifestPlaceholders["authCallbackScheme"] = "fairswap-staging"
            manifestPlaceholders["authCallbackHost"] = "auth"
            buildConfigField("String", "AUTH_CALLBACK_SCHEME", stringLiteral("fairswap-staging"))
            buildConfigField("String", "AUTH_CALLBACK_HOST", stringLiteral("auth"))
            buildConfigField("String", "FAIRSWAP_WEB_BASE_URL", stringLiteral(configValue("FAIRSWAP_STAGING_WEB_BASE_URL")))
            buildConfigField("String", "ENVIRONMENT_NAME", stringLiteral("staging"))
            buildConfigField(
                "String",
                "SUPABASE_URL",
                stringLiteral(
                    configValue("SUPABASE_STAGING_URL").ifBlank {
                        "https://wpppxlnbzqynvhpeuavd.supabase.co"
                    }
                ),
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                stringLiteral(configValue("SUPABASE_STAGING_PUBLISHABLE_KEY")),
            )
        }

        create("production") {
            dimension = "environment"
            manifestPlaceholders["authCallbackScheme"] = "fairswap"
            manifestPlaceholders["authCallbackHost"] = "auth"
            buildConfigField("String", "AUTH_CALLBACK_SCHEME", stringLiteral("fairswap"))
            buildConfigField("String", "AUTH_CALLBACK_HOST", stringLiteral("auth"))
            buildConfigField("String", "FAIRSWAP_WEB_BASE_URL", stringLiteral(configValue("FAIRSWAP_PRODUCTION_WEB_BASE_URL")))
            buildConfigField("String", "ENVIRONMENT_NAME", stringLiteral("production"))
            buildConfigField("String", "SUPABASE_URL", stringLiteral(configValue("SUPABASE_PRODUCTION_URL")))
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                stringLiteral(configValue("SUPABASE_PRODUCTION_PUBLISHABLE_KEY")),
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    implementation(project(":core:account"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:account"))
    implementation(project(":core:discovery"))
    implementation(project(":feature:discovery"))
    implementation(project(":feature:listingdetail"))
    implementation(project(":feature:sellerprofile"))
    implementation(project(":core:favourites"))
    implementation(project(":feature:favourites"))
    implementation(project(":core:sellerlistings"))
    implementation(project(":feature:sellerlistings"))
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
