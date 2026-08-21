package com.tailg.plus.ui.navigation

import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.screens.LoginScreen
import kotlinx.coroutines.launch

/**
 * Authentication navigation graph — login screen.
 *
 * @param cloudService the cloud service for authentication
 * @param snackbarHostState the snackbar host state from the parent Scaffold
 * @param onSignedIn callback after successful login, receives the navigation route
 */
fun NavGraphBuilder.authNavGraph(
    navController: NavController,
    cloudService: OfficialCloudService,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    composable(Routes.LOGIN) {
        val scope = rememberCoroutineScope()
        LoginScreen(
            cloudService = cloudService,
            onSignedIn = { successMessage ->
                navController.navigate(Routes.vehicleHome(cloudService.currentState.selectedVehicle?.key)) {
                    launchSingleTop = true
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
                if (successMessage != null) {
                    scope.launch { AppSnack.success(snackbarHostState, message = successMessage) }
                }
            },
        )
    }
}