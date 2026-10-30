package com.example.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "ApiKeyConfigStore"
private const val PREFS_NAME = "maya_api_key_vault"
private const val KEYSTORE_ALIAS = "maya_boss_api_key_aes_v1"
private const val KEY_PROVIDER = "provider"
private const val KEY_CIPHERTEXT = "encrypted_api_key"
private const val KEY_BASE_URL = "base_url"
private const val KEY_MODEL = "model_name"
private const val KEY_IMAGE_MODEL = "image_model_name"
private const val KEY_CONNECTED_AT = "connected_at_ms"

enum class ApiProvider(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val keyPrefixHint: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        shortBadge = "Gemini",
        keyPrefixHint = "AIzaSy...",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        shortBadge = "OpenAI",
        keyPrefixHint = "sk-...",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        shortBadge = "Groq",
        keyPrefixHint = "gsk_...",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        shortBadge = "OpenRouter",
        keyPrefixHint = "sk-or-...",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.5-flash"
    );

    companion object {
        fun fromId(id: String?): ApiProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI

        fun detectFromKey(rawKey: String): ApiProvider? {
            val trimmed = rawKey.trim()
            return when {
                trimmed.startsWith("AIza") -> GEMINI
                trimmed.startsWith("gsk_") -> GROQ
                trimmed.startsWith("sk-or-") -> OPENROUTER
                trimmed.startsWith("sk-") -> OPENAI
                else -> null
            }
        }
    }
}

data class ActiveApiKeyConfig(
    val provider: ApiProvider,
    val apiKey: String,
    val baseUrl: String,
    val modelName: String,
    val imageModelName: String = "",
    val maskedKey: String = maskApiKey(apiKey),
    val connectedAtMs: Long = System.currentTimeMillis()
)

data class ApiKeyVerificationResult(
    val resolvedModel: String,
    val resolvedImageModel: String = ""
)

sealed interface ApiKeyConnectionStatus {
    data object BuiltIn : ApiKeyConnectionStatus
    data class Verifying(val provider: ApiProvider) : ApiKeyConnectionStatus
    data class Connected(
        val provider: ApiProvider,
        val maskedKey: String,
        val activeModel: String,
        val baseUrl: String
    ) : ApiKeyConnectionStatus

    data class Error(
        val message: String,
        val previousConnected: Connected? = null
    ) : ApiKeyConnectionStatus
}

fun interface ApiKeyVerifier {
    suspend fun verify(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String,
        requestedModel: String
    ): Result<ApiKeyVerificationResult>
}

fun maskApiKey(rawKey: String): String {
    val clean = rawKey.trim()
    if (clean.length <= 10) return "••••" + clean.takeLast(2)
    return "${clean.take(6)}••••${clean.takeLast(4)}"
}

