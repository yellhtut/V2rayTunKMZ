plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The Xray core is a prebuilt binary, not a source dependency (see app/libs/README.md), so
// whether it is present decides what gets compiled. The binding code names classes that exist
// only inside the .aar, which is why it cannot live in src/main — a checkout without the
// binary would stop compiling. Instead exactly one of two small source dirs is added below.
//
// A working engine needs BOTH halves: the binary and the code that binds it. Requiring both
// means dropping the .aar in before the binding is written degrades to a coreless build
// rather than failing to compile on an unresolved reference.
val coreAar: File = file("libs/libv2ray.aar")
val coreBinding: File = file("src/withCore/java")
val hasCore: Boolean = coreAar.exists() && coreBinding.isDirectory

logger.lifecycle(
    when {
        hasCore -> "Tunnel core: linking ${coreAar.name}"
        coreAar.exists() ->
            "Tunnel core: ${coreAar.name} found but src/withCore/java is missing — " +
                "building without a tunnel engine"
        else ->
            "Tunnel core: none — building without a tunnel engine (see app/libs/README.md)"
    },
)

android {
    namespace = "com.kmz.v2raytun"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kmz.v2raytun"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The tun2socks native library ships for these ABIs only.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // Lets code tell an engine-less build from a real one without starting a tunnel
        // and catching the failure to find out.
        buildConfigField("boolean", "HAS_CORE", hasCore.toString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // tun2socks is executed from the app's lib dir; it must not be compressed.
            useLegacyPackaging = false
        }
    }

    // Exactly one of these is compiled. 'withCore' binds to classes that live inside the
    // .aar; 'noCore' is a stub that leaves MissingTunnelCore in place. Swapping a whole
    // source dir keeps every reference to the binary out of a coreless checkout, so the
    // repo compiles from a clean clone with no manual steps.
    sourceSets {
        getByName("main") {
            java.srcDir(if (hasCore) "src/withCore/java" else "src/noCore/java")
        }
    }

    // Keep the geo asset files uncompressed so the core can mmap them.
    androidResources {
        noCompress += listOf("dat")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Top level, not inside android {} — that nesting is not a valid AGP extension point.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    // Xray core: a prebuilt binary, deliberately not committed (see app/libs/README.md).
    // Absent is a supported state, not a broken one — the build says so and carries on.
    if (hasCore) implementation(files(coreAar))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
