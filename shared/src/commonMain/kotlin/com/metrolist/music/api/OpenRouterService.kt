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

object OpenRouterService {
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
        model: String = "google/gemini-2.5-pro",
        maxRetries: Int = 3
    ): Result<List<String>> {
        var currentAttempt = 0
        if (text.isBlank()) return Result.failure(Exception("Input text is empty"))
        
        val lines = text.lines()
        val lineCount = lines.size
        
        while (currentAttempt < maxRetries) {
            try {
                val jsonBody = buildJsonObject {
                    put("model", model)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "system")
                            put("content", "You are a professional translator. Translate the following text to . Maintain the exact same number of lines and empty lines. Do not add any explanations.")
                        })
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", text)
                        })
                    })
                }

                val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
                    header("Authorization", "Bearer $apiKey.trim()")
                    header("HTTP-Referer", "https://github.com/fast4x/RiMusic")
                    header("X-Title", "RiMusic")
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
                        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "HTTP ${response.status.value}"
                    } catch (e: Exception) {
                        "HTTP ${response.status.value}"
                    }
                    return Result.failure(Exception("Translation failed: $errorMsg"))
                }

                val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
                val choices = jsonResponse["choices"]?.jsonArray
                if (choices != null && choices.size > 0) {
                    val translatedText = choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
                    val translatedLines = translatedText.lines()
                    
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
