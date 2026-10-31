package com.example.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CustomApiInference"

/**
 * Executes fast, resilient REST inference against Boss's connected API key
 * (Google Gemini `v1beta` or OpenAI-compatible `chat/completions` for OpenAI, Groq, OpenRouter, DeepSeek)
 * with automatic model fallback, Gemini 2.5+ thinking-part filtering, and plain-text JSON recovery.
 */
class CustomApiInferenceClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(55, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateStructuredDecision(
        config: ActiveApiKeyConfig,
        fullPrompt: String,
        actionTypes: List<String>
    ): AgentDecision = withContext(Dispatchers.IO) {
        val schemaInstruction = buildDecisionJsonInstruction(actionTypes)
        val combinedPrompt = "$fullPrompt\n\n$schemaInstruction"

        val rawResponse = when (config.provider) {
            ApiProvider.GEMINI -> callGeminiWithModelFallbacks(
                config = config,
                prompt = combinedPrompt,
                bitmapJpegBase64 = null,
                preferJsonMime = true
            )
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER,
            ApiProvider.DEEPSEEK -> callOpenAiWithModelFallbacks(
                config = config,
                systemPrompt = schemaInstruction,
                userPrompt = fullPrompt,
                bitmapJpegBase64 = null,
                preferJsonObject = true
            )
        }

        parseAgentDecisionResilient(rawResponse, actionTypes)
    }

    suspend fun generateText(
        config: ActiveApiKeyConfig,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiWithModelFallbacks(
                config = config,
                prompt = prompt,
                bitmapJpegBase64 = null,
                preferJsonMime = false
            )
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER,
            ApiProvider.DEEPSEEK -> callOpenAiWithModelFallbacks(
                config = config,
                systemPrompt = null,
                userPrompt = prompt,
                bitmapJpegBase64 = null,
                preferJsonObject = false
            )
        }
    }

    suspend fun generateVisionText(
        config: ActiveApiKeyConfig,
        bitmap: Bitmap,
        visionPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val jpegBase64 = encodeBitmapToJpegBase64(bitmap)
        when (config.provider) {
            ApiProvider.GEMINI -> callGeminiWithModelFallbacks(
                config = config,
                prompt = visionPrompt,
                bitmapJpegBase64 = jpegBase64,
                preferJsonMime = false
            )
            ApiProvider.OPENAI,
            ApiProvider.GROQ,
            ApiProvider.OPENROUTER,
            ApiProvider.DEEPSEEK -> callOpenAiWithModelFallbacks(
                config = config,
                systemPrompt = null,
                userPrompt = visionPrompt,
                bitmapJpegBase64 = jpegBase64,
                preferJsonObject = false
            )
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
            ApiProvider.OPENROUTER,
            ApiProvider.DEEPSEEK -> null
        }
    }

    private fun buildCandidateModels(config: ActiveApiKeyConfig): List<String> {
        val ordered = linkedSetOf<String>()
        if (config.modelName.isNotBlank()) {
            ordered.add(config.modelName.removePrefix("models/"))
        }
        config.fallbackModels.forEach { fb ->
            if (fb.isNotBlank()) ordered.add(fb.removePrefix("models/"))
        }
        config.provider.candidateModels.forEach { def ->
            ordered.add(def)
        }
        return ordered.toList()
    }

    private fun callGeminiWithModelFallbacks(
        config: ActiveApiKeyConfig,
        prompt: String,
        bitmapJpegBase64: String?,
        preferJsonMime: Boolean
    ): String {
        val modelsToTry = buildCandidateModels(config)
        var lastException: Exception? = null

        for (modelId in modelsToTry) {
            try {
                return executeSingleGeminiCall(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    modelId = modelId,
                    prompt = prompt,
                    bitmapJpegBase64 = bitmapJpegBase64,
                    useJsonMime = preferJsonMime
                )
            } catch (e: GeminiHttpException) {
                Log.w(TAG, "Gemini model $modelId returned HTTP ${e.statusCode}: ${e.message}")
                lastException = e
                // If JSON mime or thinkingConfig caused HTTP 400 on a specific model (e.g. Gemma), retry plain mode once
                if (e.statusCode == 400 && preferJsonMime) {
                    val plainRetry = runCatching {
                        executeSingleGeminiCall(
                            baseUrl = config.baseUrl,
                            apiKey = config.apiKey,
                            modelId = modelId,
                            prompt = prompt,
                            bitmapJpegBase64 = bitmapJpegBase64,
                            useJsonMime = false
                        )
                    }.getOrNull()
                    if (!plainRetry.isNullOrBlank()) return plainRetry
                }
                // Unauthorized / invalid API key won't succeed on other models either
                if (e.statusCode == 401 || e.statusCode == 403) {
                    throw IllegalStateException("Gemini API key invalid ya unauthorized hai (${e.statusCode}), Boss.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini model $modelId failed", e)
                lastException = e
            }
        }

        throw IllegalStateException(
            lastException?.localizedMessage ?: "Gemini API se jawab nahi aaya, Boss. Key ya internet check karein."
        )
    }

    private fun executeSingleGeminiCall(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        prompt: String,
        bitmapJpegBase64: String?,
        useJsonMime: Boolean
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

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            if (useJsonMime) {
                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                    // Disable slow thinking budget on Gemini 2.5 Flash models for snappy voice replies
                    if (modelId.contains("2.5-flash", ignoreCase = true)) {
                        put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                    }
                }
                put("generationConfig", genConfig)
            }
        }

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models/$modelId:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val apiMsg = runCatching {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw GeminiHttpException(
                    statusCode = resp.code,
                    message = "Gemini ($modelId) HTTP ${resp.code}: ${apiMsg.ifBlank { "request failed" }}"
                )
            }
            return extractNonThoughtGeminiText(rawBody)
        }
    }

    private fun callOpenAiWithModelFallbacks(
        config: ActiveApiKeyConfig,
        systemPrompt: String?,
        userPrompt: String,
        bitmapJpegBase64: String?,
        preferJsonObject: Boolean
    ): String {
        val modelsToTry = buildCandidateModels(config)
        var lastException: Exception? = null

        for (modelId in modelsToTry) {
            try {
                return executeSingleOpenAiCall(
                    config = config,
                    modelId = modelId,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    bitmapJpegBase64 = bitmapJpegBase64,
                    useJsonObject = preferJsonObject
                )
            } catch (e: GeminiHttpException) {
                Log.w(TAG, "${config.provider.id} model $modelId HTTP ${e.statusCode}: ${e.message}")
                lastException = e
                // Many OpenRouter / Groq / DeepSeek models reject response_format=json_object with HTTP 400
                if (e.statusCode == 400 && preferJsonObject) {
                    val plainRetry = runCatching {
                        executeSingleOpenAiCall(
                            config = config,
                            modelId = modelId,
                            systemPrompt = systemPrompt,
                            userPrompt = userPrompt,
                            bitmapJpegBase64 = bitmapJpegBase64,
                            useJsonObject = false
                        )
                    }.getOrNull()
                    if (!plainRetry.isNullOrBlank()) return plainRetry
                }
                if (e.statusCode == 401 || e.statusCode == 402 || e.statusCode == 403) {
                    throw IllegalStateException(e.message ?: "${config.provider.displayName} key error (${e.statusCode}).")
                }
            } catch (e: Exception) {
                Log.w(TAG, "${config.provider.id} model $modelId failed", e)
                lastException = e
            }
        }

        throw IllegalStateException(
            lastException?.localizedMessage ?: "${config.provider.displayName} API se jawab nahi aaya, Boss."
        )
    }

    private fun executeSingleOpenAiCall(
        config: ActiveApiKeyConfig,
        modelId: String,
        systemPrompt: String?,
        userPrompt: String,
        bitmapJpegBase64: String?,
        useJsonObject: Boolean
    ): String {
        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        val userContent: Any = if (bitmapJpegBase64.isNullOrBlank()) {
            userPrompt
        } else {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", userPrompt))
                .put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$bitmapJpegBase64"))
                )
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            put("temperature", 0.3)
            if (useJsonObject) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

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
                throw GeminiHttpException(
                    statusCode = resp.code,
                    message = "${config.provider.displayName} ($modelId) HTTP ${resp.code}: ${msg.ifBlank { "request failed" }}"
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
                throw IllegalStateException("${config.provider.displayName} se khali jawab aaya, Boss.")
            }
            return stripThinkBlocks(content)
        }
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

    private fun buildDecisionJsonInstruction(actionTypes: List<String>): String = """
        OUTPUT FORMAT (STRICT JSON OBJECT ONLY — NO MARKDOWN CODE FENCES):
        Return exactly ONE valid JSON object with these keys:
        {
          "spokenReply": "1 to 3 short sentences in Boss's language (default Hinglish Roman script). Most important takeaway FIRST. No URLs.",
          "actionType": "MUST be one of: ${actionTypes.joinToString(", ")}",
          "target": "button label / app name / ticker / city / URL / HUD mode / setting or empty string",
          "recipient": "contact name or handle or empty string",
          "phone": "phone number or currency or reminder time or empty string",
          "subject": "subject / memory key / trigger or empty string",
          "messageBody": "message / email draft / image prompt / text to type or empty string",
          "boardTitle": "title if saving long report to Maya Board, else empty string",
          "boardCategory": "one of: research, marketing, reviews, email, market, note, dashboard",
          "boardContent": "full Markdown for Maya Board if long report, else empty string",
          "hour": 7,
          "minute": 0,
          "numberVal": 50,
          "missionSteps": []
        }
    """.trimIndent()

    private fun encodeBitmapToJpegBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private class GeminiHttpException(
        val statusCode: Int,
        override val message: String
    ) : RuntimeException(message)

    companion object {
        fun extractNonThoughtGeminiText(rawBody: String): String {
            val root = JSONObject(rawBody)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            if (candidate == null) {
                val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw IllegalStateException(
                    if (blockReason.isNotBlank()) "Safety filter ($blockReason) ki wajah se response ruka, Boss."
                    else "Gemini API se empty candidate aaya, Boss."
                )
            }
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini API response me text parts nahi mile, Boss.")

            val nonThought = StringBuilder()
            val fallbackThought = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                val text = p.optString("text", "")
                if (text.isEmpty()) continue
                if (p.optBoolean("thought", false)) {
                    fallbackThought.append(text)
                } else {
                    nonThought.append(text)
                }
            }
            val finalStr = nonThought.toString().trim().ifBlank { fallbackThought.toString().trim() }
            if (finalStr.isBlank()) {
                throw IllegalStateException("Gemini response text khali aaya, Boss.")
            }
            return stripThinkBlocks(finalStr)
        }

        fun stripThinkBlocks(raw: String): String {
            return raw.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
        }

        fun parseAgentDecisionResilient(
            rawResponse: String,
            supportedActionTypes: List<String>
        ): AgentDecision {
            val cleaned = stripThinkBlocks(rawResponse)
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = tryExtractJsonObject(cleaned)
            if (json != null) {
                val reply = listOf("spokenReply", "reply", "response", "message", "text", "answer")
                    .firstNotNullOfOrNull { key -> json.optString(key, "").trim().takeIf { it.isNotBlank() } }
                    ?: "Ji Boss, dekh rahi hoon."

                val rawAction = json.optString("actionType", "NONE").trim().uppercase(Locale.ROOT)
                val validAction = if (rawAction in supportedActionTypes) rawAction else "NONE"

                val stepsArray = json.optJSONArray("missionSteps")
                val steps = if (stepsArray != null) {
                    (0 until stepsArray.length()).mapNotNull { idx ->
                        stepsArray.optString(idx).takeIf { it.isNotBlank() }
                    }
                } else emptyList()

                val rawCat = json.optString("boardCategory", "note").trim().lowercase(Locale.ROOT)
                val validCat = if (rawCat in setOf("research", "marketing", "reviews", "email", "market", "note", "dashboard")) {
                    rawCat
                } else "note"

                return AgentDecision(
                    spokenReply = reply,
                    actionType = validAction,
                    target = json.optString("target", ""),
                    recipient = json.optString("recipient", ""),
                    phone = json.optString("phone", ""),
                    subject = json.optString("subject", ""),
                    messageBody = json.optString("messageBody", ""),
                    boardTitle = json.optString("boardTitle", ""),
                    boardCategory = validCat,
                    boardContent = json.optString("boardContent", ""),
                    hour = json.optInt("hour", 7),
                    minute = json.optInt("minute", 0),
                    numberVal = json.optInt("numberVal", 50),
                    missionSteps = steps
                )
            }

            // Plain-text fallback: if the model replied directly in natural text instead of JSON, never fail!
            val fallbackReply = cleaned.take(600).ifBlank { "Ji Boss, main sun rahi hoon." }
            return AgentDecision(
                spokenReply = fallbackReply,
                actionType = "NONE"
            )
        }

        private fun tryExtractJsonObject(text: String): JSONObject? {
            if (text.startsWith("{") && text.endsWith("}")) {
                runCatching { return JSONObject(text) }
            }
            // Scan from the last '}' backwards to find a valid balanced JSON object
            val lastClose = text.lastIndexOf('}')
            if (lastClose <= 0) return null
            var searchFrom = 0
            while (searchFrom < lastClose) {
                val openIdx = text.indexOf('{', searchFrom)
                if (openIdx < 0 || openIdx >= lastClose) break
                val slice = text.substring(openIdx, lastClose + 1)
                val parsed = runCatching { JSONObject(slice) }.getOrNull()
                if (parsed != null) return parsed
                searchFrom = openIdx + 1
            }
            return null
        }
    }
}
