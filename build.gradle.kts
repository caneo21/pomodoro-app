import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// --- Release version -------------------------------------------------------------------------
// CI passes -PreleaseVersion=1.2.3 (taken from the v1.2.3 tag). Local builds without it are
// "0.0.0-dev", which the in-app update check treats as "never newer", so dev builds stay quiet.
val releaseVersion: String? = providers.gradleProperty("releaseVersion").orNull
val releaseVersionRegex = Regex("""^(\d+)\.(\d{1,2})\.(\d{1,2})$""")

val appVersionName: String = releaseVersion ?: "0.0.0-dev"
val appVersionCode: Int = if (releaseVersion == null) {
    1
} else {
    val match = releaseVersionRegex.matchEntire(releaseVersion)
        ?: throw GradleException("releaseVersion must look like 1.2.3, got '$releaseVersion'")
    val (major, minor, patch) = match.destructured
    major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}
extra["appVersionName"] = appVersionName
extra["appVersionCode"] = appVersionCode

// --- Release signing (shared by :app and :wear) ----------------------------------------------
// The Wearable Data Layer only connects phone and watch apps signed with the same certificate,
// so both modules must sign with this one keystore. Local builds read keystore.properties; CI
// sets the ANDROID_KEYSTORE_* / ANDROID_KEY_* environment variables instead.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val releaseSigning: Map<String, String>? = when {
    keystorePropertiesFile.exists() -> {
        val props = Properties().apply { keystorePropertiesFile.inputStream().use { load(it) } }
        mapOf(
            "storeFile" to rootProject.file(props.getProperty("storeFile")).absolutePath,
            "storePassword" to props.getProperty("storePassword"),
            "keyAlias" to props.getProperty("keyAlias"),
            "keyPassword" to props.getProperty("keyPassword"),
        )
    }
    listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrEmpty() } -> mapOf(
        "storeFile" to file(System.getenv("ANDROID_KEYSTORE_PATH")).absolutePath,
        "storePassword" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
        "keyAlias" to System.getenv("ANDROID_KEY_ALIAS"),
        "keyPassword" to System.getenv("ANDROID_KEY_PASSWORD"),
    )
    else -> null
}
extra["releaseSigning"] = releaseSigning

// A release build without signing must fail rather than quietly produce an unsigned APK.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task ->
        listOf("assembleRelease", "bundleRelease", "packageRelease").any { task.path.endsWith(it) }
    }
    if (buildsRelease && releaseSigning == null) {
        throw GradleException(
            "Release signing is not configured: add keystore.properties or ANDROID_KEYSTORE_* env vars"
        )
    }
}

tasks.register("printReleaseVersion") {
    val name = appVersionName
    val code = appVersionCode
    doLast { println("versionName=$name versionCode=$code") }
}
