package com.fairswap.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.account.AccountLoadResult
import com.fairswap.core.account.AccountRepository
import com.fairswap.core.account.MyAccount
import com.fairswap.core.account.ProfileField
import com.fairswap.core.account.ProfileInput
import com.fairswap.core.account.ProfileUpdateResult
import com.fairswap.core.account.ProfileValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class ProfileForm(
    val displayName: String = "",
    val bio: String = "",
    val city: String = "",
    val state: String = "",
)

data class AccountUiState(
    val userId: String? = null,
    val account: MyAccount? = null,
    val form: ProfileForm = ProfileForm(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val editing: Boolean = false,
    val error: String? = null,
    val validationError: ProfileValidationError? = null,
)

sealed interface AccountEvent {
    data object AuthRequired : AccountEvent
    data object PolicyReviewRequired : AccountEvent
    data object Restricted : AccountEvent
}

@HiltViewModel
class AccountViewModel @Inject constructor(private val repository: AccountRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = Channel<AccountEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    private var requestJob: Job? = null
    private var generation = 0L

    fun onUserChanged(userId: String) {
        if (mutableState.value.userId == userId && (mutableState.value.loading || mutableState.value.account != null)) return
        generation++
        val request = generation
        requestJob?.cancel()
        mutableState.value = AccountUiState(userId = userId, loading = true)
        requestJob = viewModelScope.launch {
            try {
                when (val result = repository.load()) {
                    is AccountLoadResult.Success -> if (request == generation) {
                        if (result.account.userId == userId) mutableState.value = AccountUiState(userId = userId, account = result.account, form = result.account.toForm())
                        else mutableEvents.send(AccountEvent.AuthRequired)
                    }
                    AccountLoadResult.AuthRequired -> if (request == generation) mutableEvents.send(AccountEvent.AuthRequired)
                    AccountLoadResult.Restricted -> if (request == generation) mutableEvents.send(AccountEvent.Restricted)
                    AccountLoadResult.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, error = "Unable to load your account. Retry.") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) mutableState.update { it.copy(loading = false, error = "Unable to load your account. Retry.") }
            } finally {
                if (request == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun refresh() {
        val userId = mutableState.value.userId ?: return
        if (mutableState.value.loading || mutableState.value.saving) return
        val previous = mutableState.value
        val request = ++generation
        requestJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        requestJob = viewModelScope.launch {
            try {
                when (val result = repository.load()) {
                    is AccountLoadResult.Success -> if (request == generation) {
                        if (result.account.userId == userId) mutableState.value = AccountUiState(userId = userId, account = result.account, form = result.account.toForm())
                        else mutableEvents.send(AccountEvent.AuthRequired)
                    }
                    AccountLoadResult.AuthRequired -> if (request == generation) mutableEvents.send(AccountEvent.AuthRequired)
                    AccountLoadResult.Restricted -> if (request == generation) mutableEvents.send(AccountEvent.Restricted)
                    AccountLoadResult.Failure -> if (request == generation) mutableState.value = previous.copy(error = "Unable to refresh your account. Retry.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) mutableState.value = previous.copy(error = "Unable to refresh your account. Retry.")
            } finally {
                if (request == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun retry() {
        if (mutableState.value.account == null) onUserChanged(mutableState.value.userId ?: return) else refresh()
    }

    fun beginEditing() {
        mutableState.update { current -> current.account?.let { current.copy(editing = true, form = it.toForm(), error = null, validationError = null) } ?: current }
    }

    fun cancelEditing() {
        mutableState.update { current -> current.copy(editing = false, form = current.account?.toForm() ?: ProfileForm(), error = null, validationError = null) }
    }

    fun updateForm(form: ProfileForm) { mutableState.update { it.copy(form = form, validationError = null, error = null) } }

    fun save() {
        val snapshot = mutableState.value
        if (!snapshot.editing || snapshot.saving || snapshot.account == null) return
        val request = generation
        mutableState.update { it.copy(saving = true, error = null, validationError = null) }
        requestJob = viewModelScope.launch {
            try {
                when (val result = repository.update(snapshot.form.toInput())) {
                    is ProfileUpdateResult.Success -> if (request == generation && result.account.userId == snapshot.userId) {
                        mutableState.value = AccountUiState(userId = snapshot.userId, account = result.account, form = result.account.toForm())
                    }
                    is ProfileUpdateResult.Invalid -> if (request == generation) mutableState.update { it.copy(saving = false, validationError = result.error) }
                    ProfileUpdateResult.AuthRequired -> if (request == generation) mutableEvents.send(AccountEvent.AuthRequired)
                    ProfileUpdateResult.PolicyReviewRequired -> if (request == generation) mutableEvents.send(AccountEvent.PolicyReviewRequired)
                    ProfileUpdateResult.Restricted -> if (request == generation) mutableEvents.send(AccountEvent.Restricted)
                    ProfileUpdateResult.Failure -> if (request == generation) mutableState.update { it.copy(saving = false, error = "Unable to save your profile. Retry.") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) mutableState.update { it.copy(saving = false, error = "Unable to save your profile. Retry.") }
            } finally {
                if (request == generation) mutableState.update { it.copy(saving = false) }
            }
        }
    }

    private fun MyAccount.toForm() = ProfileForm(profile.displayName, profile.bio.orEmpty(), profile.city.orEmpty(), profile.state.orEmpty())
    private fun ProfileForm.toInput() = ProfileInput(displayName, bio, city, state)
}
