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

sealed interface DeleteAccountState {
    object Idle : DeleteAccountState
    object Loading : DeleteAccountState
    object Success : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
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

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState

    // Fires true after signup to prompt mode selection; consume via consumeModeSelection()
    private val _showModeSelection = MutableStateFlow(false)
    val showModeSelection: StateFlow<Boolean> = _showModeSelection

    // Fires true when Sensor mode is chosen; consume via consumeSensorGuide()
    private val _sensorGuideEvent = MutableStateFlow(false)
    val sensorGuideEvent: StateFlow<Boolean> = _sensorGuideEvent

    private val _currentUserRole = MutableStateFlow<UserRole?>(null)
    val currentUserRole: StateFlow<UserRole?> = _currentUserRole

    private val _spaceName = MutableStateFlow<String?>(null)
    val spaceName: StateFlow<String?> = _spaceName

    private val _spaceCapacity = MutableStateFlow<Int?>(null)
    val spaceCapacity: StateFlow<Int?> = _spaceCapacity

    fun signIn(username: String, password: String) {
        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            val result = repository.signIn(username.trim(), password)
            _signInState.value = if (result.isSuccess) SignInState.Success
            else SignInState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
        }
    }

    fun signUp(email: String, username: String, password: String) {
        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            val result = repository.signUp(email.trim(), username.trim(), password)
            if (result.isSuccess) {
                _showModeSelection.value = true
                _signUpState.value = SignUpState.Success
            } else {
                _signUpState.value = SignUpState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    fun setMode(role: UserRole) {
        val uid = repository.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.updateRole(uid, role)
            _currentUserRole.value = role
            if (role == UserRole.Sensor) _sensorGuideEvent.value = true
        }
    }

    fun loadUserRole() {
        val uid = repository.currentUser?.uid ?: return
        viewModelScope.launch {
            _currentUserRole.value = repository.getUserRole(uid)
        }
    }

    fun loadSpaceName() {
        val uid = repository.currentUser?.uid ?: return
        _spaceName.value = repository.getCachedSpaceName()
        _spaceCapacity.value = repository.getCachedSpaceCapacity()
        viewModelScope.launch {
            _spaceName.value = repository.loadSpaceName(uid)
            _spaceCapacity.value = repository.loadSpaceCapacity(uid)
        }
    }

    fun setSpaceName(name: String) {
        val uid = repository.currentUser?.uid ?: return
        _spaceName.value = name
        viewModelScope.launch { repository.saveSpaceName(uid, name) }
    }

    fun setSpaceCapacity(capacity: Int) {
        val uid = repository.currentUser?.uid ?: return
        _spaceCapacity.value = capacity
        viewModelScope.launch { repository.saveSpaceCapacity(uid, capacity) }
    }

    fun renameSpace(name: String) = setSpaceName(name)

    fun deleteAccount() {
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountState.Loading
            val result = repository.deleteAccount()
            _deleteAccountState.value = if (result.isSuccess) DeleteAccountState.Success
            else DeleteAccountState.Error(result.exceptionOrNull()?.message ?: "Failed to delete account")
        }
    }

    fun signOut() {
        _currentUserRole.value = null
        _spaceName.value = null
        _spaceCapacity.value = null
        repository.signOut()
    }

    fun resetSignInState() { _signInState.value = SignInState.Idle }
    fun resetSignUpState() { _signUpState.value = SignUpState.Idle }
    fun resetDeleteAccountState() { _deleteAccountState.value = DeleteAccountState.Idle }
    fun consumeModeSelection() { _showModeSelection.value = false }
    fun consumeSensorGuide() { _sensorGuideEvent.value = false }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(repository) as T
}
