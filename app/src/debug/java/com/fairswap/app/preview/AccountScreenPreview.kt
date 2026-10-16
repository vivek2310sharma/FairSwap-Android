package com.fairswap.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
import com.fairswap.core.account.MyAccount
import com.fairswap.core.account.ProfileField
import com.fairswap.core.account.PublicProfile
import com.fairswap.core.account.ProfileValidationError
import com.fairswap.core.designsystem.theme.FairSwapWebTheme
import com.fairswap.feature.account.AccountScreen
import com.fairswap.feature.account.AccountUiState
import com.fairswap.feature.account.ProfileForm

import android.content.res.Configuration

private val sampleAccount = MyAccount(
    userId = "123",
    email = "test@example.com",
    accountStatus = "active",
    termsVersion = "2026-09-18",
    profile = PublicProfile(
        displayName = "Demo Seller",
        city = "Bengaluru",
        state = "Karnataka",
        bio = "High-quality used goods.",
        updatedAt = ""
    )
)

private val suspendedAccount = sampleAccount.copy(accountStatus = "suspended")
private val longContentAccount = sampleAccount.copy(
    profile = sampleAccount.profile.copy(
        displayName = "A Very Long Display Name That Wraps Multiple Lines And Explores Text Sizing Constraints",
        bio = "This is a very long bio. It goes on for quite some time to test how the UI handles multiline text blocks in the profile viewer and editor. We should ensure it doesn't get clipped and continues correctly down the page, taking as much space as it requires, or fitting inside scrollable containers."
    )
)

