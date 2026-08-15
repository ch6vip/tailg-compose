package com.tailg.plus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tailg.plus.ui.theme.AppColors

/**
 * Root navigation host.
 *
 * Placeholder for now — replaced by the real route graph during the UI port
 * phase (login → garage → control → …).
 */
@Composable
fun TailgNavHost() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.pageBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "台铃智能 Plus",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
