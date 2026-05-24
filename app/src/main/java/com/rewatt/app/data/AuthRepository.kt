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
    ): Result<FirebaseUser> {
        // Step 1: Reject duplicate usernames before touching Firebase Auth
        val usernameTaken = suspendCoroutine<Boolean> { cont ->
            firestore.collection("rewatt_usernames").document(username).get()
                .addOnSuccessListener { doc -> cont.resume(doc.exists()) }
                .addOnFailureListener { cont.resume(false) }
        }
        if (usernameTaken) {
            return Result.failure(Exception("Username is already taken. Please choose a different one."))
        }

        // Step 2: Create the Firebase Auth account and write Firestore docs (role chosen separately)
        return suspendCoroutine { cont ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user!!
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(username).build()
                    )
                    val usernameDoc = mapOf("email" to email, "uid" to user.uid)
                    val userDoc = mapOf(
                        "username" to username,
                        "email" to email,
                        "uid" to user.uid,
                    )
                    firestore.collection("rewatt_usernames").document(username).set(usernameDoc)
                    firestore.collection("rewatt_users").document(user.uid).set(userDoc)
                    cont.resume(Result.success(user))
                }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
    }

    suspend fun getUserRole(uid: String): UserRole? = suspendCoroutine { cont ->
        firestore.collection("rewatt_users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("role")
                cont.resume(name?.let { runCatching { UserRole.valueOf(it) }.getOrNull() })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun updateRole(uid: String, role: UserRole): Result<Unit> = suspendCoroutine { cont ->
        firestore.collection("rewatt_users").document(uid)
            .update("role", role.name)
            .addOnSuccessListener { cont.resume(Result.success(Unit)) }
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

    suspend fun deleteAccount(): Result<Unit> = suspendCoroutine { cont ->
        val user = auth.currentUser
        if (user == null) {
            cont.resume(Result.failure(Exception("No user signed in")))
            return@suspendCoroutine
        }
        val uid = user.uid
        firestore.collection("rewatt_users").document(uid).get()
            .addOnSuccessListener { doc ->
                val username = doc.getString("username")
                user.delete()
                    .addOnSuccessListener {
                        firestore.collection("rewatt_users").document(uid).delete()
                        if (username != null) {
                            firestore.collection("rewatt_usernames").document(username).delete()
                        }
                        cont.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
            }
            .addOnFailureListener {
                user.delete()
                    .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                    .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
            }
    }

    fun signOut() = auth.signOut()
}
