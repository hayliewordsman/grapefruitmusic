// Grapefruitgram Messenger: push notifications through UnifiedPush (https://unifiedpush.org),
// the free alternative to Firebase Cloud Messaging. Telegram's servers wake the app through the
// distributor the user has installed (ntfy, NextPush, Sunup, ...); the app then fetches its updates
// itself, so no message content passes through the distributor.
plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.library.get().pluginId)
  id("tgx-module")
}

dependencies {
  // UnifiedPush connector 2.5.0 (Apache-2.0): minSdk 16, depends only on the Kotlin standard library.
  implementation(libs.unifiedpush.connector)
  implementation(project(":tdlib"))
  api(project(":extension:bridge"))
}

android {
  namespace = "tgx.extension"
}
