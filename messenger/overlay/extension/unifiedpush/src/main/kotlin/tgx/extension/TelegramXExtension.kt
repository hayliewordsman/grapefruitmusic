// SPDX-License-Identifier: GPL-3.0-or-later
// Grapefruitgram Messenger: the push extension of the FOSS build.
package tgx.extension

import android.content.Context
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.Extension
import tgx.extension.unifiedpush.UnifiedPushTokenRetriever

object TelegramXExtension : Extension("unifiedpush") {
  // Always offered: without a distributor installed, the retriever reports itself unavailable and
  // the app keeps its own background connection instead, as Telegram X does without Firebase.
  override fun createNewTokenRetriever(context: Context): DeviceTokenRetriever =
    UnifiedPushTokenRetriever()
}
