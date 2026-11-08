// SPDX-License-Identifier: GPL-3.0-or-later
package tgx.extension.unifiedpush

import android.content.Context
import android.content.Intent
import org.drinkless.tdlib.TdApi.DeviceTokenSimplePush
import org.unifiedpush.android.connector.MessagingReceiver
import tgx.bridge.PushManagerBridge

/**
 * Messages from the UnifiedPush distributor. A message is Telegram's Simple Push wake-up: it carries
 * no chat content, so it's handed to the app's push processor as a wake-up, which makes every
 * account sync with Telegram and show its notifications (as Telegram X does for a push it can't read).
 */
class UnifiedPushReceiver : MessagingReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    // Syncing can take a few seconds: keep the broadcast alive and work off the main thread.
    val result = goAsync()
    Thread({
      try {
        super.onReceive(context, intent)
      } catch (t: Throwable) {
        PushManagerBridge.error("UnifiedPush: failed to handle ${intent.action}", t)
      } finally {
        result.finish()
      }
    }, "UnifiedPushReceiver").start()
  }

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    PushManagerBridge.log("UnifiedPush: new endpoint")
    Endpoints.set(context, endpoint)
    if (!Endpoints.deliver(endpoint)) {
      // A changed endpoint (new distributor, re-registration): tell Telegram.
      PushManagerBridge.onNewToken(context, DeviceTokenSimplePush(endpoint))
    }
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    val sentTime = System.currentTimeMillis()
    val payload = mutableMapOf<String, Any>()
    payload["unifiedpush.wakeup"] = sentTime
    val body = String(message, Charsets.UTF_8).trim()
    if (body.isNotEmpty()) {
      payload["unifiedpush.body"] = body
    }
    PushManagerBridge.onMessageReceived(context, payload, sentTime, 0)
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    PushManagerBridge.log("UnifiedPush: registration failed")
    Endpoints.set(context, null)
    Endpoints.fail("UNIFIEDPUSH_REGISTRATION_FAILED")
  }

  override fun onUnregistered(context: Context, instance: String) {
    PushManagerBridge.log("UnifiedPush: unregistered")
    Endpoints.set(context, null)
    Endpoints.fail("UNIFIEDPUSH_UNREGISTERED")
  }
}
