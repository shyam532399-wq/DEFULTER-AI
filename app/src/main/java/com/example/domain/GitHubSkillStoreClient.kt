package com.example.domain

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class GitHubRemoteSkill(
    val id: String,
    val name: String,
    val trigger: String,
    val description: String,
    val stepsText: String,
    val sourceRepo: String
)

/**
 * Fetches and parses installable Maya skills from GitHub repositories —
 * supporting both `HunterisLive-1/mobile-maya-skills` (`index.json` + `skills/<name>.md` playbooks)
 * and general repositories such as `HunterisLive-1/maya-ui`.
 */
class GitHubSkillStoreClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Built-in `HunterisLive-1/maya-ui` HUD Skill Pack so Boss can also activate all HUD voice playbooks in 1 tap.
     */
    fun builtInMayaUiHudSkills(): List<GitHubRemoteSkill> = listOf(
        GitHubRemoteSkill(
            id = "maya_ui_system_hud",
            name = "Maya System HUD Diagnostics",
            trigger = "System HUD status ya CPU RAM batao",
            description = "HunterisLive-1/maya-ui live CPU, RAM, storage, aur FPS report",
            stepsText = "1. Phone ka live CPU %, RAM GB, aur FPS padho (GET_SYSTEM_HUD).\n2. Boss ko 2 crisp Hinglish lines me health batao aur zaroorat ho to Board pe dashboard report pin karo.",
            sourceRepo = "HunterisLive-1/maya-ui"
        ),
        GitHubRemoteSkill(
            id = "maya_ui_net_thermal",
            name = "Live Net Speed & Thermal Guard",
            trigger = "Network speed aur phone temperature check karo",
            description = "Live upload/download KB/s-MB/s aur °C thermal alert check",
            stepsText = "1. Live download ↓ aur upload ↑ throughput aur battery/device °C check karo (GET_SYSTEM_HUD).\n2. Agar temp > 40°C ho to Boss ko cool-down salah do.",
            sourceRepo = "HunterisLive-1/maya-ui"
        ),
        GitHubRemoteSkill(
            id = "maya_ui_quantum_mode",
            name = "Quantum & Holographic HUD Switcher",
            trigger = "Quantum HUD ya Holographic mode lagao",
            description = "60 FPS Quantum Grid aur Holographic Scanline HUD background switch",
            stepsText = "1. CONTROL_HUD action se requested theme ('quantum', 'holographic', ya 'minimal') activate karo.\n2. Cockpit HUD ko expand karke reactive AI Orb aur neon spectrum dikhao.",
            sourceRepo = "HunterisLive-1/maya-ui"
        ),
        GitHubRemoteSkill(
            id = "maya_ui_floating_overlay",
            name = "Draggable Floating Mini-HUD",
            trigger = "Floating HUD on ya band karo",
            description = "Cross-tab draggable cyber-pill HUD with live Orb, Net speed, aur clean close",
            stepsText = "1. CONTROL_HUD ('float_on' ya 'float_off') chala kar Floating Mini-HUD toggle karo.\n2. Boss ise kisi bhi tab ke upar drag kar sakte hain ya × se band kar sakte hain.",
            sourceRepo = "HunterisLive-1/maya-ui"
        )
    )

    fun normalizeRepoSlug(rawInput: String): String? {
        val cleaned = rawInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2 && !parts[0].contains(" ") && !parts[1].contains(" ")) {
            "${parts[0]}/${parts[1]}"
        } else {
            null
        }
    }

    suspend fun fetchSkillsFromRepo(rawRepoInput: String): Result<Pair<String, List<GitHubRemoteSkill>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val slug = normalizeRepoSlug(rawRepoInput)
                    ?: throw IllegalArgumentException("Valid GitHub URL ya 'owner/repo' daalein (jaise HunterisLive-1/maya-ui).")

                // Strategy 1: Try `index.json` playbook store format (e.g., HunterisLive-1/mobile-maya-skills)
                val fromIndex = tryFetchPlaybookIndexSkills(slug)
                if (fromIndex.isNotEmpty()) {
                    return@runCatching slug to fromIndex
                }

                // Strategy 2: Try `/skills` directory via GitHub Contents API
                val fromSkillsDir = tryFetchSkillsDirectory(slug)
                if (fromSkillsDir.isNotEmpty()) {
                    return@runCatching slug to fromSkillsDir
                }

                // Strategy 3: Inspect repository metadata, modules & README features (e.g., HunterisLive-1/maya-ui)
                val extracted = extractRepoFeatureSkills(slug)
                if (extracted.isEmpty()) {
                    throw IllegalStateException("GitHub pe '$slug' se skills read nahi ho sakein.")
                }
                slug to extracted
            }
        }

    private fun tryFetchPlaybookIndexSkills(slug: String): List<GitHubRemoteSkill> {
        return try {
            val indexUrl = "https://raw.githubusercontent.com/$slug/main/index.json"
            val indexBody = executeGet(indexUrl, acceptJson = true) ?: return emptyList()
            val arr = when {
                indexBody.trimStart().startsWith("[") -> JSONArray(indexBody)
                indexBody.trimStart().startsWith("{") -> {
                    val obj = JSONObject(indexBody)
                    obj.optJSONArray("skills") ?: obj.optJSONArray("items") ?: JSONArray()
                }
                else -> JSONArray()
            }
            val parsed = mutableListOf<GitHubRemoteSkill>()
            for (i in 0 until minOf(10, arr.length())) {
                val item = arr.optJSONObject(i) ?: continue
                val rawName = item.optString("name", "").trim()
                val filePath = item.optString("file", item.optString("path", "")).trim().removePrefix("/")
                val desc = item.optString("description", "GitHub Maya Playbook").trim()
                val mdPath = when {
                    filePath.isNotBlank() -> filePath
                    rawName.isNotBlank() -> "skills/$rawName.md"
                    else -> ""
                }
                val mdContent = if (mdPath.isNotBlank()) {
                    executeGet("https://raw.githubusercontent.com/$slug/main/$mdPath", acceptJson = false)
                } else null

                val skill = parseSkillMarkdown(
                    fallbackName = rawName.ifBlank { "Skill ${i + 1}" },
                    fallbackDescription = desc,
                    markdown = mdContent.orEmpty(),
                    sourceRepo = slug
                )
                parsed.add(skill)
            }
            parsed
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryFetchSkillsDirectory(slug: String): List<GitHubRemoteSkill> {
        return try {
            val dirJson = executeGet("https://api.github.com/repos/$slug/contents/skills", acceptJson = true)
                ?: return emptyList()
            val files = JSONArray(dirJson)
            val parsed = mutableListOf<GitHubRemoteSkill>()
            for (i in 0 until files.length()) {
                if (parsed.size >= 8) break
                val f = files.optJSONObject(i) ?: continue
                val fileName = f.optString("name", "")
                val downloadUrl = f.optString("download_url", "")
                if (fileName.endsWith(".md", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    val md = executeGet(downloadUrl, acceptJson = false).orEmpty()
                    parsed.add(
                        parseSkillMarkdown(
                            fallbackName = fileName.removeSuffix(".md"),
                            fallbackDescription = "Imported from $slug/skills/$fileName",
                            markdown = md,
                            sourceRepo = slug
                        )
                    )
                }
            }
            parsed
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractRepoFeatureSkills(slug: String): List<GitHubRemoteSkill> {
        val repoBody = executeGet("https://api.github.com/repos/$slug", acceptJson = true)
            ?: throw IllegalStateException("GitHub repo '$slug' nahi mila ya private hai.")
        val repoJson = JSONObject(repoBody)
        val description = sanitizeUntrusted(repoJson.optString("description", "$slug integration"))
        val defaultBranch = repoJson.optString("default_branch", "main").ifBlank { "main" }

        val readmeText = executeGet("https://raw.githubusercontent.com/$slug/$defaultBranch/README.md", acceptJson = false)
            ?: executeGet("https://raw.githubusercontent.com/$slug/master/README.md", acceptJson = false)
            ?: ""

        // Extract top bullet features from README safely (Golden Rule 8 sanitization)
        val featureLines = readmeText.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { sanitizeUntrusted(it.removePrefix("- ").removePrefix("* ")) }
            .filter { it.length in 12..180 && !it.lowercase(Locale.ROOT).startsWith("http") }
            .take(6)
            .toList()

        val results = mutableListOf<GitHubRemoteSkill>()

        // If this is HunterisLive-1/maya-ui (or a Maya HUD repo), include the native HUD playbooks enriched with live repo data
        if (slug.lowercase(Locale.ROOT).endsWith("/maya-ui")) {
            results.addAll(builtInMayaUiHudSkills())
        }

        if (featureLines.isNotEmpty()) {
            featureLines.chunked(2).forEachIndexed { idx, group ->
                val titleBase = group.first().substringBefore(":").take(48).trim()
                    .ifBlank { "${slug.substringAfter("/")} Feature ${idx + 1}" }
                results.add(
                    GitHubRemoteSkill(
                        id = "${slug.replace("/", "_")}_feat_$idx",
                        name = titleBase.take(80),
                        trigger = "Run $titleBase",
                        description = description.take(140),
                        stepsText = group.mapIndexed { stepIdx, line -> "${stepIdx + 1}. $line" }
                            .joinToString("\n")
                            .take(1800),
                        sourceRepo = slug
                    )
                )
            }
        } else if (results.isEmpty()) {
            results.add(
                GitHubRemoteSkill(
                    id = "${slug.replace("/", "_")}_overview",
                    name = "${slug.substringAfter("/")} Workflow",
                    trigger = "${slug.substringAfter("/")} chalaao",
                    description = description.take(140),
                    stepsText = "1. Repo context ($slug): $description\n2. Boss ke command ke hisaab se Maya HUD / Board action chalaao.",
                    sourceRepo = slug
                )
            )
        }

        return results.distinctBy { it.name.lowercase(Locale.ROOT) }.take(8)
    }

    internal fun parseSkillMarkdown(
        fallbackName: String,
        fallbackDescription: String,
        markdown: String,
        sourceRepo: String
    ): GitHubRemoteSkill {
        var name = fallbackName
        var description = fallbackDescription
        var body = markdown.trim()

        if (body.startsWith("---")) {
            val endIdx = body.indexOf("\n---", startIndex = 3)
            if (endIdx > 3) {
                val frontmatter = body.substring(3, endIdx)
                body = body.substring(endIdx + 4).trim()
                frontmatter.lines().forEach { line ->
                    val key = line.substringBefore(":", "").trim().lowercase(Locale.ROOT)
                    val value = line.substringAfter(":", "").trim().trim('"', '\'')
                    if (key == "name" && value.isNotBlank()) name = value
                    if (key == "description" && value.isNotBlank()) description = value
                }
            }
        }

        val cleanName = sanitizeUntrusted(name).replace('-', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            .take(90)
        val cleanDesc = sanitizeUntrusted(description).take(180)
        val cleanBody = sanitizeUntrusted(body)
            .ifBlank { "1. Trigger condition: $cleanDesc\n2. Boss ke command ko Hinglish me step-by-step poora karo." }
            .take(3500)

        return GitHubRemoteSkill(
            id = "${sourceRepo}_${cleanName}".lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_"),
            name = cleanName,
            trigger = cleanDesc.take(140).ifBlank { cleanName },
            description = cleanDesc,
            stepsText = cleanBody,
            sourceRepo = sourceRepo
        )
    }

    private fun sanitizeUntrusted(raw: String): String {
        return raw
            .replace(Regex("(?i)ignore (all )?previous instructions"), "[filtered]")
            .replace(Regex("[`*_#>]+"), "")
            .trim()
    }

    private fun executeGet(url: String, acceptJson: Boolean): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "MayaPhoneAgent/1.0 (Android)")
        if (acceptJson) {
            builder.header("Accept", "application/vnd.github+json")
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }
}
