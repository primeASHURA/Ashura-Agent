import java.net.URI
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "ai.ashura.app"
    compileSdk = 36
    defaultConfig { applicationId = "ai.ashura.app"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        for (field in listOf("PRIVACY_POLICY_URL", "AI_REPORT_URL")) {
            val url = providers.gradleProperty(field).orElse("").get()
            require(url.isEmpty() || (URI(url).scheme == "https" && !URI(url).host.isNullOrEmpty() && url.none { it == '"' || it == '\\' }))
            buildConfigField("String", field, "\"" + url + "\"")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        resources.excludes += "META-INF/versions/9/module-info.class"
        jniLibs.keepDebugSymbols += "**/*.so"
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.smali:smali:2.5.2")
    implementation("org.smali:baksmali:2.5.2")
    implementation("com.github.mwiede:jsch:0.2.20")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
