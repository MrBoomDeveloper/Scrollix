package com.mrboomdev.scrollix.data

/**
 * Facts about this build that Settings → About shows.
 *
 * [version] is bumped together with the release tag: the release scripts
 * derive the artifact version from git, so the two have to stay in step.
 */
object AppInfo {
    const val version: String = "0.1.0"
    const val sourceUrl: String = "https://github.com/MrBoomDeveloper/Scrollix"
    const val dependenciesUrl: String = "$sourceUrl/blob/master/libs.versions.toml"

    /**
     * Core open-source components shown in the licenses dialog. Only the ones
     * with a known license are listed; the full pinned dependency list lives in
     * `libs.versions.toml`, which the dialog links to.
     */
    val thirdPartyComponents: List<ThirdPartyComponent> = listOf(
        ThirdPartyComponent("Kotlin and kotlinx libraries", "Apache-2.0"),
        ThirdPartyComponent("Compose Multiplatform", "Apache-2.0"),
        ThirdPartyComponent("Jetpack Compose and Material 3", "Apache-2.0"),
        ThirdPartyComponent("AndroidX Lifecycle, Room, SQLite and Navigation", "Apache-2.0"),
        ThirdPartyComponent("TDLib", "Boost Software License 1.0"),
        ThirdPartyComponent("OkHttp", "Apache-2.0"),
        ThirdPartyComponent("Lottie", "Apache-2.0"),
        ThirdPartyComponent("ZXing core", "Apache-2.0"),
        ThirdPartyComponent("Haze", "Apache-2.0")
    )
}

/** One third-party dependency listed on the About page. */
data class ThirdPartyComponent(
    val name: String,
    val license: String
)
