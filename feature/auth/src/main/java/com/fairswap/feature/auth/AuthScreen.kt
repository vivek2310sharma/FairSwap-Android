package com.fairswap.feature.auth

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.auth.LegalPolicy
import com.fairswap.core.auth.SignUpInput

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onBrowse: () -> Unit,
    onShowSignIn: () -> Unit,
    onShowSignUp: () -> Unit,
    onShowForgotPassword: () -> Unit,
    onSubmitEmail: (String) -> Unit = {},
    onChangeEmail: () -> Unit = {},
    onSwitchToPassword: () -> Unit = {},
    onSwitchToOtp: () -> Unit = {},
    onRequestOtp: () -> Unit = {},
    onVerifyOtp: (String) -> Unit = {},
    onSignIn: (String, String) -> Unit,
    onSignUp: (SignUpInput) -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onSetNewPassword: (String, String) -> Unit,
    onReacceptPolicies: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("FairSwap", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onBrowse) { Text("Browse marketplace") }
            Text(
                "FairSwap account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (uiState.isInitializing) {
                CircularProgressIndicator()
            } else when (val gate = uiState.gate) {
                AuthGate.SignedOut -> when (uiState.mode) {
                    AuthMode.SignIn -> when (uiState.signInStep) {
                        SignInStep.Email -> SignInEmailForm(
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            initialEmail = uiState.normalizedEmail,
                            onSubmit = onSubmitEmail,
                            onShowSignUp = onShowSignUp,
                        )
                        SignInStep.Password -> SignInPasswordForm(
                            email = uiState.normalizedEmail,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onChangeEmail = onChangeEmail,
                            onSubmit = onSignIn,
                            onSwitchToOtp = onSwitchToOtp,
                            onShowForgotPassword = onShowForgotPassword,
                        )
                        SignInStep.Otp -> SignInOtpForm(
                            email = uiState.normalizedEmail,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            otpAcknowledged = uiState.otpAcknowledged,
                            resendAvailableAt = uiState.resendAvailableAt,
                            onChangeEmail = onChangeEmail,
                            onSubmit = onVerifyOtp,
                            onResend = onRequestOtp,
                            onSwitchToPassword = onSwitchToPassword,
                        )
                    }
                    AuthMode.SignUp -> SignUpForm(
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onSubmit = onSignUp,
                        onShowSignIn = onShowSignIn,
                    )
                    AuthMode.ForgotPassword -> ForgotPasswordForm(
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onSubmit = onRequestPasswordReset,
                        onShowSignIn = onShowSignIn,
                    )
                }
                AuthGate.ResetEmailSent -> ResetEmailSent(onShowSignIn)
                AuthGate.RecoveryRequired -> SetNewPasswordForm(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onSubmit = onSetNewPassword,
                    onSignOut = onSignOut,
                )
                is AuthGate.ConfirmationRequired -> ConfirmationPending(
                    email = gate.email,
                    onBackToSignIn = onShowSignIn,
                )
                is AuthGate.PolicyReviewRequired -> PolicyReview(
                    displayName = gate.profile.displayName,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onAccept = onReacceptPolicies,
                    onSignOut = onSignOut,
                )
                is AuthGate.SignedIn -> Unit
                is AuthGate.Restricted -> RestrictedAccount(
                    status = gate.accountStatus,
                    onSignOut = onSignOut,
                )
                is AuthGate.Failure -> Unit
            }

            if (!uiState.isInitializing && uiState.error != null && (
                    uiState.gate is AuthGate.ConfirmationRequired ||
                    uiState.gate is AuthGate.ResetEmailSent ||
                    uiState.gate is AuthGate.Restricted
                )
            ) {
                Spacer(Modifier.height(12.dp))
                ErrorText(uiState.error!!)
            }

            if (uiState.isLoading && !uiState.isInitializing) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun SignInEmailForm(
    isLoading: Boolean,
    error: String?,
    initialEmail: String,
    onSubmit: (String) -> Unit,
    onShowSignUp: () -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sign in to FairSwap", style = MaterialTheme.typography.headlineSmall)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(email) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
        OutlinedButton(
            onClick = onShowSignUp,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}

@Composable
private fun SignInPasswordForm(
    email: String,
    isLoading: Boolean,
    error: String?,
    onChangeEmail: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onSwitchToOtp: () -> Unit,
    onShowForgotPassword: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sign in to FairSwap", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Email: $email", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeEmail, enabled = !isLoading) { Text("Change") }
        }
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password")
        }
        Button(
            onClick = { onSubmit(email, password) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in") }
        OutlinedButton(
            onClick = onSwitchToOtp,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Email me a one-time code instead") }
        OutlinedButton(
            onClick = onShowForgotPassword,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Forgot password?") }
    }
}

@Composable
private fun SignInOtpForm(
    email: String,
    isLoading: Boolean,
    error: String?,
    otpAcknowledged: Boolean,
    resendAvailableAt: Long,
    onChangeEmail: () -> Unit,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onSwitchToPassword: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resendAvailableAt) {
        while (System.currentTimeMillis() < resendAvailableAt) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val secondsLeft = kotlin.math.max(0, kotlin.math.ceil((resendAvailableAt - now) / 1000.0).toInt())

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sign in to FairSwap", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Email: $email", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeEmail, enabled = !isLoading) { Text("Change") }
        }
        if (otpAcknowledged) {
            Text("If this email is associated with an eligible account, you'll receive a sign-in code shortly.", color = androidx.compose.ui.graphics.Color(0xFF059669))
        }
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it.filter { ch -> ch in '0'..'9' }.take(6) },
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(otp) },
            enabled = !isLoading && otp.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Verify & Continue") }
        OutlinedButton(
            onClick = onSwitchToPassword,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in with password instead") }
        OutlinedButton(
            onClick = onResend,
            enabled = !isLoading && secondsLeft == 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (secondsLeft > 0) "Resend code (${secondsLeft}s)" else "Resend code") }
    }
}

