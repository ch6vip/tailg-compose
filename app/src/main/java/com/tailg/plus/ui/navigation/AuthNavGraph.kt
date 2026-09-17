package com.tailg.plus.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tailg.plus.data.cloud.OfficialCloudService
import com.tailg.plus.ui.components.AppSnack
import com.tailg.plus.ui.screens.LoginScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Authentication navigation graph — login screen.
 *
 * @param cloudService the cloud service for authentication
 * @param snackbarHostState the snackbar host state from the parent Scaffold
 * @param snackbarScope a scope that outlives this destination (the nav-host
 *   scope). The success snackbar must NOT be launched on the LOGIN composable's
 *   own `rememberCoroutineScope`: the same callback pops LOGIN, which cancels
 *   that scope and dismisses the snackbar before it is seen.
 */
fun NavGraphBuilder.authNavGraph(
    navController: NavController,
    cloudService: OfficialCloudService,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    snackbarScope: CoroutineScope,
) {
    composable(Routes.LOGIN) {
        LoginScreen(
            cloudService = cloudService,
            onSignedIn = { successMessage ->
                navController.navigate(Routes.vehicleHome(cloudService.currentState.selectedVehicle?.key)) {
                    launchSingleTop = true
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
                if (successMessage != null) {
                    snackbarScope.launch { AppSnack.success(snackbarHostState, message = successMessage) }
                }
            },
        )
    }
}