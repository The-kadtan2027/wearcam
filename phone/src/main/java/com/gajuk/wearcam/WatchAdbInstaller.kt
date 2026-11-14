package com.gajuk.wearcam

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Installs the bundled watch APK directly from the phone: pairs with the watch's wireless
 * debugging (Android 11+ pairing) and pushes the APK with Kadb — no PC, no Play Store.
 */
object WatchAdbInstaller {

    interface Callback {
        fun onStatus(text: String)
        fun onResult(ok: Boolean, message: String)
    }

    fun pairAndInstall(host: String, pairingPort: Int, code: String,
                       connectPort: Int, apk: File, callback: Callback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                callback.onStatus("Pairing with $host:$pairingPort…")
                Kadb.pair(host, pairingPort, code)
                callback.onStatus("Paired — installing…")
                Kadb.create(host, connectPort).use { kadb ->
                    kadb.install(apk)
                }
                callback.onResult(true, "Watch app installed")
            } catch (e: Throwable) {
                callback.onResult(false, e.message ?: e.toString())
            }
        }
    }
}
