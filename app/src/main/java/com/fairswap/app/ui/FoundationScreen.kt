package com.fairswap.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.network.BackendHealth

@Composable
fun FoundationScreen(
    uiState: FoundationUiState,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "FairSwap Android",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Android-0 foundation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Environment: ${uiState.environmentName}")
                    Text("Supabase: ${uiState.backendUrl.ifBlank { "not configured" }}")
                    Text(
                        if (uiState.isConfigured) {
                            "Publishable key: configured"
                        } else {
                            "Publishable key: missing (expected in local.properties or CI)"
                        }
                    )

                    when (val health = uiState.health) {
                        null -> Unit
                        BackendHealth.NotConfigured -> Text("Connection check skipped: staging key is missing.")
                        is BackendHealth.Reachable -> Text(
                            "FairSwapTest reachable under anon RLS" +
                                (health.sampleCategoryName?.let { ": $it" } ?: ".")
                        )
                        is BackendHealth.Unreachable -> Text("Connection failed: ${health.reason}")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onCheckConnection,
                enabled = !uiState.isChecking,
                modifier = Modifier.align(Alignment.Start),
            ) {
                if (uiState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Check staging connection")
                }
            }
        }
    }
}
