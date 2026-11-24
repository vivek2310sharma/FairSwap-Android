package com.fairswap.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.auth.LegalPolicy
import com.fairswap.core.auth.SignUpInput
import com.fairswap.core.designsystem.R as DesignR
import com.fairswap.core.designsystem.components.FairSwapWordmark

private val FairSwapGreen = Color(0xFF008C45)
private val FairSwapMint = Color(0xFFE8F9E8)
private val Ink = Color(0xFF0C1D43)
private val MutedInk = Color(0xFF607089)

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
    onTerms: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    onSupport: () -> Unit = {},
    selectedLanguage: String = "en",
    onLanguageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Branding Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FairSwapWordmark()
                Text(
                    text = "Give. Get. Grow Together.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedInk,
                    fontWeight = FontWeight.Medium
                )
            }

            // Prominent Browse Marketplace Button
            Surface(
                onClick = onBrowse,
                shape = RoundedCornerShape(16.dp),
                color = FairSwapMint,
                border = BorderStroke(1.dp, Color(0xFFD5F2D7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = FairSwapGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = DesignR.string.auth_browse_marketplace),
                        color = FairSwapGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Community Handshake Illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(com.fairswap.feature.discovery.R.drawable.discovery_handshake_hero),
                    contentDescription = "Community members greeting each other",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Welcome Message & Dynamic Forms
            if (uiState.isInitializing) {
                CircularProgressIndicator(color = FairSwapGreen)
            } else when (val gate = uiState.gate) {
                AuthGate.SignedOut -> {
                    when (uiState.mode) {
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

                    // Language Selector
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            text = stringResource(id = DesignR.string.auth_language_picker),
                            style = MaterialTheme.typography.titleSmall,
                            color = MutedInk,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedLanguage == "en",
                                    onClick = { onLanguageSelected("en") }
                                )
                                Text(
                                    stringResource(id = DesignR.string.auth_english),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedLanguage == "hi",
                                    onClick = { onLanguageSelected("hi") }
                                )
                                Text(
                                    stringResource(id = DesignR.string.auth_hindi),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink
                                )
                            }
                        }
                    }

                    // Footer Links
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        TextButton(onClick = onSupport) { Text(stringResource(id = DesignR.string.auth_help_center), color = MutedInk, fontSize = 12.sp) }
                        TextButton(onClick = onTerms) { Text(stringResource(id = DesignR.string.auth_terms), color = MutedInk, fontSize = 12.sp) }
                        TextButton(onClick = onPrivacyPolicy) { Text(stringResource(id = DesignR.string.auth_privacy), color = MutedInk, fontSize = 12.sp) }
                    }
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
                ErrorText(uiState.error)
            }

            if (uiState.isLoading && !uiState.isInitializing) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = FairSwapGreen)
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = DesignR.string.auth_join_community),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(id = DesignR.string.auth_welcome_desc),
            fontSize = 12.sp,
            color = MutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(id = DesignR.string.auth_email_label)) },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = FairSwapGreen) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(email) },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                stringResource(id = DesignR.string.auth_continue),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        OutlinedButton(
            onClick = onShowSignUp,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, FairSwapGreen),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                stringResource(id = DesignR.string.auth_sign_up),
                color = FairSwapGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
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
        Text("Sign in with password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Email: $email", style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeEmail, enabled = !isLoading) { Text("Change", color = FairSwapGreen) }
        }
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(id = DesignR.string.auth_password_label)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password", color = FairSwapGreen)
        }
        Button(
            onClick = { onSubmit(email, password) },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(id = DesignR.string.auth_sign_in), fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onSwitchToOtp,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, FairSwapGreen),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Email me a one-time code instead", color = FairSwapGreen) }
        OutlinedButton(
            onClick = onShowForgotPassword,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
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
        Text("Enter 6-digit code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Email: $email", style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.weight(1f))
            TextButton(onClick = onChangeEmail, enabled = !isLoading) { Text("Change", color = FairSwapGreen) }
        }
        if (otpAcknowledged) {
            Text("If this email is associated with an eligible account, you'll receive a sign-in code shortly.", color = Color(0xFF059669), fontSize = 12.sp)
        }
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it.filter { ch -> ch in '0'..'9' }.take(6) },
            label = { Text("6-digit code") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(otp) },
            enabled = !isLoading && otp.length == 6,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Verify & Continue", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onSwitchToPassword,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, FairSwapGreen),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in with password instead", color = FairSwapGreen) }
        OutlinedButton(
            onClick = onResend,
            enabled = !isLoading && secondsLeft == 0,
            shape = RoundedCornerShape(16.dp),
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
        Text("Reset password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text("Enter your email address to receive a reset link.", color = MutedInk, fontSize = 12.sp)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(id = DesignR.string.auth_email_label)) },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = FairSwapGreen) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(email) },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Send reset link", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onShowSignIn,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(id = DesignR.string.auth_back_to_sign_in)) }
    }
}

