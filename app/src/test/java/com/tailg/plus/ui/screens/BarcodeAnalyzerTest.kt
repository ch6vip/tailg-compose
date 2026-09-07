package com.tailg.plus.ui.screens

import android.media.Image
import android.os.Looper
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarcodeAnalyzerTest {
  private fun frame(): ImageProxy {
    val image = mockk<Image>()
    val proxy = mockk<ImageProxy>(relaxed = true)
    every { proxy.image } returns image
    every { proxy.imageInfo.rotationDegrees } returns 0
    every { InputImage.fromMediaImage(image, 0) } returns mockk()
    return proxy
  }

  @Test
  fun aResultArrivingAfterDisposalCannotNavigate() {
    mockkStatic(InputImage::class)
    try {
      val frame = frame()
      val result = TaskCompletionSource<List<Barcode>>()
      val scanner = mockk<BarcodeScanner>()
      every { scanner.process(any<InputImage>()) } returns result.task
      val detected = mutableListOf<String>()
      val analyzer = BarcodeAnalyzer(scanner, detected::add)
      analyzer.analyze(frame)
      analyzer.enabled = false
      result.setResult(listOf(mockk { every { rawValue } returns "vehicle code" }))
      shadowOf(Looper.getMainLooper()).idle()

      assertEquals(emptyList<String>(), detected)
      verify(exactly = 1) { frame.close() }
    } finally {
      unmockkStatic(InputImage::class)
    }
  }

  @Test
  fun aSynchronousScannerFailureStillReleasesTheCameraFrame() {
    mockkStatic(InputImage::class)
    try {
      val frame = frame()
      val scanner = mockk<BarcodeScanner>()
      every { scanner.process(any<InputImage>()) } throws IllegalStateException("Scanner is closed")

      BarcodeAnalyzer(scanner) {}.analyze(frame)

      verify(exactly = 1) { frame.close() }
    } finally {
      unmockkStatic(InputImage::class)
    }
  }
}
