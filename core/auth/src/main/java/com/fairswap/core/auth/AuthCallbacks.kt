package com.fairswap.core.auth

import android.content.Context
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AuthCallbackKind { Confirmation, Recovery }

sealed interface CallbackClassification {
    data class Valid(val kind: AuthCallbackKind) : CallbackClassification
    data object Invalid : CallbackClassification
}

data class AuthCallbackConfig(val scheme: String, val host: String) {
    val confirmationUrl: String get() = "$scheme://$host/confirm"
    val recoveryUrl: String get() = "$scheme://$host/recovery"

    fun classify(rawUri: String?): CallbackClassification {
        val uri = runCatching { URI(rawUri ?: return CallbackClassification.Invalid) }
            .getOrNull() ?: return CallbackClassification.Invalid
        if (uri.scheme != scheme || uri.host != host || uri.fragment != null ||
            uri.port != -1 || uri.userInfo != null) {
            return CallbackClassification.Invalid
        }
        val kind = when (uri.rawPath) {
            "/confirm" -> AuthCallbackKind.Confirmation
            "/recovery" -> AuthCallbackKind.Recovery
            else -> return CallbackClassification.Invalid
        }
        val parameters = uri.rawQuery?.split('&').orEmpty().map { it.substringBefore('=') }
        if (parameters.count { it == "code" } != 1 || parameters.any {
                it == "error" || it == "error_code" || it == "error_description"
            }) {
            return CallbackClassification.Invalid
        }
        val code = uri.rawQuery?.split('&')?.singleOrNull { it.substringBefore('=') == "code" }
            ?.substringAfter('=', "")
        if (code.isNullOrBlank()) return CallbackClassification.Invalid
        return CallbackClassification.Valid(kind)
    }
}

sealed interface AuthCallbackEvent {
    val sequence: Long

    data class Processing(override val sequence: Long) : AuthCallbackEvent
    data class Succeeded(override val sequence: Long, val kind: AuthCallbackKind) : AuthCallbackEvent
    data class Failed(override val sequence: Long) : AuthCallbackEvent
}

class AuthCallbackCoordinator {
    private val mutableEvents = MutableStateFlow<AuthCallbackEvent?>(null)
    val events: StateFlow<AuthCallbackEvent?> = mutableEvents
    private val mutablePendingNavigation = MutableStateFlow<Long?>(null)
    val pendingNavigation: StateFlow<Long?> = mutablePendingNavigation
    private var nextSequence = 0L

    fun begin(): Long? = synchronized(this) {
        if (mutableEvents.value is AuthCallbackEvent.Processing) return@synchronized null
        (++nextSequence).also {
            mutableEvents.value = AuthCallbackEvent.Processing(it)
            mutablePendingNavigation.value = it
        }
    }

    fun succeed(sequence: Long, kind: AuthCallbackKind, onAccepted: () -> Unit = {}): Boolean = synchronized(this) {
        if (mutableEvents.value != AuthCallbackEvent.Processing(sequence)) return@synchronized false
        onAccepted()
        mutableEvents.value = AuthCallbackEvent.Succeeded(sequence, kind)
        true
    }

    fun fail(sequence: Long) = synchronized(this) {
        if (mutableEvents.value == AuthCallbackEvent.Processing(sequence)) {
            mutableEvents.value = AuthCallbackEvent.Failed(sequence)
        }
    }

    fun acknowledge(sequence: Long) = synchronized(this) {
        if (mutableEvents.value?.sequence == sequence) mutableEvents.value = null
    }

    fun acknowledgeNavigation(sequence: Long) = synchronized(this) {
        if (mutablePendingNavigation.value == sequence) mutablePendingNavigation.value = null
    }
}

class RecoveryStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_recovery", Context.MODE_PRIVATE)

    var isPending: Boolean
        get() = preferences.getBoolean("pending", false)
        set(value) { preferences.edit().putBoolean("pending", value).commit() }

    var expectedCallbackKind: AuthCallbackKind?
        get() = preferences.getString("expected_callback", null)?.let {
            runCatching { AuthCallbackKind.valueOf(it) }.getOrNull()
        }
        set(value) { preferences.edit().putString("expected_callback", value?.name).commit() }
}
