package com.tailg.plus.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * QR code image rendered with zxing-core (Dart used the `qr_flutter`
 * equivalent in the garage vehicle-code sheet).
 */
@Composable
fun QrImage(
  content: String,
  modifier: Modifier = Modifier,
  sizePx: Int = 512,
) {
  val bitmap = remember(content, sizePx) {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
      val rowOffset = y * sizePx
      for (x in 0 until sizePx) {
        pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
      }
    }
    Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
  }
  Image(
    bitmap = bitmap.asImageBitmap(),
    contentDescription = stringResource(R.string.qr_image_label),
    modifier = modifier,
  )
}
