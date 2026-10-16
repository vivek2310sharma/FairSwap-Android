package com.fairswap.feature.account.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.components.FairSwapCard
import com.fairswap.core.designsystem.components.FairSwapNotice
import com.fairswap.core.designsystem.components.FairSwapPrimaryButton
import com.fairswap.core.designsystem.components.FairSwapTextField
import com.fairswap.core.designsystem.components.NoticeType
import com.fairswap.core.designsystem.theme.FairSwapWebTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    state: ReportUiState,
    onReasonChange: (String) -> Unit,
    onDetailsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    FairSwapWebTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Report ${state.targetType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""}") },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("Cancel") }
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
                if (state.success) {
                    FairSwapNotice(message = "Report submitted successfully. Thank you.", type = NoticeType.SUCCESS)
                    FairSwapPrimaryButton(text = "Go Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    return@Column
                }

                state.error?.let { 
                    FairSwapNotice(message = it, type = NoticeType.ERROR)
                }

                if (state.targetType != null) {
                    FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Submit a Report", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Please provide details so we can investigate.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            FairSwapTextField(
                                value = state.reason,
                                onValueChange = onReasonChange,
                                label = "Reason",
                                enabled = !state.submitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            FairSwapTextField(
                                value = state.details,
                                onValueChange = onDetailsChange,
                                label = "Additional Details (Optional)",
                                enabled = !state.submitting,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            FairSwapPrimaryButton(
                                text = "Submit Report",
                                onClick = onSubmit,
                                isLoading = state.submitting,
                                enabled = !state.submitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