class LiveHttpApiKeyVerifier(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : ApiKeyVerifier {

    override suspend fun verify(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String,
        requestedModel: String
    ): Result<ApiKeyVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trim().trimEnd('/')
            when (provider) {
                ApiProvider.GEMINI -> verifyGeminiKey(apiKey, cleanBase, requestedModel)
                ApiProvider.OPENAI,
                ApiProvider.GROQ,
                ApiProvider.OPENROUTER -> verifyOpenAiCompatibleKey(provider, apiKey, cleanBase, requestedModel)
            }
        }
    }

    private fun verifyGeminiKey(
        apiKey: String,
        cleanBase: String,
        requestedModel: String
    ): ApiKeyVerificationResult {
        val req = Request.Builder()
            .url("$cleanBase/models?pageSize=100")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val apiMsg = runCatching {
                    JSONObject(bodyStr).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                val readable = when (resp.code) {
                    400, 401, 403 -> "Gemini API key invalid ya unauthorized hai (${resp.code}). Sahi 'AIza...' key daalein, Boss."
                    429 -> "Gemini API quota limit hit ho gayi hai (429). Thodi der baad ya doosri key try karein."
                    else -> "Gemini server se connect nahi hua (${resp.code}): ${apiMsg.ifBlank { "connection error" }}"
                }
                throw IllegalArgumentException(readable)
            }

            val availableContentModels = mutableListOf<String>()
            val availableImageModels = mutableListOf<String>()
            val modelsArr = JSONObject(bodyStr).optJSONArray("models")
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val mObj = modelsArr.optJSONObject(i) ?: continue
                    val rawName = mObj.optString("name", "").removePrefix("models/")
                    if (rawName.isBlank()) continue
                    val methods = mObj.optJSONArray("supportedGenerationMethods")
                    var supportsGenContent = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGenContent = true
                                break
                            }
                        }
                    }
                    if (supportsGenContent) {
                        availableContentModels.add(rawName)
                        if (rawName.contains("image", ignoreCase = true)) {
                            availableImageModels.add(rawName)
                        }
                    }
                }
            }

            val custom = requestedModel.trim().removePrefix("models/")
            val chosenModel = when {
                custom.isNotBlank() -> custom
                "gemini-2.5-flash" in availableContentModels -> "gemini-2.5-flash"
                "gemini-2.0-flash" in availableContentModels -> "gemini-2.0-flash"
                else -> availableContentModels.firstOrNull {
                    it.contains("gemini", ignoreCase = true) &&
                        it.contains("flash", ignoreCase = true) &&
                        !it.contains("image", ignoreCase = true) &&
                        !it.contains("tts", ignoreCase = true)
                } ?: ApiProvider.GEMINI.defaultModel
            }

            val chosenImageModel = availableImageModels.firstOrNull {
                it.contains("2.5-flash-image", ignoreCase = true)
            } ?: availableImageModels.firstOrNull().orEmpty()

            return ApiKeyVerificationResult(
                resolvedModel = chosenModel,
                resolvedImageModel = chosenImageModel
            )
        }
    }

    private fun verifyOpenAiCompatibleKey(
        provider: ApiProvider,
        apiKey: String,
        cleanBase: String,
        requestedModel: String
    ): ApiKeyVerificationResult {
        val reqBuilder = Request.Builder()
            .url("$cleanBase/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
        if (provider == ApiProvider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://maya.agent.local")
            reqBuilder.header("X-Title", "Maya Personal Agent")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val readable = when (resp.code) {
                    401, 403 -> "${provider.displayName} API key invalid ya expired hai (${resp.code}), Boss."
                    429 -> "${provider.displayName} rate limit / quota khatam ho gaya hai (429)."
                    else -> "${provider.displayName} verify nahi ho saka (HTTP ${resp.code})."
                }
                throw IllegalArgumentException(readable)
            }

            val available = mutableSetOf<String>()
            val dataArr = runCatching { JSONObject(bodyStr).optJSONArray("data") }.getOrNull()
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val id = dataArr.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) available.add(id)
                }
            }

            val custom = requestedModel.trim()
            val chosen = when {
                custom.isNotBlank() -> custom
                provider.defaultModel in available -> provider.defaultModel
                else -> provider.defaultModel
            }
            return ApiKeyVerificationResult(resolvedModel = chosen)
        }
    }
}

