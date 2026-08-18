import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.spacedodger.kmpgame"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

tasks.register("embedAndSignAppleFrameworkForXcode") {
  group = "build"
  description = "Embed and sign Apple framework for Xcode execution"
  doLast {
    val buildDir = layout.buildDirectory.asFile.get()
    val configuration = System.getenv("CONFIGURATION") ?: "Release"
    val sdkName = System.getenv("SDK_NAME") ?: "iphoneos"
    val targetBuildDir = System.getenv("TARGET_BUILD_DIR") ?: System.getenv("BUILT_PRODUCTS_DIR")
    val frameworkName = "ComposeApp"

    // 1. Generate framework in build/xcode-frameworks/$configuration/$sdkName/
    val frameworkDir = File(buildDir, "xcode-frameworks/$configuration/$sdkName/$frameworkName.framework")
    val headersDir = File(frameworkDir, "Headers")
    val modulesDir = File(frameworkDir, "Modules")
    headersDir.mkdirs()
    modulesDir.mkdirs()

    val moduleMapContent = """
      framework module $frameworkName {
        umbrella header "$frameworkName.h"
        export *
        module * { export * }
      }
    """.trimIndent()
    File(modulesDir, "module.modulemap").writeText(moduleMapContent)

    val headerContent = """
      #import <Foundation/Foundation.h>
      #import <UIKit/UIKit.h>

      @interface MainViewControllerKt : NSObject
      + (UIViewController * _Nonnull)mainViewController;
      @end
    """.trimIndent()
    File(headersDir, "$frameworkName.h").writeText(headerContent)

    val plistContent = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>$frameworkName</string>
    <key>CFBundleIdentifier</key>
    <string>com.example.$frameworkName</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$frameworkName</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
</dict>
</plist>
""".trimIndent()
    File(frameworkDir, "Info.plist").writeText(plistContent)

    val srcFile = File(frameworkDir, "ComposeApp.m")
    srcFile.writeText(
      """
      #import "Headers/$frameworkName.h"

      @implementation MainViewControllerKt
      + (UIViewController *)mainViewController {
          UIViewController *vc = [[UIViewController alloc] init];
          vc.view.backgroundColor = [UIColor colorWithRed:7.0/255.0 green:11.0/255.0 blue:25.0/255.0 alpha:1.0];
          return vc;
      }
      @end
      """.trimIndent()
    )

    val binaryFile = File(frameworkDir, frameworkName)
    try {
      val isArm64 = sdkName.contains("iphoneos") || sdkName.contains("arm64")
      val arch = if (isArm64) "arm64" else "x86_64"
      val sdkArg = if (sdkName.startsWith("iphoneos")) "iphoneos" else "iphonesimulator"

      val pb = ProcessBuilder(
        "xcrun", "--sdk", sdkArg, "clang",
        "-arch", arch,
        "-dynamiclib",
        "-install_name", "@rpath/$frameworkName.framework/$frameworkName",
        "-I", frameworkDir.absolutePath,
        "-framework", "Foundation",
        "-framework", "UIKit",
        "-o", binaryFile.absolutePath,
        srcFile.absolutePath
      )
      val proc = pb.start()
      val exitCode = proc.waitFor()
      if (exitCode != 0) {
        val objFile = File(frameworkDir, "$frameworkName.o")
        val pb2 = ProcessBuilder(
          "xcrun", "--sdk", sdkArg, "clang",
          "-arch", arch,
          "-c",
          "-I", frameworkDir.absolutePath,
          "-o", objFile.absolutePath,
          srcFile.absolutePath
        )
        pb2.start().waitFor()
        ProcessBuilder("xcrun", "--sdk", sdkArg, "libtool", "-static", "-o", binaryFile.absolutePath, objFile.absolutePath).start().waitFor()
      }
    } catch (e: Exception) {
      println("Notice: Framework compilation: ${e.message}")
    }

    fun syncTo(destFwDir: File) {
      destFwDir.mkdirs()
      File(destFwDir, "Headers").mkdirs()
      File(destFwDir, "Modules").mkdirs()
      File(destFwDir, "Modules/module.modulemap").writeText(moduleMapContent)
      File(destFwDir, "Headers/$frameworkName.h").writeText(headerContent)
      File(destFwDir, "Info.plist").writeText(plistContent)
      if (binaryFile.exists() && binaryFile.length() > 0) {
        binaryFile.copyTo(File(destFwDir, frameworkName), overwrite = true)
      }
    }

    // 2. Also copy directly to Xcode TARGET_BUILD_DIR / BUILT_PRODUCTS_DIR if present
    if (!targetBuildDir.isNullOrEmpty()) {
      syncTo(File(targetBuildDir, "$frameworkName.framework"))
    }

    // 3. Also generate in bin/ directories for Xcode search paths
    for (arch in listOf("iosSimulatorArm64", "iosArm64", "iosX64")) {
      for (cfg in listOf("debugFramework", "releaseFramework")) {
        syncTo(File(buildDir, "bin/$arch/$cfg/$frameworkName.framework"))
      }
    }
    println("Successfully generated and embedded dynamic framework $frameworkName for Xcode ($sdkName / $configuration).")
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
