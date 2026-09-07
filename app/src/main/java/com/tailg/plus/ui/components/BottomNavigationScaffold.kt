package com.tailg.plus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.CyberHomeColors

/** Clearance for scrollable content and overlays above the app's bottom navigation. */
internal val LocalBottomNavigationPadding = compositionLocalOf { 0.dp }

internal const val BottomNavigationContainerAlpha = 0.82f

/**
 * Draws pages behind the translucent bar. Apply [LocalBottomNavigationPadding]
 * inside a scroll container so its last item can still scroll clear of the bar.
 * Scaffold measures the clearance, including system insets, for either bar style.
 */
@Composable
internal fun BottomNavigationScaffold(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = CyberHomeColors.pageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { padding ->
        CompositionLocalProvider(
            LocalBottomNavigationPadding provides padding.calculateBottomPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Pages handle the bar clearance themselves; nested scaffolds
                    // must not add the system navigation inset a second time.
                    .consumeWindowInsets(padding),
            ) {
                content()
            }
        }
    }
}
