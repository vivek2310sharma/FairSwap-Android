package com.fairswap.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.account.ProfileField

@Composable
fun AccountScreen(
    state: AccountUiState,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onFormChange: (ProfileForm) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onFavourites: () -> Unit,
    onMyListings: () -> Unit = {},
    onMessages: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("My Account", style = MaterialTheme.typography.headlineMedium)
            if (state.account == null) {
                if (state.loading) CircularProgressIndicator()
                else {
                    Text(state.error ?: "Unable to load your account.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                }
                return@Column
            }
            val account = state.account
            Text(account.email ?: "Email unavailable")
            Text("Account status: ${account.accountStatus}", style = MaterialTheme.typography.bodyMedium)
            account.termsVersion?.let { Text("Accepted terms version: $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(4.dp))
            if (state.editing) {
                ProfileEditor(state, onFormChange)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSave, enabled = !state.saving) { Text(if (state.saving) "Saving…" else "Save") }
                    OutlinedButton(onClick = onCancel, enabled = !state.saving) { Text("Cancel") }
                }
            } else {
                Text(account.profile.displayName, style = MaterialTheme.typography.titleLarge)
                listOfNotNull(account.profile.city, account.profile.state).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", ")) }
                account.profile.bio?.let { Text(it) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !state.loading) { Text("Edit Public Profile") }
                    if (state.loading) CircularProgressIndicator()
                    else OutlinedButton(onClick = onRetry) { Text("Refresh") }
                }
            }
            OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) { Text("Notifications") }
            OutlinedButton(onClick = onMessages, modifier = Modifier.fillMaxWidth()) { Text("Messages") }
            OutlinedButton(onClick = onMyListings, modifier = Modifier.fillMaxWidth()) { Text("My Listings") }
            OutlinedButton(onClick = onFavourites, modifier = Modifier.fillMaxWidth()) { Text("Saved Favourites") }
            OutlinedButton(onClick = onSignOut, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Sign Out") }
        }
    }
}

@Composable
private fun ProfileEditor(state: AccountUiState, onFormChange: (ProfileForm) -> Unit) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Edit Public Profile", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = form.displayName,
            onValueChange = { onFormChange(form.copy(displayName = it.take(60))) },
            label = { Text("Display Name") },
            supportingText = { Text("${form.displayName.length}/50 characters") },
            isError = state.validationError?.field == ProfileField.DISPLAY_NAME,
            singleLine = true,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        state.validationError?.takeIf { it.field == ProfileField.DISPLAY_NAME }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = form.bio,
            onValueChange = { onFormChange(form.copy(bio = it.take(520))) },
            label = { Text("About / Bio") },
            supportingText = { Text("${form.bio.length}/500 characters") },
            isError = state.validationError?.field == ProfileField.BIO,
            enabled = !state.saving,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        state.validationError?.takeIf { it.field == ProfileField.BIO }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = form.city,
            onValueChange = { onFormChange(form.copy(city = it.take(120))) },
            label = { Text("City") },
            supportingText = { Text("${form.city.length}/100 characters") },
            isError = state.validationError?.field == ProfileField.CITY,
            enabled = !state.saving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.validationError?.takeIf { it.field == ProfileField.CITY }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = form.state,
            onValueChange = { onFormChange(form.copy(state = it.take(120))) },
            label = { Text("State") },
            supportingText = { Text("${form.state.length}/100 characters") },
            isError = state.validationError?.field == ProfileField.STATE,
            enabled = !state.saving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.validationError?.takeIf { it.field == ProfileField.STATE }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
    }
}
