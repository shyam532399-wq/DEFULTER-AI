package com.example.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ApiKeyConfigStore"
private const val PREFS_NAME = "maya_api_key_vault"
private const val KEYSTORE_ALIAS = "maya_boss_api_key_aes_v1"
private const val KEY_PROVIDER = "provider"
private const val KEY_CIPHERTEXT = "encrypted_api_key"
private const val KEY_BASE_URL = "base_url"
private const val KEY_MODEL = "model_name"
private const val KEY_FALLBACK_MODELS = "fallback_models"
private const val KEY_IMAGE_MODEL = "image_model_name"
private const val KEY_CONNECTED_AT = "connected_at_ms"

enum class ApiProvider(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val keyPrefixHint: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val candidateModels: List<String>
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        shortBadge = "Gemini",
        keyPrefixHint = "AIzaSy...",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash",
        candidateModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3-flash-preview",
            "gemini-flash-latest",
            "gemini-2.0-flash",
            "gemini-1.5-flash"
        )
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        shortBadge = "OpenAI",
        keyPrefixHint = "sk-...",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        candidateModels = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o", "gpt-3.5-turbo")
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        shortBadge = "Groq",
        keyPrefixHint = "gsk_...",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        candidateModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        shortBadge = "OpenRouter",
        keyPrefixHint = "sk-or-...",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.5-flash",
        candidateModels = listOf(
            "google/gemini-2.5-flash",
            "openai/gpt-4o-mini",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-3-27b-it:free"
        )
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        shortBadge = "DeepSeek",
        keyPrefixHint = "sk-...",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-chat",
        candidateModels = listOf("deepseek-chat", "deepseek-reasoner")
    );

    companion object {
        fun fromId(id: String?): ApiProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI

        fun detectFromKey(rawKey: String, currentSelection: ApiProvider? = null): ApiProvider? {
            val trimmed = rawKey.replace(Regex("\\s+"), "")
            return when {
                trimmed.startsWith("AIza") -> GEMINI
                trimmed.startsWith("gsk_") -> GROQ
                trimmed.startsWith("sk-or-") -> OPENROUTER
                trimmed.startsWith("sk-") -> {
                    if (currentSelection == DEEPSEEK || currentSelection == OPENROUTER) {
                        currentSelection
                    } else {
                        OPENAI
                    }
                }
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
    val fallbackModels: List<String> = emptyList(),
    val imageModelName: String = "",
    val maskedKey: String = maskApiKey(apiKey),
    val connectedAtMs: Long = System.currentTimeMillis()
)

data class ApiKeyVerificationResult(
    val resolvedModel: String,
    val fallbackModels: List<String> = emptyList(),
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
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) : ApiKeyVerifier {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun verify(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String,
        requestedModel: String
    ): Result<ApiKeyVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trim().trimEnd('/')
            when (provider) {
                ApiProvider.GEMINI -> verifyGeminiKeyWithLivePing(apiKey, cleanBase, requestedModel)
                ApiProvider.OPENAI,
                ApiProvider.GROQ,
                ApiProvider.OPENROUTER,
                ApiProvider.DEEPSEEK -> verifyOpenAiCompatibleKeyWithLivePing(
                    provider = provider,
                    apiKey = apiKey,
                    cleanBase = cleanBase,
                    requestedModel = requestedModel
                )
            }
        }
    }

    private fun verifyGeminiKeyWithLivePing(
        apiKey: String,
        cleanBase: String,
        requestedModel: String
    ): ApiKeyVerificationResult {
        val listReq = Request.Builder()
            .url("$cleanBase/models?pageSize=100")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        val availableContentModels = mutableListOf<String>()
        val availableImageModels = mutableListOf<String>()

        httpClient.newCall(listReq).execute().use { resp ->
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

            val modelsArr = runCatching { JSONObject(bodyStr).optJSONArray("models") }.getOrNull()
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
        }

        val custom = requestedModel.trim().removePrefix("models/")
        val rankedCandidates = linkedSetOf<String>()
        if (custom.isNotBlank()) rankedCandidates.add(custom)
        for (preferred in ApiProvider.GEMINI.candidateModels) {
            if (availableContentModels.isEmpty() || preferred in availableContentModels) {
                rankedCandidates.add(preferred)
            }
        }
        availableContentModels.filter { name ->
            name.contains("gemini", ignoreCase = true) &&
                name.contains("flash", ignoreCase = true) &&
                !name.contains("image", ignoreCase = true) &&
                !name.contains("tts", ignoreCase = true) &&
                !name.contains("audio", ignoreCase = true) &&
                !name.contains("live", ignoreCase = true) &&
                !name.contains("robotics", ignoreCase = true) &&
                !name.contains("computer-use", ignoreCase = true)
        }.forEach { rankedCandidates.add(it) }

        if (rankedCandidates.isEmpty()) {
            rankedCandidates.addAll(ApiProvider.GEMINI.candidateModels)
        }

        val chosenImageModel = availableImageModels.firstOrNull {
            it.contains("2.5-flash-image", ignoreCase = true)
        } ?: availableImageModels.firstOrNull().orEmpty()

        // Perform a real 1-shot generateContent ping so we guarantee the selected model responds!
        var lastError = "Gemini model test fail hua."
        val candidateList = rankedCandidates.toList()
        for ((idx, modelId) in candidateList.withIndex()) {
            val pingPayload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "Hi"))
                        )
                    )
                )
                put("generationConfig", JSONObject().put("maxOutputTokens", 8))
            }
            val pingReq = Request.Builder()
                .url("$cleanBase/models/$modelId:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(pingPayload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(pingReq).execute().use { pingResp ->
                if (pingResp.isSuccessful) {
                    val remaining = candidateList.drop(idx + 1).take(5)
                    return ApiKeyVerificationResult(
                        resolvedModel = modelId,
                        fallbackModels = remaining,
                        resolvedImageModel = chosenImageModel
                    )
                } else {
                    val errBody = pingResp.body?.string().orEmpty()
                    val apiMsg = runCatching {
                        JSONObject(errBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    lastError = when (pingResp.code) {
                        429 -> "Gemini API key ka quota/rate-limit abhi full hai (429): ${apiMsg.take(120)}"
                        403 -> "Gemini API key permission denied (403): ${apiMsg.take(120)}"
                        else -> "Gemini model '$modelId' error (${pingResp.code}): ${apiMsg.take(120)}"
                    }
                    // If custom model was explicitly entered by Boss and failed, stop immediately and tell them
                    if (custom.isNotBlank() && modelId == custom) {
                        throw IllegalArgumentException(lastError)
                    }
                }
            }
        }

        throw IllegalArgumentException(lastError)
    }

    private fun verifyOpenAiCompatibleKeyWithLivePing(
        provider: ApiProvider,
        apiKey: String,
        cleanBase: String,
        requestedModel: String
    ): ApiKeyVerificationResult {
        val available = mutableSetOf<String>()
        val listReqBuilder = Request.Builder()
            .url("$cleanBase/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
        if (provider == ApiProvider.OPENROUTER) {
            listReqBuilder.header("HTTP-Referer", "https://maya.agent.local")
            listReqBuilder.header("X-Title", "Maya Personal Agent")
        }

        runCatching {
            httpClient.newCall(listReqBuilder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.code == 401 || resp.code == 403) {
                    throw IllegalArgumentException(
                        "${provider.displayName} API key invalid ya expired hai (${resp.code}), Boss."
                    )
                }
                if (resp.isSuccessful) {
                    val dataArr = runCatching { JSONObject(bodyStr).optJSONArray("data") }.getOrNull()
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val id = dataArr.optJSONObject(i)?.optString("id").orEmpty()
                            if (id.isNotBlank()) available.add(id)
                        }
                    }
                }
            }
        }.onFailure { e ->
            if (e is IllegalArgumentException) throw e
        }

        val custom = requestedModel.trim()
        val rankedCandidates = linkedSetOf<String>()
        if (custom.isNotBlank()) rankedCandidates.add(custom)
        for (preferred in provider.candidateModels) {
            if (available.isEmpty() || preferred in available) {
                rankedCandidates.add(preferred)
            }
        }
        if (rankedCandidates.isEmpty()) {
            rankedCandidates.add(provider.defaultModel)
        }

        val candidateList = rankedCandidates.toList()
        var lastError = "${provider.displayName} model test fail hua."

        for ((idx, modelId) in candidateList.withIndex()) {
            val pingPayload = JSONObject().apply {
                put("model", modelId)
                put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", "Hi"))
                )
                put("max_tokens", 8)
            }
            val pingBuilder = Request.Builder()
                .url("$cleanBase/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(pingPayload.toString().toRequestBody(jsonMediaType))
            if (provider == ApiProvider.OPENROUTER) {
                pingBuilder.header("HTTP-Referer", "https://maya.agent.local")
                pingBuilder.header("X-Title", "Maya Personal Agent")
            }

            httpClient.newCall(pingBuilder.build()).execute().use { pingResp ->
                if (pingResp.isSuccessful) {
                    val remaining = candidateList.drop(idx + 1).take(4)
                    return ApiKeyVerificationResult(
                        resolvedModel = modelId,
                        fallbackModels = remaining
                    )
                } else {
                    val errBody = pingResp.body?.string().orEmpty()
                    val apiMsg = runCatching {
                        JSONObject(errBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    lastError = when (pingResp.code) {
                        401, 403 -> "${provider.displayName} API key invalid hai (${pingResp.code}). Agar DeepSeek ya OpenRouter key hai to sahi provider chip chunein."
                        402, 429 -> "${provider.displayName} account me credit/quota nahi hai (${pingResp.code}): ${apiMsg.take(120)}"
                        else -> "${provider.displayName} error (${pingResp.code}): ${apiMsg.take(120)}"
                    }
                    if (pingResp.code == 401 || pingResp.code == 402 || (custom.isNotBlank() && modelId == custom)) {
                        throw IllegalArgumentException(lastError)
                    }
                }
            }
        }

        throw IllegalArgumentException(lastError)
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
        val cleanKey = rawKey.replace(Regex("\\s+"), "")
        val prevConnected = cachedConfig?.toConnectedStatus()
        if (cleanKey.length < 12) {
            val msg = "Valid API key paste karein Boss (kam se kam 12 characters)."
            _status.value = ApiKeyConnectionStatus.Error(msg, prevConnected)
            return Result.failure(IllegalArgumentException(msg))
        }

        val provider = ApiProvider.detectFromKey(cleanKey, selectedProvider) ?: selectedProvider
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
                fallbackModels = verifyRes.fallbackModels,
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
            .putString(KEY_FALLBACK_MODELS, config.fallbackModels.joinToString(","))
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
        val fallbacks = prefs.getString(KEY_FALLBACK_MODELS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val imageModel = prefs.getString(KEY_IMAGE_MODEL, "").orEmpty()
        val connectedAt = prefs.getLong(KEY_CONNECTED_AT, System.currentTimeMillis())
        return ActiveApiKeyConfig(
            provider = provider,
            apiKey = decryptedKey,
            baseUrl = baseUrl,
            modelName = model,
            fallbackModels = fallbacks,
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
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
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