@Preview(name = "1. Loading", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountLoading() {
    AccountScreen(
        state = AccountUiState(userId = "123", loading = true),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "2. Error Retry", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountError() {
    AccountScreen(
        state = AccountUiState(userId = "123", loading = false, error = "Network connection failed. Please try again."),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "3. Populated Light (360dp)", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountPopulatedLight360() {
    // Relying on system default (light)
    AccountScreen(
        state = AccountUiState(userId = "123", loading = false, account = sampleAccount),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "4. Populated Dark (412dp)", showBackground = true, widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewAccountPopulatedDark412() {
    // Relying on system mode (dark)
    AccountScreen(
        state = AccountUiState(userId = "123", loading = false, account = sampleAccount),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "4a. Inverse: Forced Dark in Light Mode", showBackground = true, widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
fun PreviewAccountForcedDark() {
    FairSwapWebTheme(darkTheme = true) {
        AccountScreen(
            state = AccountUiState(userId = "123", loading = false, account = sampleAccount),
            onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
        )
    }
}

@Preview(name = "4b. Inverse: Forced Light in Dark Mode", showBackground = true, widthDp = 360, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewAccountForcedLight() {
    FairSwapWebTheme(darkTheme = false) {
        AccountScreen(
            state = AccountUiState(userId = "123", loading = false, account = sampleAccount),
            onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
        )
    }
}

@Preview(name = "5. Refreshing In Progress", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountRefreshing() {
    AccountScreen(
        state = AccountUiState(userId = "123", loading = true, account = sampleAccount),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "6. Editing Light (font 2.0)", showBackground = true, widthDp = 412, heightDp = 892, fontScale = 2.0f)
@Composable
fun PreviewAccountEditingLargeFont() {
    val state = AccountUiState(
        userId = "123",
        loading = false,
        editing = true,
        account = sampleAccount,
        form = ProfileForm(displayName = "Demo Seller", bio = "High-quality used goods.", city = "Bengaluru", state = "Karnataka")
    )
    AccountScreen(
        state = state,
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "7. Editing Field Error (Fixture)", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountEditingError() {
    val state = AccountUiState(
        userId = "123",
        loading = false,
        editing = true,
        account = sampleAccount,
        form = ProfileForm(displayName = "", bio = "", city = "", state = ""),
        validationError = ProfileValidationError(field = ProfileField.DISPLAY_NAME, message = "Display name is required. (Fixture)")
    )
    AccountScreen(
        state = state,
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "8. Saving (Disabled Actions)", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountSaving() {
    val state = AccountUiState(
        userId = "123",
        loading = false,
        editing = true,
        saving = true,
        account = sampleAccount,
        form = ProfileForm(displayName = "Demo Seller", bio = "Saving changes...", city = "Bengaluru", state = "Karnataka")
    )
    AccountScreen(
        state = state,
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "9. Suspended Account", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun PreviewAccountSuspended() {
    AccountScreen(
        state = AccountUiState(userId = "123", loading = false, account = suspendedAccount),
        onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
    )
}

@Preview(name = "10. Long Content", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountLongContent() {
    FairSwapWebTheme(darkTheme = false) {
        AccountScreen(
            state = AccountUiState(userId = "123", loading = false, account = longContentAccount),
            onEdit = {}, onCancel = {}, onFormChange = {}, onSave = {}, onRetry = {}, onFavourites = {}, onBlockedUsers = {}, onPrivacyRequests = {}, onSupport = {}, onSafety = {}, onTerms = {}, onPrivacyPolicy = {}, onSignOut = {}
        )
    }
}

@Composable
fun InteractiveAccountHarness() {
    var state by remember { mutableStateOf(AccountUiState(userId = "123", loading = false, account = sampleAccount)) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var editCount by remember { mutableStateOf(0) }
    var saveCount by remember { mutableStateOf(0) }
    var retryCount by remember { mutableStateOf(0) }
    
    fun log(msg: String) {
        logs = (listOf(msg) + logs).take(5)
    }

    FairSwapWebTheme(darkTheme = false) {
        Column {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(8.dp), color = androidx.compose.ui.graphics.Color.LightGray) {
                Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            state = AccountUiState(userId = "123", loading = false, account = sampleAccount)
                            logs = emptyList()
                            editCount = 0; saveCount = 0; retryCount = 0
                        }) { Text("Reset") }
                        Button(onClick = { 
                            state = state.copy(saving = false, editing = false)
                            log("Mock save complete")
                        }) { Text("Finish Save") }
                        Button(onClick = { 
                            state = state.copy(loading = false)
                            log("Mock refresh complete")
                        }) { Text("Finish Refresh") }
                    }
                    Text("Interactive Log (E:$editCount S:$saveCount R:$retryCount):", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    Text("State -> editing:${state.editing}, loading:${state.loading}, saving:${state.saving}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    Text("Form -> name:'${state.form.displayName}' bio length:${state.form.bio.length} city:'${state.form.city}' state:'${state.form.state}'", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    logs.forEach { Text(it, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
                }
            }
            AccountScreen(
                state = state,
                onEdit = { 
                    editCount++
                    log("onEdit clicked")
                    state = state.copy(editing = true, form = ProfileForm(displayName = state.account?.profile?.displayName ?: "", bio = state.account?.profile?.bio ?: "", city = state.account?.profile?.city ?: "", state = state.account?.profile?.state ?: "")) 
                },
                onCancel = { 
                    log("onCancel clicked")
                    state = state.copy(editing = false, validationError = null) 
                },
                onFormChange = { 
                    log("onFormChange: ${it.displayName}")
                    state = state.copy(form = it) 
                },
                onSave = { 
                    saveCount++
                    log("onSave clicked")
                    state = state.copy(saving = true)
                },
                onRetry = { 
                    retryCount++
                    log("onRetry clicked")
                    state = state.copy(loading = true)
                },
                onFavourites = { log("onFavourites clicked") },
                onMyListings = { log("onMyListings clicked") },
                onMessages = { log("onMessages clicked") },
                onNotifications = { log("onNotifications clicked") },
                onBlockedUsers = { log("onBlockedUsers clicked") },
                onPrivacyRequests = { log("onPrivacyRequests clicked") },
                onSupport = { log("onSupport clicked") },
                onSafety = { log("onSafety clicked") },
                onTerms = { log("onTerms clicked") },
                onPrivacyPolicy = { log("onPrivacyPolicy clicked") },
                onSignOut = { log("onSignOut clicked") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(name = "11. Interactive (360dp, 1.0x)", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewAccountInteractive360() {
    InteractiveAccountHarness()
}

@Preview(name = "12. Interactive (412dp, 2.0x)", showBackground = true, widthDp = 412, heightDp = 892, fontScale = 2.0f)
@Composable
fun PreviewAccountInteractive412Large() {
    InteractiveAccountHarness()
}