@Composable
private fun ForgotPasswordForm(
    isLoading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onShowSignIn: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reset password", style = MaterialTheme.typography.headlineSmall)
        Text("Enter your email address to receive a reset link.")
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(email) }, enabled = !isLoading) { Text("Send reset link") }
        OutlinedButton(onClick = onShowSignIn, enabled = !isLoading) { Text("Back to sign in") }
    }
}

@Composable
private fun ResetEmailSent(onShowSignIn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check your email", style = MaterialTheme.typography.headlineSmall)
        Text("If an account exists for that address, a password reset link has been sent.")
        Button(onClick = onShowSignIn) { Text("Back to sign in") }
    }
}

@Composable
private fun SetNewPasswordForm(
    isLoading: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Set new password", style = MaterialTheme.typography.headlineSmall)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New password (min. 8 characters)") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = { Text("Confirm new password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password")
        }
        Button(onClick = { onSubmit(password, confirmation) }, enabled = !isLoading) {
            Text("Update password")
        }
        OutlinedButton(onClick = onSignOut, enabled = !isLoading) { Text("Cancel and sign out") }
    }
}

@Composable
private fun SignUpForm(
    isLoading: Boolean,
    error: String?,
    onSubmit: (SignUpInput) -> Unit,
    onShowSignIn: () -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var ageConfirmed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Create your FairSwap account", style = MaterialTheme.typography.headlineSmall)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min. 8 characters)") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password")
        }

        ConsentRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            text = "I accept the Terms of Service and Acceptable Use Policy, and acknowledge the Privacy Policy.",
        )
        ConsentRow(
            checked = ageConfirmed,
            onCheckedChange = { ageConfirmed = it },
            text = "I confirm that I am at least ${LegalPolicy.MinimumAge} years old.",
        )

        Button(
            onClick = {
                onSubmit(
                    SignUpInput(
                        displayName = displayName,
                        email = email,
                        password = password,
                        termsAccepted = termsAccepted,
                        ageConfirmed = ageConfirmed,
                    )
                )
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign up") }
        OutlinedButton(
            onClick = onShowSignIn,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Back to sign in") }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ConfirmationPending(email: String, onBackToSignIn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check your email", style = MaterialTheme.typography.headlineSmall)
        Text("We sent a confirmation link to $email. Confirm your email before signing in.")
        Button(onClick = onBackToSignIn) { Text("Back to sign in") }
    }
}

@Composable
private fun PolicyReview(
    displayName: String,
    isLoading: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onSignOut: () -> Unit,
) {
    var termsAccepted by remember { mutableStateOf(false) }
    var ageConfirmed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Policy review required", style = MaterialTheme.typography.headlineSmall)
        Text("Welcome back, $displayName. Review and accept the current Public Beta policies to continue.")
        error?.let { ErrorText(it) }
        ConsentRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            text = "I accept the Terms of Service and Acceptable Use Policy, and acknowledge the Privacy Policy.",
        )
        ConsentRow(
            checked = ageConfirmed,
            onCheckedChange = { ageConfirmed = it },
            text = "I confirm that I am at least ${LegalPolicy.MinimumAge} years old.",
        )
        Button(
            onClick = onAccept,
            enabled = termsAccepted && ageConfirmed && !isLoading,
        ) { Text("Accept and continue") }
        OutlinedButton(onClick = onSignOut, enabled = !isLoading) { Text("Sign out") }
    }
}

@Composable
private fun RestrictedAccount(status: String, onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account unavailable", style = MaterialTheme.typography.headlineSmall)
        Text("Current account status: $status")
        Button(onClick = onSignOut) { Text("Sign out") }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
