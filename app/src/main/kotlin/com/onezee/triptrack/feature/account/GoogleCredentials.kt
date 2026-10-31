// iOS: AppleSignInButton.swift (AppleAuthorizationRunner, startAuthorization). Android signs in with
// Google through Credential Manager instead of ASAuthorizationController (docs/divergences.md).
package com.onezee.triptrack.feature.account

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/**
 * Which Credential Manager request a sign-in starts with.
 *
 * - [button]: the «Sign in with Google» button, `GetSignInWithGoogleOption`. Every Google account
 *   on the phone plus «add account»; what the iPhone's Apple button is.
 * - [returning]: `GetGoogleIdOption`, first limited to accounts that already signed in to
 *   TripTrack with auto-select (one tap back after a soft expiry), then every account. The service
 *   falls back to [button] when neither offers anything.
 */
enum class GoogleSignInFlow { button, returning }

/** What Credential Manager answered. Only [Token] goes on to the server. */
sealed interface GoogleCredentialResult {
    /** `idToken` is the raw compact JWT; the server verifies it. The rest is display only. */
    data class Token(val idToken: String, val displayName: String?, val email: String?) : GoogleCredentialResult

    /** The person closed the Google sheet. Silent, like Apple's cancel code 1001 on iOS. */
    data object Cancelled : GoogleCredentialResult

    /** No Google account (or no authorized one, for the filtered request) on this phone. */
    data object NoAccount : GoogleCredentialResult

    /** Anything else: no Google Play services, an interrupted request, a malformed answer. */
    data class Failed(val reason: String) : GoogleCredentialResult
}

/** The seam the sign-in state machine is tested through. */
interface GoogleCredentialSource {
    /**
     * [hashedNonce] is the SHA-256 hex of the raw nonce the server receives; Google embeds it in
     * the token's `nonce` claim. [authorizedOnly] limits a [GoogleSignInFlow.returning] request to
     * accounts that already used this app.
     */
    suspend fun request(context: Context, flow: GoogleSignInFlow, hashedNonce: String, authorizedOnly: Boolean): GoogleCredentialResult

    /** After a hard sign-out, so auto-select does not sign the same account straight back in. */
    suspend fun clear(context: Context)
}

/** The production source: Credential Manager with the Google ID token options. */
class CredentialManagerGoogleSource(private val webClientId: String) : GoogleCredentialSource {
    override suspend fun request(
        context: Context, flow: GoogleSignInFlow, hashedNonce: String, authorizedOnly: Boolean,
    ): GoogleCredentialResult {
        val option = when (flow) {
            GoogleSignInFlow.button -> GetSignInWithGoogleOption.Builder(webClientId).setNonce(hashedNonce).build()
            GoogleSignInFlow.returning -> GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(authorizedOnly)
                .setAutoSelectEnabled(authorizedOnly)
                .setNonce(hashedNonce)
                .build()
        }
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleCredentialResult.Token(google.idToken, google.displayName, google.email)
            } else GoogleCredentialResult.Failed("unexpectedCredential")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleCredentialResult.NoAccount
        } catch (error: GetCredentialException) {
            // The type only: messages of provider errors can carry account details.
            val reason = when (error) {
                is GetCredentialInterruptedException -> "interrupted"
                is GetCredentialProviderConfigurationException -> "providerConfiguration"
                is GetCredentialUnsupportedException -> "unsupported"
                else -> error.type
            }
            Log.w(TAG, "Google credential request failed: $reason")
            GoogleCredentialResult.Failed(reason)
        } catch (_: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Failed("parsing")
        }
    }

    override suspend fun clear(context: Context) {
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: ClearCredentialException) { /* Best effort: the session is already gone. */ }
    }

    private companion object {
        const val TAG = "TripTrackAuth"
    }
}
