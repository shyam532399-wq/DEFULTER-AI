package com.example.ui.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val AUTH_TAG = "Auth"
private const val REGISTERED_SHA1 = "AD:8C:A3:5C:24:4E:79:2C:E3:3F:5B:0A:9E:84:5B:D8:6D:D0:2A:40"

data class ApkSignatureHashes(
    val sha1: String,
    val sha256: String
) {
    val matchesRegisteredFirebaseKey: Boolean
        get() = sha1.equals(REGISTERED_SHA1, ignoreCase = true)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reads the runtime APK signing certificate SHA-1 and SHA-256 so any mismatch
 * (e.g. from an unregistered GitHub CI key or Release upload key) is immediately visible.
 */
@Suppress("DEPRECATION")
fun readRuntimeApkHashes(context: Context): ApkSignatureHashes? {
    return try {
        val pm = context.packageManager
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = pkgInfo.signingInfo
            val signers = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            signers?.firstOrNull()?.toByteArray()
        } else {
            val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            pkgInfo.signatures?.firstOrNull()?.toByteArray()
        }

        if (certBytes == null || certBytes.isEmpty()) return null

        val sha1 = MessageDigest.getInstance("SHA-1").digest(certBytes)
            .joinToString(":") { "%02X".format(it) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(certBytes)
            .joinToString(":") { "%02X".format(it) }
        ApkSignatureHashes(sha1 = sha1, sha256 = sha256)
    } catch (_: Exception) {
        null
    }
}

private fun extractGoogleIdToken(credential: Credential): String? {
    return when (credential) {
        is GoogleIdTokenCredential -> credential.idToken
        is CustomCredential -> {
            if (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        }
        else -> null
    }
}

/**
 * Silent Auto-Sign-In on startup using a single-option GetCredentialRequest (GetGoogleIdOption).
 * Runs sequentially before enabling interactive sign-in so Play Services HiddenActivity never collides.
 */
suspend fun attemptAutoSignInSuspend(
    context: Context,
    credentialManager: CredentialManager
): Boolean {
    if (Firebase.auth.currentUser != null) {
        return true
    }
    val activity = context.findActivity() ?: return false
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        return false
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    // Strictly ONE option per request for Android 16 (API 36) Credential Manager responsiveness
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(activity, request)
        val googleIdToken = extractGoogleIdToken(result.credential) ?: return false
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        withTimeout(25_000L) {
            Firebase.auth.signInWithCredential(authCredential).await()
        }
        Firebase.auth.currentUser != null
    } catch (_: Exception) {
        false
    }
}

/**
 * Interactive Google Sign-In using a single-option GetCredentialRequest (GetSignInWithGoogleOption).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    useQuickBottomSheet: Boolean = false,
    onAuthCancelled: (String?) -> Unit = {}
) {
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Activity context unavailable for Google Sign-In. App restart karke try karein.")
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    // Strictly ONE credential option per request
    val request = if (useQuickBottomSheet) {
        val bottomSheetOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        GetCredentialRequest.Builder()
            .addCredentialOption(bottomSheetOption)
            .build()
    } else {
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
    }

    // Prefer Activity lifecycleScope so coroutine survives Play Services overlay pauses
    val executionScope = (activity as? LifecycleOwner)?.lifecycleScope ?: scope

    executionScope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val googleIdToken = extractGoogleIdToken(result.credential)
            if (googleIdToken != null) {
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                withTimeout(25_000L) {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type: ${result.credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            val apkHashes = readRuntimeApkHashes(context)
            val hint = if (apkHashes != null && !apkHashes.matchesRegisteredFirebaseKey) {
                "APK Signing SHA-1 Firebase me match nahi hua! Neeche diye 'Copy SHA-1 & SHA-256' button ko dabakar AI Studio chat me paste kar dein — hum turant register kar denge."
            } else {
                "Account choose karne ke baad dialog band ho gaya (${e.message ?: "cancelled"}). Neeche 'Google Quick Sheet se Sign In' button try karein."
            }
            onAuthCancelled(hint)
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google credential on device: ${e.message}", e)
            onAuthError(
                "Is phone/emulator me koi Google Account login nahi mila. Device Settings -> Accounts me Google sign-in karke dobara try karein."
            )
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError("${e.javaClass.simpleName}: ${e.localizedMessage ?: "Sign in failed"}")
        }
    }
}

/**
 * Sign-Out Flow clearing both Firebase Auth and Credential Manager state.
 */
fun signOutUser(
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val apkHashes = remember(context) { readRuntimeApkHashes(context) }

    var isAutoChecking by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val signedIn = attemptAutoSignInSuspend(
            context = context,
            credentialManager = credentialManager
        )
        isAutoChecking = false
        if (signedIn) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090B12),
                        Color(0xFF141829),
                        Color(0xFF090B12)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner Card with overlapping Maya Emblem
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_maya_orb_hero),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            RoundedCornerShape(28.dp)
                        ),
                    contentScale = ContentScale.Crop
                )
                Image(
                    painter = painterResource(id = R.drawable.img_maya_avatar),
                    contentDescription = stringResource(R.string.cd_maya_avatar),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.auth_headline),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Proactive APK SHA-1 Mismatch Detector Card (only shown if APK key != Registered Firebase Key)
            if (apkHashes != null && !apkHashes.matchesRegisteredFirebaseKey) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "APK Signing Key Mismatch Detect Hua",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Is APK ki signing key Firebase me registered nahi hai, isliye account select karne ke baad login ruk raha hai.\nSHA-1: ${apkHashes.sha1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val payload = "SHA1: ${apkHashes.sha1}\nSHA256: ${apkHashes.sha256}"
                                cm.setPrimaryClip(ClipData.newPlainText("Maya APK Fingerprints", payload))
                                Toast.makeText(context, "SHA-1 & SHA-256 copied! Chat me paste kar dein.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy SHA-1 & SHA-256 (Chat me bhejein)")
                        }
                    }
                }
            }

            // 3 Feature Pillars
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuthFeatureRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "Hinglish Voice + 3 Personas",
                    subtitle = "Maya, Friday, aur Venom — 'Hey Maya' wake word aur crisp 1–3 sentence voice."
                )
                AuthFeatureRow(
                    icon = Icons.Filled.Smartphone,
                    title = "Bolo Kam, Karo Zyada",
                    subtitle = "Real Torch, Alarms, WhatsApp/SMS/Email confirm gate, Live Weather, Markets aur Board."
                )
                AuthFeatureRow(
                    icon = Icons.Filled.Security,
                    title = "Voice Guardian & Cloud Memory",
                    subtitle = "'Sirf meri suno' speaker check, Touch Guard, aur Boss ka personal Hinglish wiki."
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        useQuickBottomSheet = false,
                        onAuthCancelled = { hint ->
                            isLoading = false
                            errorMessage = hint
                        }
                    )
                },
                enabled = !isLoading && !isAutoChecking,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_with_google),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        useQuickBottomSheet = true,
                        onAuthCancelled = { hint ->
                            isLoading = false
                            errorMessage = hint
                        }
                    )
                },
                enabled = !isLoading && !isAutoChecking,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("google_quick_sheet_button")
            ) {
                Text(
                    text = "Dialog aage na badhe? Google Quick Sheet se Sign In karein",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
