package com.daniel.reproductor.ui.together

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.daniel.reproductor.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** "Continuar con Google" con Credential Manager: devuelve el ID token que verifica el servidor. */
object GoogleSignIn {

    sealed interface Result {
        data class Token(val idToken: String) : Result
        data class Error(val message: String) : Result
        data object Cancelled : Result
    }

    suspend fun idToken(activity: Activity): Result {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Error("Google no devolvió una cuenta válida.")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: GetCredentialException) {
            // Suele ser la huella SHA-1 o el ID de cliente web mal configurados en Firebase
            Result.Error("No se pudo iniciar con Google (${e.type}). Revisa la huella SHA-1 en Firebase.")
        }
    }
}
