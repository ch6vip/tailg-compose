package com.tailg.plus.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tailg.plus.ui.components.AppPressable
import com.tailg.plus.ui.components.Lucide
import com.tailg.plus.ui.components.LucideIcon
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.AppTouchTargets
import com.tailg.plus.ui.theme.CyberHomeColors
import timber.log.Timber
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * Port of `lib/pages/garage_code_scanner_page.dart` — QR / barcode scanner.
 *
 * The Dart page uses `mobile_scanner` (MobileScannerController) with QR,
 * Code128 and Code39 formats. This Compose port uses CameraX (Preview +
 * ImageAnalysis) bound to the activity lifecycle and ML Kit BarcodeScanning
 * with the same format set.
 *
 * When a barcode is detected, [onScanned] is called with the trimmed raw
 * value and the host pops the screen (Dart `Navigator.pop(value)`). A
 * [handled] guard prevents duplicate callbacks for the same scan window.
 */
@Composable
fun GarageCodeScannerScreen(
  onBack: () -> Unit,
  onScanned: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var torchOn by remember { mutableStateOf(false) }
  var camera by remember { mutableStateOf<Camera?>(null) }
  var handled by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted -> hasCameraPermission = granted }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  // Dart: AnnotatedRegion<SystemUiOverlayStyle> — dark scanner page needs
  // LIGHT status/nav bar icons; restore the app's dark-icon default on exit.
  val activity = androidx.activity.compose.LocalActivity.current
  if (activity != null) {
    DisposableEffect(activity) {
      val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
      val prevLightStatus = controller.isAppearanceLightStatusBars
      val prevLightNav = controller.isAppearanceLightNavigationBars
      controller.isAppearanceLightStatusBars = false
      controller.isAppearanceLightNavigationBars = false
      onDispose {
        controller.isAppearanceLightStatusBars = prevLightStatus
        controller.isAppearanceLightNavigationBars = prevLightNav
      }
    }
  }

  // ML Kit scanner configured for the same formats as the Dart page.
  val barcodeScanner = remember {
    BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
          Barcode.FORMAT_QR_CODE,
          Barcode.FORMAT_CODE_128,
          Barcode.FORMAT_CODE_39,
        )
        .build(),
    )
  }

  // Release the ML Kit scanner when the composable leaves the tree.
  DisposableEffect(barcodeScanner) {
    onDispose { barcodeScanner.close() }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CyberHomeColors.ink,
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(CyberHomeColors.ink),
    ) {
      if (hasCameraPermission) {
        CameraPreview(
          lifecycleOwner = lifecycleOwner,
          barcodeScanner = barcodeScanner,
          onCameraReady = { camera = it },
          onDetected = { value ->
            if (!handled && value.isNotEmpty()) {
              handled = true
              onScanned(value)
            }
          },
        )
        ScannerMask()
      } else {
        ScannerPlaceholder(hasPermission = hasCameraPermission, onOpenSettings = {
          permissionLauncher.launch(Manifest.permission.CAMERA)
        })
      }

      // Overlay chrome (back + torch + hint).
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          ScannerAction(
            label = stringResource(R.string.common_back),
            icon = Lucide.arrowLeft,
            onTap = onBack,
          )
          ScannerAction(
            label = if (torchOn) stringResource(R.string.garage_scan_torch_off) else stringResource(R.string.garage_scan_torch_on),
            icon = Lucide.zap,
            active = torchOn,
            onTap = {
              val cam = camera
              if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                val next = !torchOn
                cam.cameraControl.enableTorch(next)
                torchOn = next
              }
            },
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = stringResource(R.string.garage_scan_vehicle_qr),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = TextStyle(
            color = CyberHomeColors.white,
            fontSize = 15.sp,
            fontWeight = FontWeight.W700,
          ),
        )
      }
    }
  }
}

/**
 * CameraX preview + ML Kit barcode analysis. The [PreviewView] is created
 * once and reused; binding to the lifecycle happens once the
 * [ProcessCameraProvider] future resolves. [onCameraReady] exposes the bound
 * [Camera] so the host can toggle the torch.
 */
