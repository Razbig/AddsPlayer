import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 1. Зчитуємо local.properties на етапі конфігурації Gradle
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}


android {
    namespace = "com.example.addsplayer"
    compileSdk = 36 // Спростив запис для стабільності

    defaultConfig {
        applicationId = "com.example.addsplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val serverUrl = localProperties.getProperty("server.url") ?: ""
        val apiLogin = localProperties.getProperty("api.login") ?: ""
        val apiPassword = localProperties.getProperty("api.password") ?: ""
        val posId = localProperties.getProperty("pos.id") ?: ""
        // 2. Додаємо поле, яке буде доступне в коді
        // Параметри: Тип, Назва, Значення (значення має бути в лапках як рядок)
        buildConfigField("String", "DEFAULT_SERVER", "\"$serverUrl\"")
        buildConfigField("String", "DEFAULT_LOGIN", "\"$apiLogin\"")
        buildConfigField("String", "DEFAULT_PASSWORD", "\"$apiPassword\"")
        buildConfigField("String", "DEFAULT_POS_ID", "\"$posId\"")
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

    // 3. ВАЖЛИВО: У нових версіях AGP BuildConfig вимкнено за замовчуванням
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.material)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}