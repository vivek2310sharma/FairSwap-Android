package com.fairswap.feature.account.safety

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.components.FairSwapCard
import com.fairswap.core.designsystem.components.FairSwapDestructiveButton
import com.fairswap.core.designsystem.components.FairSwapNotice
import com.fairswap.core.designsystem.components.FairSwapOutlinedButton
import com.fairswap.core.designsystem.components.FairSwapPrimaryButton
import com.fairswap.core.designsystem.components.NoticeType
import com.fairswap.core.designsystem.theme.FairSwapWebTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onExportData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(state.userId) {
        showDeleteConfirmation = false
    }

    FairSwapWebTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Privacy & Data Rights") },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("Back") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.error?.let { 
                    FairSwapNotice(message = it, type = NoticeType.ERROR)
                }
                state.successMessage?.let {
                    FairSwapNotice(message = it, type = NoticeType.SUCCESS)
                }

                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Export Account Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Request a portable copy of your account profile, listings, and message history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FairSwapPrimaryButton(
                            text = "Request Export",
                            onClick = onExportData,
                            isLoading = state.submitting,
                            enabled = !state.submitting
                        )
                    }
                }

                FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Delete Account & Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text("Request permanent closure of your account and anonymization of your public advertisements.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FairSwapDestructiveButton(
                            text = "Request Deletion",
                            onClick = { showDeleteConfirmation = true },
                            isLoading = state.submitting,
                            enabled = !state.submitting
                        )
                    }
                }
            }
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Request Account Deletion?") },
                text = { Text("This will request permanent closure of your account and anonymization of your public advertisements under platform policy.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirmation = false
                        onDeleteAccount()
                    }) {
                        Text("Confirm Deletion Request", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
