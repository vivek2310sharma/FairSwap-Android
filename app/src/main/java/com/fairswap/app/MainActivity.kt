package com.fairswap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fairswap.app.ui.FairSwapApp
import com.fairswap.core.designsystem.theme.FairSwapTheme
import com.fairswap.core.auth.AuthCallbackConfig
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.auth.AuthCallbackKind
import com.fairswap.core.auth.CallbackClassification
import com.fairswap.core.auth.RecoveryStateStore
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var callbackConfig: AuthCallbackConfig
    @Inject lateinit var callbackCoordinator: AuthCallbackCoordinator
    @Inject lateinit var recoveryState: RecoveryStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FairSwapTheme {
                FairSwapApp()
            }
        }
        handleAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(incoming: Intent) {
        if (incoming.action != Intent.ACTION_VIEW || incoming.data == null) return
        if (incoming.data?.scheme != callbackConfig.scheme || incoming.data?.host != callbackConfig.host) return
        val classification = callbackConfig.classify(incoming.dataString)
        // Remove the callback from the Activity intent so recreation cannot exchange its code twice.
        setIntent(Intent(incoming).apply { data = null })
        val sequence = callbackCoordinator.begin() ?: return
        when (classification) {
            CallbackClassification.Invalid -> callbackCoordinator.fail(sequence)
            is CallbackClassification.Valid -> if (
                classification.kind != recoveryState.expectedCallbackKind
            ) {
                callbackCoordinator.fail(sequence)
            } else supabase.handleDeeplinks(
                incoming,
                onSessionSuccess = {
                    callbackCoordinator.succeed(sequence, classification.kind) {
                        recoveryState.expectedCallbackKind = null
                        if (classification.kind == AuthCallbackKind.Recovery) recoveryState.isPending = true
                    }
                },
                onError = { callbackCoordinator.fail(sequence) },
            )
        }
    }
}
