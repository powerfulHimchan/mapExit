plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.powerfulhimchan.mapexit"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.powerfulhimchan.mapexit"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.10.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    val localDebugKey = rootProject.file("dev-signing/mapexit-debug.keystore")
    if (localDebugKey.exists()) signingConfigs.getByName("debug") {
        storeFile = localDebugKey
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
