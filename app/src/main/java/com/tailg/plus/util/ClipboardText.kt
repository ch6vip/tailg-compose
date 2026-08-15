package com.tailg.plus.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Port of `lib/services/clipboard_text.dart`.
 *
 * `android.content.Context` is injected via the constructor (no static
 * Application access). Dart's `Future` wrappers are the Flutter platform
 * channel hop; Android's `ClipboardManager` calls are synchronous, so these
 * are plain functions instead of `suspend` — callers on the main thread are
 * fine (clipboard access is fast and does not block on I/O).
 */
class ClipboardText(private val context: Context) {

    /**
     * `readClipboardText({trim: true})`: plain text from the system clipboard,
     * or null when there is no text payload. With [trim] (default), values
     * that become empty after trimming are also null, so "missing" and
     * "blank" can be treated the same.
     */
    fun readClipboardText(trim: Boolean = true): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        // coerceToText mirrors Flutter's Clipboard.getData(kTextPlain).
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return null
        val value = if (trim) text.trim() else text
        if (trim && value.isEmpty()) return null
        return value
    }

    /** `writeClipboardText`: replace the primary clip with [text] as plain text. */
    fun writeClipboardText(text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(null, text))
    }
}
