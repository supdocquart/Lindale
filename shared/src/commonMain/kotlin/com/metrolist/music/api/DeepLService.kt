package com.metrolist.music.api
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.delay

object DeepLService {
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 90000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
    }

    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        formality: String = "default",
        maxRetries: Int = 3
    ): Result<List<String>> {
        var currentAttempt = 0
        
        if (text.isBlank()) {
            return Result.failure(Exception("Input text is empty"))
        }
        
        val lines = text.lines()
        val lineCount = lines.size
        
        val deeplLangCode = when (targetLanguage.lowercase()) {
            "zh", "zh-cn", "zh-hans" -> "ZH"
            "zh-tw", "zh-hant" -> "ZH"
            "en", "en-us" -> "EN-US"
            "en-gb" -> "EN-GB"
            "pt", "pt-pt" -> "PT-PT"
            "pt-br" -> "PT-BR"
            else -> targetLanguage.uppercase().take(2)
        }
        
        val baseUrl = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }
        
        while (currentAttempt < maxRetries) {
            try {
                val jsonBody = buildJsonObject {
                    put("text", buildJsonArray { lines.forEach { add(it) } })
                    put("target_lang", deeplLangCode)
                    if (formality != "default") {
                        put("formality", formality)
                    }
                    put("preserve_formatting", true)
                }

                val response = client.post(baseUrl) {
                    header("Authorization", "DeepL-Auth-Key $apiKey.trim()")
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody.toString())
                }

                val responseBody = response.bodyAsText()

                if (!response.status.isSuccess()) {
                    if (response.status.value >= 500) {
                        currentAttempt++
                        delay(1000L * currentAttempt)
                        continue
                    }
                    
                    val errorMsg = try {
                        val obj = Json.parseToJsonElement(responseBody).jsonObject
                        obj["message"]?.jsonPrimitive?.content ?: "HTTP ${response.status.value}: ${response.status.description}"
                    } catch (e: Exception) {
                        "HTTP ${response.status.value}: ${response.status.description}"
                    }
                    return Result.failure(Exception("Translation failed: $errorMsg"))
                }

                val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
                val translations = jsonResponse["translations"]?.jsonArray
                if (translations != null && translations.size > 0) {
                    val translatedLines = translations.map { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                    
                    if (translatedLines.size == lineCount) {
                        return Result.success(translatedLines)
                    } else if (translatedLines.size > lineCount) {
                        return Result.success(translatedLines.take(lineCount))
                    } else {
                        val paddedLines = translatedLines.toMutableList()
                        while (paddedLines.size < lineCount) {
                            paddedLines.add("")
                        }
                        return Result.success(paddedLines)
                    }
                }
            } catch (e: Exception) {
                if (currentAttempt == maxRetries - 1) {
                    return Result.failure(e)
                }
            }
            currentAttempt++
            delay(1000L * currentAttempt)
        }
        return Result.failure(Exception("Max retries exceeded"))
    }
}

