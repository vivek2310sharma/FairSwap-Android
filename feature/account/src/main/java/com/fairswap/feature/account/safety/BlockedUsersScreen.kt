package com.fairswap.feature.account.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.components.FairSwapCard
import com.fairswap.core.designsystem.components.FairSwapNotice
import com.fairswap.core.designsystem.components.FairSwapOutlinedButton
import com.fairswap.core.designsystem.components.NoticeType
import com.fairswap.core.designsystem.theme.FairSwapWebTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    state: BlockedUsersUiState,
    onUnblock: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    FairSwapWebTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Blocked Users") },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("Back") }
                    },
                    actions = {
                        TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let {
                    FairSwapNotice(message = it, type = NoticeType.ERROR)
                }

                if (state.loading && state.users.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (!state.initialLoadDone && state.error != null) {
                    FairSwapOutlinedButton(text = "Retry Loading", onClick = onRefresh, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (state.initialLoadDone && state.users.isEmpty()) {
                    Text("You have no blocked users.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.users, key = { it }) { userId ->
                            val isUnblocking = userId in state.unblockingUserIds
                            FairSwapCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("User ID: $userId", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isUnblocking) {
                                        CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                                    } else {
                                        FairSwapOutlinedButton(
                                            text = "Unblock",
                                            onClick = { onUnblock(userId) },
                                            enabled = !state.loading
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
