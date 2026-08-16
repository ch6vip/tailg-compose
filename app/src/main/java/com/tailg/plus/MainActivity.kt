package com.tailg.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tailg.plus.ui.navigation.TailgNavHost
import com.tailg.plus.ui.theme.TailgTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fixed light theme (Dart: ThemeMode.light + SystemUiOverlayStyle with
        // statusBarIconBrightness: dark). The app renders Cyber light pages, so
        // status/nav bar icons must be dark regardless of the system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TailgTheme {
                TailgNavHost()
            }
        }
    }
}
