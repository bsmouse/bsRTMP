import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.bsrtmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.bsrtmp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "02.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 1. RTMP 송출용 라이브러리 (PedroSG94)
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.7")

    // 2. RTMP 재생용 라이브러리 (ExoPlayer + RTMP Extension)
    val media3_version = "1.9.0" // 최신 안정 버전
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-datasource-rtmp:$media3_version")
    implementation("androidx.media3:media3-common:$media3_version")
}

androidComponents {
    onVariants { variant ->
        val date = SimpleDateFormat("yyyyMMdd").format(Date())
        val newName = "DCCL_${variant.name}_$date.apk"

        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = newName
            }
        }
    }
}
