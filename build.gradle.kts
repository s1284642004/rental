plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://developer.huawei.com/repo/")
        }
    }
    dependencies {
        val agcpVersion = "1.9.5.302"
        classpath("com.huawei.agconnect:agcp:$agcpVersion")
    }
}