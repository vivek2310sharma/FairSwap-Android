package com.fairswap.feature.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fairswap.core.designsystem.R as DesignR
import com.fairswap.core.communication.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: ThreadUiState,
    onVisible: () -> Unit,
    onNotVisible: () -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    onReportUser: (String) -> Unit = {},
    onBlockUser: (String) -> Unit = {},
    blockActionPending: Boolean = false,
    blockActionError: String? = null,
    blockActionSuccess: String? = null,
    onClearBlockError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LifecycleStartEffect(Unit) {
        onVisible()
        onStopOrDispose {
            onNotVisible()
        }
    }

    val listState = rememberLazyListState()
    var showBlockDialog by remember { mutableStateOf(false) }
    var targetBlockUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.summary?.listingTitle ?: stringResource(id = DesignR.string.chats_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.summary?.counterpartyName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(id = DesignR.string.action_back)) }
                },
                actions = {
                    val counterpartyId = state.summary?.counterpartyId
                    if (counterpartyId != null) {
                        TextButton(onClick = { onReportUser(counterpartyId) }) { Text(stringResource(id = DesignR.string.listing_detail_report)) }
                        TextButton(
                            onClick = { 
                                targetBlockUserId = counterpartyId
                                showBlockDialog = true 
                            },
                            enabled = !blockActionPending
                        ) { 
                            if (blockActionPending) {
                                CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                            } else {
                                Text(stringResource(id = DesignR.string.seller_profile_block_confirm)) 
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            blockActionError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearBlockError) { Text("Dismiss") }
                    }
                }
            }
            state.messagingUnavailableError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (state.isLoadingInitial && state.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    if (state.canLoadOlder) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoadingOlder) {
                                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                                } else {
                                    TextButton(onClick = onLoadOlder) {
                                        Text("Load older messages")
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = state.messages,
                        key = { it.id },
                    ) { msg ->
                        val isMine = msg.senderId == state.boundSession?.userId
                        ChatBubbleItem(
                            message = msg,
                            isMine = isMine,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Composer area
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    state.sendError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.composerText,
                            onValueChange = { if (it.length <= 2000) onComposerTextChanged(it) },
                            placeholder = { Text(stringResource(id = DesignR.string.chats_type_message)) },
                            enabled = !state.isSending && state.messagingUnavailableError == null,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSend,
                            enabled = !state.isSending &&
                                    state.composerText.trim().isNotEmpty() &&
                                    state.messagingUnavailableError == null,
                        ) {
                            if (state.isSending) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.height(16.dp),
                                )
                            } else {
                                Text(if (state.sendError != null) "Retry" else "Send")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBlockDialog && targetBlockUserId != null) {
        AlertDialog(
            onDismissRequest = {
                showBlockDialog = false
                targetBlockUserId = null
            },
            title = { Text("Block User?") },
            text = { Text("They will no longer be able to send you messages or start new inquiries. You can unblock them anytime in Blocked Users.") },
            confirmButton = {
                TextButton(onClick = {
                    val userId = targetBlockUserId
                    showBlockDialog = false
                    targetBlockUserId = null
                    if (userId != null) {
                        onBlockUser(userId)
                    }
                }) {
                    Text(stringResource(id = DesignR.string.seller_profile_block_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    targetBlockUserId = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isMine) 12.dp else 2.dp,
                        bottomEnd = if (isMine) 2.dp else 12.dp,
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = CommunicationTimeFormatter.formatShortTime(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
