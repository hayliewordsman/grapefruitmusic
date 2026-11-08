// SPDX-License-Identifier: GPL-3.0-or-later
// Grapefruitgram Messenger: device tokens from UnifiedPush, registered with Telegram as Simple Push.
package tgx.extension.unifiedpush

import android.content.Context
import org.drinkless.tdlib.TdApi.DeviceTokenSimplePush
import org.unifiedpush.android.connector.UnifiedPush
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.TokenRetrieverListener

/**
 * Registers with the user's UnifiedPush distributor and gives Telegram the endpoint it returns as a
 * Simple Push token. Telegram then POSTs to that endpoint when there is something new, and the
 * distributor wakes the app ([UnifiedPushReceiver]), which syncs with Telegram itself.
 */
class UnifiedPushTokenRetriever : DeviceTokenRetriever("unifiedpush") {
  override fun isAvailable(context: Context): Boolean =
    UnifiedPush.getDistributors(context).isNotEmpty()

  override val configuration: String
    get() = "UnifiedPush"

  override fun performInitialization(context: Context): Boolean = true

  override fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener) {
    val app = context.applicationContext
    val distributors = UnifiedPush.getDistributors(app)
    if (distributors.isEmpty()) {
      PushManagerBridge.log("UnifiedPush: no distributor installed")
      listener.onTokenRetrievalError("UNIFIEDPUSH_NO_DISTRIBUTOR", null)
      return
    }
    // Keep the distributor the user already chose; otherwise take the first one installed.
    val distributor = UnifiedPush.getAckDistributor(app)?.takeIf { distributors.contains(it) }
      ?: UnifiedPush.getSavedDistributor(app)?.takeIf { distributors.contains(it) }
      ?: distributors.first()
    UnifiedPush.saveDistributor(app, distributor)
    val endpoint = Endpoints.get(app)
    if (endpoint != null) {
      // Already registered: the distributor keeps the endpoint until it tells us otherwise.
      PushManagerBridge.log("UnifiedPush: using saved endpoint from %s", distributor)
      listener.onTokenRetrievalSuccess(DeviceTokenSimplePush(endpoint))
      return
    }
    PushManagerBridge.log("UnifiedPush: registering with %s", distributor)
    Endpoints.awaitEndpoint(listener)
    UnifiedPush.registerApp(app, INSTANCE, arrayListOf(), "Grapefruitgram Messenger")
  }

  companion object {
    const val INSTANCE = "default"
  }
}
