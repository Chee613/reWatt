package com.rewatt.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.rewatt.app.data.AuthRepository
import com.rewatt.app.data.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    object Loading : AuthState
    object Unauthenticated : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
}

sealed interface SignInState {
    object Idle : SignInState
    object Loading : SignInState
    object Success : SignInState
    data class Error(val message: String) : SignInState
}

sealed interface SignUpState {
    object Idle : SignUpState
    object Loading : SignUpState
    object Success : SignUpState
    data class Error(val message: String) : SignUpState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authStateFlow
        .map { user ->
            if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState

    fun signIn(username: String, password: String) {
        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            val result = repository.signIn(username.trim(), password)
            _signInState.value = if (result.isSuccess) SignInState.Success
            else SignInState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
        }
    }

    fun signUp(email: String, username: String, password: String, role: UserRole) {
        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            val result = repository.signUp(email.trim(), username.trim(), password, role)
            _signUpState.value = if (result.isSuccess) SignUpState.Success
            else SignUpState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
        }
    }

    fun signOut() = repository.signOut()

    fun resetSignInState() { _signInState.value = SignInState.Idle }
    fun resetSignUpState() { _signUpState.value = SignUpState.Idle }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(repository) as T
}
