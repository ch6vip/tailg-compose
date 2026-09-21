package com.tailg.plus.macrobenchmark

/**
 * Application id of the :app module under test.
 *
 * Kept in one place so every benchmark targets the same package — the
 * `benchmark` build type in app/build.gradle.kts does not add an
 * applicationIdSuffix, so this is the plain production id.
 */
internal const val APP_PACKAGE = "com.tailg.plus"
