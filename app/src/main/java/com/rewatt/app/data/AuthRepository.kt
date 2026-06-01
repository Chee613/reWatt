package com.rewatt.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class UserRole(val label: String) {
    Admin("Sign Up as Admin"),
    Sensor("Sign Up as Sensor"),
}

class AuthRepository(private val context: Context) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Local username→email cache so sign-in works even when Firestore is
    // inaccessible (e.g. security rules block unauthenticated reads).
    private val prefs by lazy { context.getSharedPreferences("rewatt_auth_cache", Context.MODE_PRIVATE) }

    private val spacePrefs by lazy { context.getSharedPreferences("rewatt_space_config", Context.MODE_PRIVATE) }

    fun getOrCreateDeviceId(): String {
        val key = "device_id"
        return spacePrefs.getString(key, null) ?: UUID.randomUUID().toString().also {
            spacePrefs.edit().putString(key, it).apply()
        }
    }

    fun getCachedSpaceName(): String? = spacePrefs.getString("space_name", null)

    fun cacheSpaceName(name: String) = spacePrefs.edit().putString("space_name", name).apply()

    suspend fun saveSpaceName(uid: String, name: String): Result<Unit> {
        cacheSpaceName(name)
        val deviceId = getOrCreateDeviceId()
        return suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("devices").document(deviceId)
                .set(mapOf("spaceName" to name, "createdAt" to System.currentTimeMillis()), SetOptions.merge())
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
    }

    suspend fun loadSpaceName(uid: String): String? {
        val cached = getCachedSpaceName()
        if (cached != null) return cached
        val deviceId = getOrCreateDeviceId()
        return suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("devices").document(deviceId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("spaceName")
                    if (name != null) cacheSpaceName(name)
                    cont.resume(name)
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /** Room seating capacity (e.g. 300 for "DK1, 300 pax"). Null until the user sets it. */
    fun getCachedSpaceCapacity(): Int? =
        spacePrefs.getInt("space_capacity", -1).takeIf { it > 0 }

    fun cacheSpaceCapacity(capacity: Int) =
        spacePrefs.edit().putInt("space_capacity", capacity).apply()

    suspend fun saveSpaceCapacity(uid: String, capacity: Int): Result<Unit> {
        cacheSpaceCapacity(capacity)
        val deviceId = getOrCreateDeviceId()
        return suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("devices").document(deviceId)
                .set(mapOf("capacity" to capacity), SetOptions.merge())
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
    }

    suspend fun loadSpaceCapacity(uid: String): Int? {
        val cached = getCachedSpaceCapacity()
        if (cached != null) return cached
        val deviceId = getOrCreateDeviceId()
        return suspendCoroutine { cont ->
            firestore.collection("rewatt_users").document(uid)
                .collection("devices").document(deviceId).get()
                .addOnSuccessListener { doc ->
                    val capacity = doc.getLong("capacity")?.toInt()
                    if (capacity != null) cacheSpaceCapacity(capacity)
                    cont.resume(capacity)
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun cacheEmail(username: String, email: String) {
        prefs.edit().putString("email_$username", email).apply()
    }

    private fun getCachedEmail(username: String): String? =
        prefs.getString("email_$username", null)

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
            return Result.failure(Exception("Building name is already taken. Please choose a different one."))
        }

        // Step 2: Create the Firebase Auth account
        val createResult = suspendCoroutine<Result<FirebaseUser>> { cont ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user!!
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(username).build()
                    )
                    cont.resume(Result.success(user))
                }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
        if (createResult.isFailure) return createResult

        val user = createResult.getOrThrow()

        // Step 3: Save username→email to local cache immediately (survives Firestore failures)
        cacheEmail(username, email)

        // Step 4: Write username doc — await so we know if it fails
        val usernameWriteResult = suspendCoroutine<Result<Unit>> { cont ->
            firestore.collection("rewatt_usernames").document(username)
                .set(mapOf("email" to email, "uid" to user.uid))
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
        if (usernameWriteResult.isFailure) {
            // Auth account created, cache saved; Firestore failed — sign-in on this
            // device will use the cache. Return success so the user can proceed.
            firestore.collection("rewatt_users").document(user.uid)
                .set(mapOf("username" to username, "email" to email, "uid" to user.uid))
            return Result.success(user)
        }

        // Step 5: Write user profile doc (fire-and-forget is acceptable here)
        firestore.collection("rewatt_users").document(user.uid)
            .set(mapOf("username" to username, "email" to email, "uid" to user.uid))

        return Result.success(user)
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

    suspend fun signIn(username: String, password: String): Result<FirebaseUser> {
        // If the user typed an email directly, skip Firestore lookup entirely
        if (username.contains("@")) {
            val result = suspendCoroutine<Result<FirebaseUser>> { cont ->
                auth.signInWithEmailAndPassword(username, password)
                    .addOnSuccessListener { r -> cont.resume(Result.success(r.user!!)) }
                    .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
            }
            // Cache the email under itself so future username lookups still work
            if (result.isSuccess) {
                result.getOrNull()?.displayName?.let { name ->
                    if (name.isNotBlank()) cacheEmail(name, username)
                }
            }
            return result
        }

        // Try local cache first — avoids Firestore reads while unauthenticated
        val cachedEmail = getCachedEmail(username)
        if (cachedEmail != null) {
            val authResult = suspendCoroutine<Result<FirebaseUser>> { cont ->
                auth.signInWithEmailAndPassword(cachedEmail, password)
                    .addOnSuccessListener { result -> cont.resume(Result.success(result.user!!)) }
                    .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
            }
            if (authResult.isSuccess) return authResult
            // Cache hit but auth failed → wrong password (not a missing-document issue)
            // Return the Firebase error directly so the user sees "wrong password"
            val err = authResult.exceptionOrNull()
            if (err != null && !isNetworkError(err)) return authResult
            // Network error → fall through to Firestore attempt
        }

        // Fallback: look up email from Firestore
        return suspendCoroutine { cont ->
            firestore.collection("rewatt_usernames").document(username).get()
                .addOnSuccessListener { doc ->
                    val email = doc.getString("email")
                    if (email == null) {
                        cont.resume(Result.failure(Exception("Username not found. Check that you typed it correctly.")))
                        return@addOnSuccessListener
                    }
                    // Save to cache so future sign-ins skip Firestore
                    cacheEmail(username, email)
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result -> cont.resume(Result.success(result.user!!)) }
                        .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
                }
                .addOnFailureListener { e ->
                    if (cachedEmail != null) {
                        // Cache had email but auth already failed above — propagate that error
                        cont.resume(Result.failure(e))
                    } else {
                        cont.resume(Result.failure(Exception("Username not found. Check that you typed it correctly.")))
                    }
                }
        }
    }

    private fun isNetworkError(e: Throwable): Boolean =
        e is com.google.firebase.FirebaseNetworkException ||
        e.message?.contains("network", ignoreCase = true) == true

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
