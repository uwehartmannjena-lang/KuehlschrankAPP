package com.example.myapplication.data

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BringApi(private val client: HttpClient) {

    private var accessToken: String? = null
    private var userUuid: String? = null

    // 1. Bei Bring! anmelden und Token holen
    suspend fun login(email: String, pass: String): Boolean {
        return try {
            val response = client.submitForm(
                url = "https://api.getbring.com/rest/v2/bringauth/login",
                formParameters = parameters {
                    append("email", email.trim())
                    append("password", pass.trim())
                }
            ) {
                header("X-BRING-COUNTRY", "DE")
                header("X-BRING-CLIENT", "android")
            }
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                accessToken = json["access_token"]?.jsonPrimitive?.content
                userUuid = json["uuid"]?.jsonPrimitive?.content
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 2. Deine Liste "Kühlschrank auffüllen" suchen
    suspend fun findListUuid(targetListName: String): String? {
        val token = accessToken ?: return null
        val uuid = userUuid ?: return null
        return try {
            val response = client.get("https://api.getbring.com/rest/v2/bringusers/$uuid/lists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-BRING-COUNTRY", "DE")
            }
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val lists = json["lists"]?.jsonArray ?: return null
                for (l in lists) {
                    val obj = l.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    if (name.contains(targetListName, ignoreCase = true)) {
                        return obj["listUuid"]?.jsonPrimitive?.content
                    }
                }
                // Fallback: Nimm die erste Liste, falls der Name abweicht
                lists.firstOrNull()?.jsonObject?.get("listUuid")?.jsonPrimitive?.content
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // 3. Artikel direkt auf die Bring!-Kacheln schieben (Echter PUT-Aufruf!)
    suspend fun exportToBring(listUuid: String, items: List<ShoppingItem>): Boolean {
        val token = accessToken ?: return false
        return try {
            for (item in items) {
                val spec = if (item.quantity > 1) "${item.quantity} ${item.unit}" else ""
                client.submitForm(
                    url = "https://api.getbring.com/rest/v2/bringlists/$listUuid",
                    formParameters = parameters {
                        append("purchase", item.name)
                        append("specification", spec)
                    }
                ) {
                    method = HttpMethod.Put
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-BRING-COUNTRY", "DE")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class BringList(val uuid: String, val name: String)

    suspend fun getLists(): List<BringList> {
        val token = accessToken ?: return emptyList()
        val uuid = userUuid ?: return emptyList()
        return try {
            val response = client.get("https://api.getbring.com/rest/v2/bringusers/$uuid/lists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-BRING-COUNTRY", "DE")
            }
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val listsArray = json["lists"]?.jsonArray ?: return emptyList()
                listsArray.map { 
                    val obj = it.jsonObject
                    BringList(
                        uuid = obj["listUuid"]?.jsonPrimitive?.content ?: "",
                        name = obj["name"]?.jsonPrimitive?.content ?: ""
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
