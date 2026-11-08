// SPDX-License-Identifier: GPL-3.0-or-later
package tgx.extension.unifiedpush

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.drinkless.tdlib.TdApi.DeviceTokenSimplePush
import tgx.bridge.TokenRetrieverListener

/** The endpoint the distributor gave us, and whoever is waiting for the first one. */
internal object Endpoints {
  private const val PREFS = "grapefruit_unifiedpush"
  private const val KEY_ENDPOINT = "endpoint"
  private const val REGISTRATION_TIMEOUT_MS = 30_000L

  private val main = Handler(Looper.getMainLooper())
  private var pending: TokenRetrieverListener? = null
  private val timeout = Runnable {
    val listener = synchronized(this) { pending.also { pending = null } }
    listener?.onTokenRetrievalError("UNIFIEDPUSH_TIMEOUT", null)
  }

  fun get(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, null)

  fun set(context: Context, endpoint: String?) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
      if (endpoint != null) putString(KEY_ENDPOINT, endpoint) else remove(KEY_ENDPOINT)
    }.apply()
  }

  fun awaitEndpoint(listener: TokenRetrieverListener) {
    synchronized(this) { pending = listener }
    main.removeCallbacks(timeout)
    main.postDelayed(timeout, REGISTRATION_TIMEOUT_MS)
  }

  /** Returns true when someone was waiting for this endpoint (so it's been delivered already). */
  fun deliver(endpoint: String): Boolean {
    val listener = synchronized(this) { pending.also { pending = null } } ?: return false
    main.removeCallbacks(timeout)
    main.post { listener.onTokenRetrievalSuccess(DeviceTokenSimplePush(endpoint)) }
    return true
  }

  fun fail(reason: String) {
    val listener = synchronized(this) { pending.also { pending = null } } ?: return
    main.removeCallbacks(timeout)
    main.post { listener.onTokenRetrievalError(reason, null) }
  }
}