@Composable
private fun ResetEmailSent(onShowSignIn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(id = DesignR.string.auth_check_email_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text("If an account exists for that address, a password reset link has been sent.", color = MutedInk, fontSize = 12.sp)
        Button(
            onClick = onShowSignIn,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(id = DesignR.string.auth_back_to_sign_in), fontWeight = FontWeight.Bold) }
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
        Text("Set new password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New password (min. 8 characters)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = { Text("Confirm new password") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password", color = FairSwapGreen)
        }
        Button(
            onClick = { onSubmit(password, confirmation) },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Update password", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onSignOut, enabled = !isLoading, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text("Cancel and sign out") }
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
        Text("Create your FairSwap account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        error?.let { ErrorText(it) }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(id = DesignR.string.auth_email_label)) },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = FairSwapGreen) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min. 8 characters)") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.align(Alignment.End)) {
            Text(if (passwordVisible) "Hide password" else "Show password", color = FairSwapGreen)
        }

        ConsentRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            text = stringResource(id = DesignR.string.auth_terms_accept),
        )
        ConsentRow(
            checked = ageConfirmed,
            onCheckedChange = { ageConfirmed = it },
            text = stringResource(id = DesignR.string.auth_age_accept, LegalPolicy.MinimumAge),
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
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(id = DesignR.string.auth_sign_up), fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onShowSignIn,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, FairSwapGreen),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(id = DesignR.string.auth_back_to_sign_in), color = FairSwapGreen) }
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
            color = MutedInk,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ConfirmationPending(email: String, onBackToSignIn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(id = DesignR.string.auth_check_email_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(stringResource(id = DesignR.string.auth_check_email_desc, email), color = MutedInk)
        Button(
            onClick = onBackToSignIn,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(id = DesignR.string.auth_back_to_sign_in), fontWeight = FontWeight.Bold) }
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
        Text(stringResource(id = DesignR.string.auth_policy_review_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(stringResource(id = DesignR.string.auth_policy_review_desc, displayName), color = MutedInk)
        error?.let { ErrorText(it) }
        ConsentRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            text = stringResource(id = DesignR.string.auth_terms_accept),
        )
        ConsentRow(
            checked = ageConfirmed,
            onCheckedChange = { ageConfirmed = it },
            text = stringResource(id = DesignR.string.auth_age_accept, LegalPolicy.MinimumAge),
        )
        Button(
            onClick = onAccept,
            enabled = termsAccepted && ageConfirmed && !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(id = DesignR.string.auth_accept_continue), fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onSignOut, enabled = !isLoading, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(stringResource(id = DesignR.string.action_sign_out)) }
    }
}

@Composable
private fun RestrictedAccount(status: String, onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(id = DesignR.string.auth_account_unavailable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(stringResource(id = DesignR.string.auth_status, status), color = MutedInk)
        Button(
            onClick = onSignOut,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FairSwapGreen, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(stringResource(id = DesignR.string.action_sign_out), fontWeight = FontWeight.Bold) }
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
