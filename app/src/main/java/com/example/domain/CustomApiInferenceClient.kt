package com.example.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes real REST inference against Boss's connected API key
 * (Google Gemini `v1beta` or OpenAI-compatible `v1/chat/completions` for OpenAI, Groq, OpenRouter).
 */
class CustomApiInferenceClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateStructuredDecisionJson(
        config: ActiveApiKeyConfig,
        fullPrompt: String,
        actionTypes: List<String>
    ): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiStructuredDecision(config, fullPrompt, actionTypes)
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER -> callOpenAiStructuredDecision(config, fullPrompt, actionTypes)
        }
    }

    suspend fun generateText(
        config: ActiveApiKeyConfig,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiTextOrVision(config, prompt, bitmapJpegBase64 = null)
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER -> callOpenAiTextOrVision(config, prompt, bitmapJpegBase64 = null)
        }
    }

    suspend fun generateVisionText(
        config: ActiveApiKeyConfig,
        bitmap: Bitmap,
        visionPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val jpegBase64 = encodeBitmapToJpegBase64(bitmap)
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiTextOrVision(config, visionPrompt, bitmapJpegBase64 = jpegBase64)
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER -> callOpenAiTextOrVision(config, visionPrompt, bitmapJpegBase64 = jpegBase64)
        }
    }

    suspend fun generateImage(
        config: ActiveApiKeyConfig,
        prompt: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiImageGeneration(config, prompt)
            ApiProvider.OPENAI -> callOpenAiImageGeneration(config, prompt)
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER -> null
        }
    }

    private fun callGeminiStructuredDecision(
        config: ActiveApiKeyConfig,
        fullPrompt: String,
        actionTypes: List<String>
    ): String {
        val schemaJson = buildGeminiDecisionSchemaJson(actionTypes)
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", fullPrompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schemaJson)
                }
            )
        }

        val modelId = config.modelName.removePrefix("models/")
        val req = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/models/$modelId:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndExtractGeminiText(req)
    }

    private fun callGeminiTextOrVision(
        config: ActiveApiKeyConfig,
        prompt: String,
        bitmapJpegBase64: String?
    ): String {
        val parts = JSONArray()
        if (!bitmapJpegBase64.isNullOrBlank()) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", bitmapJpegBase64)
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", parts))
        )

        val modelId = config.modelName.removePrefix("models/")
        val req = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/models/$modelId:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndExtractGeminiText(req)
    }

    private fun callGeminiImageGeneration(
        config: ActiveApiKeyConfig,
        prompt: String
    ): Bitmap? {
        val imgModel = config.imageModelName.ifBlank { "gemini-2.5-flash-image" }.removePrefix("models/")
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("TEXT").put("IMAGE")
                )
            )
        }

        val req = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/models/$imgModel:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
                val b64 = inline.optString("data", "")
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            return null
        }
    }

    private fun callOpenAiStructuredDecision(
        config: ActiveApiKeyConfig,
        fullPrompt: String,
        actionTypes: List<String>
    ): String {
        val schemaHint = """
            Respond STRICTLY with a single valid JSON object (no markdown fences) matching this exact schema:
            {
              "spokenReply": "string (1-3 short Hinglish sentences, no URLs)",
              "actionType": "one of [${actionTypes.joinToString(", ")}]",
              "target": "string",
              "recipient": "string",
              "phone": "string",
              "subject": "string",
              "messageBody": "string",
              "boardTitle": "string",
              "boardCategory": "one of [research, marketing, reviews, email, market, note, dashboard]",
              "boardContent": "string",
              "hour": 7,
              "minute": 0,
              "numberVal": 50,
              "missionSteps": ["string"]
            }
        """.trimIndent()

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", schemaHint))
            .put(JSONObject().put("role", "user").put("content", fullPrompt))

        val payload = JSONObject().apply {
            put("model", config.modelName)
            put("messages", messages)
            put("response_format", JSONObject().put("type", "json_object"))
            put("temperature", 0.3)
        }

        return executeAndExtractOpenAiText(config, payload)
    }

    private fun callOpenAiTextOrVision(
        config: ActiveApiKeyConfig,
        prompt: String,
        bitmapJpegBase64: String?
    ): String {
        val userContent: Any = if (bitmapJpegBase64.isNullOrBlank()) {
            prompt
        } else {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$bitmapJpegBase64")
                        )
                )
        }

        val payload = JSONObject().apply {
            put("model", config.modelName)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userContent)))
        }

        return executeAndExtractOpenAiText(config, payload)
    }

    private fun callOpenAiImageGeneration(
        config: ActiveApiKeyConfig,
        prompt: String
    ): Bitmap? {
        val payload = JSONObject().apply {
            put("model", "dall-e-3")
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            put("response_format", "b64_json")
        }
        val req = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/images/generations")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val b64 = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("data")
                ?.optJSONObject(0)
                ?.optString("b64_json", "")
                .orEmpty()
            if (b64.isBlank()) return null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun executeAndExtractGeminiText(req: Request): String {
        httpClient.newCall(req).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw IllegalStateException(
                    "Gemini API error (${resp.code}): ${msg.ifBlank { "Key ya quota check karein, Boss." }}"
                )
            }
            val root = JSONObject(rawBody)
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini API se empty response aaya, Boss.")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                if (text.isNotEmpty()) sb.append(text)
            }
            return sb.toString().trim().ifBlank {
                throw IllegalStateException("Gemini response text khali aaya, Boss.")
            }
        }
    }

    private fun executeAndExtractOpenAiText(config: ActiveApiKeyConfig, payload: JSONObject): String {
        val reqBuilder = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toString().toRequestBody(jsonMediaType))
        if (config.provider == ApiProvider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://maya.agent.local")
            reqBuilder.header("X-Title", "Maya Personal Agent")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw IllegalStateException(
                    "${config.provider.displayName} API error (${resp.code}): ${msg.ifBlank { "Key ya model check karein, Boss." }}"
                )
            }
            val content = JSONObject(rawBody)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
                .trim()
            if (content.isBlank()) {
                throw IllegalStateException("${config.provider.displayName} se empty response aaya, Boss.")
            }
            return content
        }
    }

    private fun encodeBitmapToJpegBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildGeminiDecisionSchemaJson(actionTypes: List<String>): JSONObject {
        val props = JSONObject().apply {
            put("spokenReply", JSONObject().put("type", "STRING"))
            put(
                "actionType",
                JSONObject()
                    .put("type", "STRING")
                    .put("enum", JSONArray(actionTypes))
            )
            put("target", JSONObject().put("type", "STRING"))
            put("recipient", JSONObject().put("type", "STRING"))
            put("phone", JSONObject().put("type", "STRING"))
            put("subject", JSONObject().put("type", "STRING"))
            put("messageBody", JSONObject().put("type", "STRING"))
            put("boardTitle", JSONObject().put("type", "STRING"))
            put(
                "boardCategory",
                JSONObject()
                    .put("type", "STRING")
                    .put("enum", JSONArray(listOf("research", "marketing", "reviews", "email", "market", "note", "dashboard")))
            )
            put("boardContent", JSONObject().put("type", "STRING"))
            put("hour", JSONObject().put("type", "INTEGER"))
            put("minute", JSONObject().put("type", "INTEGER"))
            put("numberVal", JSONObject().put("type", "INTEGER"))
            put(
                "missionSteps",
                JSONObject()
                    .put("type", "ARRAY")
                    .put("items", JSONObject().put("type", "STRING"))
            )
        }
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", props)
            .put("required", JSONArray(listOf("spokenReply", "actionType")))
    }

    companion object {
        fun extractCleanJsonObject(raw: String): JSONObject {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            val candidate = if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
            return JSONObject(candidate)
        }
    }
}
