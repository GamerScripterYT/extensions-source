plugins {
    id("com.android.application")
    id("kotlin-android")
    
}

ext {
    extName = "Chikari"
    pkgNameSuffix = "en.chikari"
    extClass = ".Chikari"
    extVersionCode = 1
    isNsfw = false
}

apply(from = "$rootDir/common.gradle")

dependencies {
    
}
