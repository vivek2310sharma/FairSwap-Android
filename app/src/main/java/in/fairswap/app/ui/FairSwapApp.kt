package in.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private data object FoundationRoute : NavKey

@Composable
fun FairSwapApp() {
    val backStack = rememberNavBackStack(FoundationRoute)
    val foundationViewModel: FoundationViewModel = viewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                FoundationRoute -> NavEntry(key) {
                    FoundationScreen(
                        uiState = foundationViewModel.uiState,
                        onCheckConnection = foundationViewModel::checkConnection,
                    )
                }
                else -> error("Unknown navigation key: $key")
            }
        },
    )
}
