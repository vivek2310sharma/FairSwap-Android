package com.fairswap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.fairswap.app.ui.FairSwapApp
import com.fairswap.app.ui.LocaleHelper
import com.fairswap.core.auth.AuthCallbackConfig
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.auth.AuthCallbackKind
import com.fairswap.core.auth.CallbackClassification
import com.fairswap.core.auth.RecoveryStateStore
import com.fairswap.core.communication.CommunicationSessionOwner
import com.fairswap.core.designsystem.theme.FairSwapTheme
import com.fairswap.core.favourites.FavouriteStateStore
import com.fairswap.core.favourites.FavouritesRepository
import com.fairswap.core.sellerlistings.SellerListingRepository
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var callbackConfig: AuthCallbackConfig
    @Inject lateinit var callbackCoordinator: AuthCallbackCoordinator
    @Inject lateinit var recoveryState: RecoveryStateStore
    @Inject lateinit var favouritesRepository: FavouritesRepository
    @Inject lateinit var favouriteStateStore: FavouriteStateStore
    @Inject lateinit var sellerRepository: SellerListingRepository
    @Inject lateinit var communicationSessionOwner: CommunicationSessionOwner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var currentLanguage by remember { mutableStateOf(LocaleHelper.getSelectedLanguage(this)) }
            val context = LocalContext.current
            val localizedContext = remember(currentLanguage) { LocaleHelper.applyLocale(context, currentLanguage) }
            val localizedConfiguration = remember(currentLanguage) { localizedContext.resources.configuration }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                FairSwapTheme {
                    FairSwapApp(
                        callbacks = callbackCoordinator,
                        favouritesRepository = favouritesRepository,
                        favouriteStore = favouriteStateStore,
                        sellerRepository = sellerRepository,
                        communicationSessionOwner = communicationSessionOwner,
                        supabase = supabase,
                        selectedLanguage = currentLanguage,
                        onLanguageSelected = { lang ->
                            LocaleHelper.setSelectedLanguage(this, lang)
                            currentLanguage = lang
                        }
                    )
                }
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
