package com.rewatt.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewatt.app.R
import com.rewatt.app.data.UserRole

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
) {
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.Admin) }
    val isLoading = signUpState is SignUpState.Loading
    val passwordMismatch = password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.rewatt_logo),
            contentDescription = "ReWatt logo",
            modifier = Modifier
                .size(96.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(16.dp))
        Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text(
            "Join ReWatt",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            isError = passwordMismatch,
            supportingText = if (passwordMismatch) {
                { Text("Passwords do not match") }
            } else {
                null
            },
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Select Role",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserRole.values().forEach { role ->
                if (role == selectedRole) {
                    Button(
                        onClick = { selectedRole = role },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                    ) {
                        Text(role.label, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedRole = role },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                    ) {
                        Text(role.label, fontSize = 13.sp)
                    }
                }
            }
        }

        if (signUpState is SignUpState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = (signUpState as SignUpState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.signUp(email, username, password, selectedRole) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading &&
                email.isNotBlank() &&
                username.isNotBlank() &&
                password.isNotBlank() &&
                confirmPassword.isNotBlank() &&
                !passwordMismatch,
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Create Account")
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToLogin, enabled = !isLoading) {
            Text("Already have an account? Sign In")
        }
    }

    LaunchedEffect(signUpState) {
        if (signUpState is SignUpState.Success) viewModel.resetSignUpState()
    }
}
