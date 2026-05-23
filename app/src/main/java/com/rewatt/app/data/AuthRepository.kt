package com.rewatt.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class UserRole(val label: String) {
    Admin("Sign Up as Admin"),
    Sensor("Sign Up as Sensor"),
}

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUp(
        email: String,
        username: String,
        password: String,
        role: UserRole,
    ): Result<FirebaseUser> = suspendCoroutine { cont ->
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user!!
                // Set displayName = username so Profile shows it immediately
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(username).build()
                )
                // Store username → email mapping for username-based login
                val usernameDoc = mapOf("email" to email, "uid" to user.uid)
                val userDoc = mapOf(
                    "username" to username,
                    "email" to email,
                    "role" to role.name,
                    "uid" to user.uid,
                )
                firestore.collection("rewatt_usernames").document(username).set(usernameDoc)
                firestore.collection("rewatt_users").document(user.uid).set(userDoc)
                cont.resume(Result.success(user))
            }
            .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
    }

    // Looks up email by username in Firestore, then signs in with Firebase Auth
    suspend fun signIn(username: String, password: String): Result<FirebaseUser> =
        suspendCoroutine { cont ->
            firestore.collection("rewatt_usernames").document(username).get()
                .addOnSuccessListener { doc ->
                    val email = doc.getString("email")
                    if (email == null) {
                        cont.resume(Result.failure(Exception("Username not found")))
                        return@addOnSuccessListener
                    }
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result -> cont.resume(Result.success(result.user!!)) }
                        .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
                }
                .addOnFailureListener { cont.resume(Result.failure(Exception("Username not found"))) }
        }

    fun signOut() = auth.signOut()
}
