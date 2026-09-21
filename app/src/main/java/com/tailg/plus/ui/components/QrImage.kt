package com.tailg.plus.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QR code image rendered with zxing-core (Dart used the `qr_flutter`
 * equivalent in the garage vehicle-code sheet).
 *
 * The rasteriser fills `sizePx * sizePx` pixels one by one — 262 144
 * iterations plus a 2 MB ARGB_8888 allocation at the default 512px. That used
 * to run inside `remember`, i.e. synchronously on the main thread the first
 * time the vehicle-code sheet composed, which blocked the sheet's entrance
 * animation for tens to hundreds of milliseconds.
 *
 * It now runs on [Dispatchers.Default] through [produceState]; the bitmap is
 * absent for the first frames, so the sheet's white card shows through and the
 * code pops in when ready. The result is scoped to this composition: closing
 * and reopening the sheet re-rasterises (same lifetime as the previous
 * `remember`, which also never outlived the composable).
 */
@Composable
fun QrImage(
  content: String,
  modifier: Modifier = Modifier,
  sizePx: Int = 512,
) {
  val bitmap by produceState<ImageBitmap?>(initialValue = null, content, sizePx) {
    value = withContext(Dispatchers.Default) {
      renderQrBitmap(content, sizePx).asImageBitmap()
    }
  }
  val frame = bitmap ?: return
  Image(
    bitmap = frame,
    contentDescription = stringResource(R.string.qr_image_label),
    modifier = modifier,
  )
}

/**
 * Blocking QR rasteriser. Must be called off the main thread.
 *
 * `QRCodeWriter.encode` throws `WriterException` when the payload cannot fit
 * the requested size; an empty (transparent) frame is returned instead of
 * letting the exception escape the coroutine.
 */
private fun renderQrBitmap(content: String, sizePx: Int): Bitmap =
  try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
      val rowOffset = y * sizePx
      for (x in 0 until sizePx) {
        pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
      }
    }
    Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
  } catch (e: Exception) {
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  }
