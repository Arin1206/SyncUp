package com.example.syncup.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class AuthHelper(private val auth: FirebaseAuth) {
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (Exception?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onError(task.exception) // ✅ passing required value
                }
            }
    }
}
