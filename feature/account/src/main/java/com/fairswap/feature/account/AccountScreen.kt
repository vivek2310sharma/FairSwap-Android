package com.fairswap.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairswap.core.account.ProfileField
import com.fairswap.core.designsystem.components.FairSwapCard
import com.fairswap.core.designsystem.components.FairSwapNotice
import com.fairswap.core.designsystem.components.FairSwapOutlinedButton
import com.fairswap.core.designsystem.components.FairSwapPrimaryButton
import com.fairswap.core.designsystem.components.FairSwapTextField
import com.fairswap.core.designsystem.R as DesignR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onBlockedUsers: () -> Unit = {},
    onPrivacyRequests: () -> Unit = {},
    onSupport: () -> Unit = {},
    onSafety: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    selectedLanguage: String = "en",
    onLanguageSelected: (String) -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = DesignR.string.account_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        if (state.account != null) {
                            if (state.editing) {
                                TextButton(onClick = onCancel, enabled = !state.saving) { Text(stringResource(id = DesignR.string.action_cancel)) }
                            } else {
                                TextButton(onClick = onEdit, enabled = !state.loading) { Text(stringResource(id = DesignR.string.action_edit)) }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (state.account == null) {
                    if (state.loading) CircularProgressIndicator()
                    else {
                        Text(state.error ?: "Unable to load your account.", color = MaterialTheme.colorScheme.error)
                        FairSwapPrimaryButton(text = stringResource(id = DesignR.string.action_retry), onClick = onRetry)
                    }
                    return@Column
                }
                val account = state.account

                if (account.accountStatus == "suspended") {
                    FairSwapNotice(message = "Account Suspended: Marketplace actions are disabled.")
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (state.editing) {
                    FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                        ProfileEditor(state, onFormChange, onSave, onCancel)
                    }
                } else {
                    FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(id = DesignR.string.account_public_profile), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(id = DesignR.string.account_name, account.profile.displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val location = listOfNotNull(account.profile.city, account.profile.state).takeIf { it.isNotEmpty() }?.joinToString(", ") ?: stringResource(id = DesignR.string.account_not_set)
                            Text(stringResource(id = DesignR.string.account_location, location), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(id = DesignR.string.account_bio, account.profile.bio ?: stringResource(id = DesignR.string.account_no_bio)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                                FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_edit_public_profile), onClick = onEdit, enabled = !state.loading)
                                if (state.loading) CircularProgressIndicator()
                                else FairSwapOutlinedButton(text = stringResource(id = DesignR.string.action_refresh), onClick = onRetry)
                            }
                        }
                    }
                }

                // Security & Status
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(id = DesignR.string.account_security_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(id = DesignR.string.account_email, account.email ?: "—"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(id = DesignR.string.account_status, account.accountStatus), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        account.termsVersion?.let { Text(stringResource(id = DesignR.string.account_terms_version, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }

                // Language & Display Settings
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(id = DesignR.string.account_lang_region), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(id = DesignR.string.account_lang_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedLanguage == "en", onClick = { onLanguageSelected("en") })
                                Text("English", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedLanguage == "hi", onClick = { onLanguageSelected("hi") })
                                Text("हिन्दी (Hindi)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // My Listings
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = DesignR.string.account_my_listings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(id = DesignR.string.account_my_listings_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_view_listings), onClick = onMyListings)
                    }
                }

                // Messages
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = DesignR.string.account_messages), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(id = DesignR.string.account_messages_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_view_inbox), onClick = onMessages)
                    }
                }

                // Notifications
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = DesignR.string.account_notifications), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(id = DesignR.string.account_notifications_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_notification_center), onClick = onNotifications)
                    }
                }

                // Saved Favourites
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = DesignR.string.account_saved_favourites), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_view_favourites), onClick = onFavourites)
                    }
                }

                // Privacy & Safety
                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = DesignR.string.account_privacy_safety), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_blocked_users), onClick = onBlockedUsers)
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_privacy_requests), onClick = onPrivacyRequests)
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_support), onClick = onSupport)
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_safety_center), onClick = onSafety)
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_terms), onClick = onTerms)
                            FairSwapOutlinedButton(text = stringResource(id = DesignR.string.account_privacy_policy), onClick = onPrivacyPolicy)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FairSwapOutlinedButton(text = stringResource(id = DesignR.string.action_sign_out), onClick = onSignOut, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
            }
        }
    // }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(state: AccountUiState, onFormChange: (ProfileForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val form = state.form
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Public Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        
        FairSwapTextField(
            value = form.displayName,
            onValueChange = { onFormChange(form.copy(displayName = it.take(60))) },
            label = "Display Name *",
            supportingText = { Text("${form.displayName.length}/50 characters") },
            isError = state.validationError?.field == ProfileField.DISPLAY_NAME,
            singleLine = true,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth()
        )
        state.validationError?.takeIf { it.field == ProfileField.DISPLAY_NAME }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 140.dp)) {
                FairSwapTextField(
                    value = form.city,
                    onValueChange = { onFormChange(form.copy(city = it.take(120))) },
                    label = "City",
                    supportingText = { Text("${form.city.length}/100 characters") },
                    isError = state.validationError?.field == ProfileField.CITY,
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                )
                state.validationError?.takeIf { it.field == ProfileField.CITY }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            Column(modifier = Modifier.weight(1f).defaultMinSize(minWidth = 140.dp)) {
                FairSwapTextField(
                    value = form.state,
                    onValueChange = { onFormChange(form.copy(state = it.take(120))) },
                    label = "State",
                    supportingText = { Text("${form.state.length}/100 characters") },
                    isError = state.validationError?.field == ProfileField.STATE,
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                )
                state.validationError?.takeIf { it.field == ProfileField.STATE }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
        }
        
        FairSwapTextField(
            value = form.bio,
            onValueChange = { onFormChange(form.copy(bio = it.take(520))) },
            label = "About / Bio",
            supportingText = { Text("${form.bio.length}/500 characters") },
            isError = state.validationError?.field == ProfileField.BIO,
            enabled = !state.saving,
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        state.validationError?.takeIf { it.field == ProfileField.BIO }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FairSwapPrimaryButton(
                text = "Save Changes",
                onClick = onSave,
                isLoading = state.saving,
                enabled = !state.saving,
            )
            FairSwapOutlinedButton(
                text = "Cancel",
                onClick = onCancel,
                enabled = !state.saving,
            )
        }
    }
}
