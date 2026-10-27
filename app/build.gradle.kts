plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.ironquest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ironquest.app"
        minSdk = 26 // java.time without desugaring
        targetSdk = 35
        // The release workflow derives both from the git tag (v1.2.3); local builds keep these defaults.
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Shipped languages. Libraries bring strings for ~85 locales; keep only ours. Add a locale here
        // when its translation lands. The pseudo-locales (en-XA, ar-XB) are added for debug builds only,
        // see androidComponents below: listing them here would also pull the whole "ar" library strings into release.
        resourceConfigurations += listOf("en")
    }

    androidResources {
        generateLocaleConfig = true
    }

    // The upload key lives only in GitHub Actions secrets and the owner's password manager, never in
    // the repo. Without IRONQUEST_KEYSTORE set (any local build) the release APK is simply unsigned.
    val releaseKeystore = providers.environmentVariable("IRONQUEST_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("IRONQUEST_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("IRONQUEST_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("IRONQUEST_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release code (R8, not debuggable) signed with the debug key so it can be installed
        // with adb, and profileable from the shell. Use this for all performance checks: debug
        // builds of Compose are several times slower and say nothing about real smoothness.
        create("profiling") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "DebugProbesKt.bin", // coroutines debug agent, never attached in the app
                "kotlin-tooling-metadata.json",
                "kotlin/**", // .kotlin_builtins: only kotlin-reflect reads them
                "META-INF/*.version",
            )
        }
    }

    sourceSets {
        // MigrationTestHelper reads the exported schema JSON from androidTest assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

baselineProfile {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

ksp {
    // Exported schemas let you write proper Room migration tests later.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        // isPseudoLocalesEnabled generates en-XA / ar-XB, but resourceConfigurations = ["en"] would filter them out again.
        variant.androidResources.aaptAdditionalParameters.addAll("-c", "en-rXA,ar-rXB")
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