@Composable
private fun CameraPreview(
  lifecycleOwner: LifecycleOwner,
  barcodeScanner: BarcodeScanner,
  onCameraReady: (Camera) -> Unit,
  onDetected: (String) -> Unit,
) {
  val context = LocalContext.current

  val previewView = remember { PreviewView(context) }
  val analyzer = remember { BarcodeAnalyzer(barcodeScanner, onDetected) }
  val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

  AndroidView(
    factory = { previewView },
    modifier = Modifier.fillMaxSize(),
  )

  LaunchedEffect(Unit) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
      {
        val cameraProvider = try {
          future.get()
        } catch (e: Exception) {
          Timber.tag("GarageCodeScanner").e(e, "ProcessCameraProvider unavailable")
          return@addListener
        }

        val preview = Preview.Builder().build().also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { it.setAnalyzer(mainExecutor, analyzer) }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
          // Unbind any previous use cases before rebinding.
          cameraProvider.unbindAll()
          val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            imageAnalysis,
          )
          onCameraReady(camera)
        } catch (e: Exception) {
          Timber.tag("GarageCodeScanner").e(e, "Camera bind failed")
        }
      },
      mainExecutor,
    )
  }
}

/**
 * ImageAnalysis.Analyzer that feeds CameraX frames into ML Kit
 * [BarcodeScanning]. Detection runs on the ML Kit task thread; the callback
 * is dispatched back to the main executor set on the ImageAnalysis.
 */
private class BarcodeAnalyzer(
  private val scanner: BarcodeScanner,
  private val onDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }

    val inputImage = InputImage.fromMediaImage(
      mediaImage,
      imageProxy.imageInfo.rotationDegrees,
    )

    scanner.process(inputImage)
      .addOnSuccessListener { barcodes ->
        for (barcode in barcodes) {
          val value = barcode.rawValue?.trim() ?: ""
          if (value.isNotEmpty()) {
            onDetected(value)
            break
          }
        }
      }
      .addOnCompleteListener { imageProxy.close() }
  }
}

/**
 * Scan-window mask matching the Dart `_ScannerMaskPainter`: a translucent
 * overlay with a rounded square window centered at 44% height, plus a white
 * stroke frame. Drawn over the live camera preview.
 */
@Composable
private fun ScannerMask() {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val windowSide = size.width.coerceIn(220f, 290f)
    val window = Rect(
      offset = Offset(
        x = (size.width - windowSide) / 2f,
        y = size.height * 0.44f - windowSide / 2f,
      ),
      size = Size(windowSide, windowSide),
    )

    val overlay = Path().apply {
      addRect(
        Rect(
          offset = Offset.Zero,
          size = this@Canvas.size,
        ),
      )
      addRoundRect(
        RoundRect(
          left = window.left,
          top = window.top,
          right = window.right,
          bottom = window.bottom,
          cornerRadius = CornerRadius(
            AppRadii.tile.toPx(),
            AppRadii.tile.toPx(),
          ),
        ),
      )
      fillType = PathFillType.EvenOdd
    }

    drawPath(
      path = overlay,
      color = CyberHomeColors.ink.copy(alpha = 0.62f),
    )
    drawRoundRect(
      color = CyberHomeColors.white,
      topLeft = window.topLeft,
      size = window.size,
      cornerRadius = CornerRadius(
        AppRadii.tile.toPx(),
        AppRadii.tile.toPx(),
      ),
      style = Stroke(width = 2f),
    )
  }
}

@Composable
private fun ScannerPlaceholder(
  hasPermission: Boolean,
  onOpenSettings: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    LucideIcon(
      icon = Lucide.alertCircle,
      size = 32.dp,
      color = CyberHomeColors.white,
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = if (hasPermission) stringResource(R.string.garage_scan_coming_soon) else stringResource(R.string.garage_scan_camera_permission),
      textAlign = TextAlign.Center,
      style = TextStyle(
        color = CyberHomeColors.white,
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
      ),
    )
    if (!hasPermission) {
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onOpenSettings,
        colors = ButtonDefaults.buttonColors(
          containerColor = CyberHomeColors.primary,
          contentColor = CyberHomeColors.white,
        ),
      ) {
        Text(stringResource(R.string.garage_scan_open_settings))
      }
    }
  }
}

@Composable
private fun ScannerAction(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onTap: () -> Unit,
  active: Boolean = false,
) {
  AppPressable(
    onClick = onTap,
    semanticsLabel = label,
    semanticsButton = true,
    shape = CircleShape,
    background = if (active) CyberHomeColors.primary else CyberHomeColors.ink.copy(alpha = 0.56f),
  ) {
    Box(
      modifier = Modifier.size(AppTouchTargets.min),
      contentAlignment = Alignment.Center,
    ) {
      LucideIcon(icon = icon, color = CyberHomeColors.white, size = 20.dp)
    }
  }
}