class ApiKeyConfigStore(
    context: Context,
    private val verifier: ApiKeyVerifier = LiveHttpApiKeyVerifier()
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedConfig: ActiveApiKeyConfig? = loadPersistedConfig()

    private val _status = MutableStateFlow<ApiKeyConnectionStatus>(
        cachedConfig?.toConnectedStatus() ?: ApiKeyConnectionStatus.BuiltIn
    )
    val status: StateFlow<ApiKeyConnectionStatus> = _status.asStateFlow()

    fun getActiveConfig(): ActiveApiKeyConfig? = cachedConfig

    suspend fun verifyAndConnect(
        rawKey: String,
        selectedProvider: ApiProvider,
        customModel: String = "",
        customBaseUrl: String = ""
    ): Result<ApiKeyConnectionStatus.Connected> {
        val cleanKey = rawKey.trim()
        val prevConnected = cachedConfig?.toConnectedStatus()
        if (cleanKey.length < 12 || cleanKey.contains(' ')) {
            val msg = "Valid API key paste karein Boss (bina space ke, kam se kam 12 characters)."
            _status.value = ApiKeyConnectionStatus.Error(msg, prevConnected)
            return Result.failure(IllegalArgumentException(msg))
        }

        val provider = ApiProvider.detectFromKey(cleanKey) ?: selectedProvider
        val baseUrl = customBaseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')

        _status.value = ApiKeyConnectionStatus.Verifying(provider)

        return try {
            val verifyRes = verifier.verify(
                provider = provider,
                apiKey = cleanKey,
                baseUrl = baseUrl,
                requestedModel = customModel
            ).getOrThrow()

            val newConfig = ActiveApiKeyConfig(
                provider = provider,
                apiKey = cleanKey,
                baseUrl = baseUrl,
                modelName = verifyRes.resolvedModel,
                imageModelName = verifyRes.resolvedImageModel
            )
            persistConfig(newConfig)
            cachedConfig = newConfig
            val connected = newConfig.toConnectedStatus()
            _status.value = connected
            Result.success(connected)
        } catch (e: CancellationException) {
            _status.value = prevConnected ?: ApiKeyConnectionStatus.BuiltIn
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "API key verification failed for ${provider.id}", e)
            val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Network ya API key check fail hua, Boss. Internet aur key dono check karein."
            _status.value = ApiKeyConnectionStatus.Error(errMsg, prevConnected)
            Result.failure(e)
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        cachedConfig = null
        _status.value = ApiKeyConnectionStatus.BuiltIn
    }

    private fun ActiveApiKeyConfig.toConnectedStatus(): ApiKeyConnectionStatus.Connected =
        ApiKeyConnectionStatus.Connected(
            provider = provider,
            maskedKey = maskedKey,
            activeModel = modelName,
            baseUrl = baseUrl
        )

    private fun persistConfig(config: ActiveApiKeyConfig) {
        val encrypted = encryptSecret(config.apiKey)
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.id)
            .putString(KEY_CIPHERTEXT, encrypted)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.modelName)
            .putString(KEY_IMAGE_MODEL, config.imageModelName)
            .putLong(KEY_CONNECTED_AT, config.connectedAtMs)
            .apply()
    }

    private fun loadPersistedConfig(): ActiveApiKeyConfig? {
        val cipherPayload = prefs.getString(KEY_CIPHERTEXT, null)?.takeIf { it.isNotBlank() } ?: return null
        val decryptedKey = decryptSecret(cipherPayload)?.takeIf { it.isNotBlank() } ?: return null
        val provider = ApiProvider.fromId(prefs.getString(KEY_PROVIDER, ApiProvider.GEMINI.id))
        val baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        val model = prefs.getString(KEY_MODEL, provider.defaultModel) ?: provider.defaultModel
        val imageModel = prefs.getString(KEY_IMAGE_MODEL, "").orEmpty()
        val connectedAt = prefs.getLong(KEY_CONNECTED_AT, System.currentTimeMillis())
        return ActiveApiKeyConfig(
            provider = provider,
            apiKey = decryptedKey,
            baseUrl = baseUrl,
            modelName = model,
            imageModelName = imageModel,
            connectedAtMs = connectedAt
        )
    }

    private fun encryptSecret(plainText: String): String {
        return try {
            val secretKey = getOrCreateAesKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val encB64 = Base64.encodeToString(
                    cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP
                )
                "gcm:$ivB64:$encB64"
            } else {
                "b64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable, falling back to local encoding", e)
            "b64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptSecret(payload: String): String? {
        return try {
            when {
                payload.startsWith("gcm:") -> {
                    val parts = payload.split(":")
                    if (parts.size != 3) return null
                    val secretKey = getOrCreateAesKey() ?: return null
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }
                payload.startsWith("b64:") -> {
                    val bytes = Base64.decode(payload.removePrefix("b64:"), Base64.NO_WRAP)
                    String(bytes, Charsets.UTF_8)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt persisted API key", e)
            null
        }
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.PrivateKeyEntry)
            if (existing == null && ks.containsAlias(KEYSTORE_ALIAS)) {
                val secEntry = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (secEntry != null) return secEntry.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }
}
