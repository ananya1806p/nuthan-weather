package com.ananya.nuthanweather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(val role: String, val text: String)

object GeminiApi {
    suspend fun reply(apiKey: String, history: List<ChatTurn>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key to start chatting." }

        val contents = JSONArray().apply {
            history.forEach { turn ->
                put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("parts", JSONArray().put(JSONObject().put("text", turn.text))),
                )
            }
        }
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "You are Nuthan, a friendly, accurate assistant. Use clear language. If unsure, say so.",
                        ),
                    ),
                ),
            )
            .put("contents", contents)

        val connection = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
        ).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("x-goog-api-key", apiKey)
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val response = runCatching { JSONObject(responseBody) }.getOrElse {
                throw GeminiServiceException("Gemini returned an unreadable response. Please try again.")
            }
            if (status !in 200..299) {
                val detail = response.optJSONObject("error")?.optString("message")
                    ?.takeIf(String::isNotBlank)
                throw GeminiServiceException(
                    detail ?: "Gemini returned an error (HTTP $status). Check your key and try again.",
                )
            }

            val parts = response.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw GeminiServiceException("Gemini returned an empty response. Please try again.")
            val answer = buildString {
                for (index in 0 until parts.length()) {
                    append(parts.optJSONObject(index)?.optString("text").orEmpty())
                }
            }.trim()
            if (answer.isBlank()) {
                throw GeminiServiceException("Gemini returned an empty response. Please try again.")
            }
            answer
        } finally {
            connection.disconnect()
        }
    }
}

class GeminiServiceException(message: String) : Exception(message)
