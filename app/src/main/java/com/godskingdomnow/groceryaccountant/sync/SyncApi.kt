package com.godskingdomnow.groceryaccountant.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/* Messages exchanged with the family app's app_api.php (see that file for the rules). */

@Serializable
data class UserDto(
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    val role: String = "",
    @SerialName("family_name") val familyName: String = "",
)

@Serializable
data class LoginReq(val username: String, val password: String, val device: String)

@Serializable
data class LoginRes(val ok: Boolean = false, val error: String? = null, val token: String? = null, val user: UserDto? = null)

@Serializable
data class SimpleRes(val ok: Boolean = false, val error: String? = null, val user: UserDto? = null)

@Serializable
data class ItemDto(
    val cid: String? = null,
    val rid: Long? = null,
    @SerialName("list_cid") val listCid: String? = null,
    @SerialName("list_rid") val listRid: Long? = null,
    val name: String = "",
    val size: String = "",
    val category: String = "",
    val price: Double? = null,
    val qty: Int = 1,
    val taxable: Int = 0,
    val checked: Int = 0,
    val sort: Int = 0,
    val snap: Int = 1,
    @SerialName("wic_cat") val wicCat: String? = null,
    @SerialName("wic_units") val wicUnits: Double = 1.0,
    @SerialName("coupon_type") val couponType: String? = null,
    @SerialName("coupon_value") val couponValue: Double? = null,
    @SerialName("coupon_each") val couponEach: Int = 0,
    @SerialName("coupon_mfr") val couponMfr: Int = 0,
    val deleted: Int = 0,
)

@Serializable
data class ListDto(
    val cid: String? = null,
    val rid: Long? = null,
    val name: String = "",
    val store: String = "",
    @SerialName("tax_rate") val taxRate: Double = 0.0,
    val sort: Int = 0,
    val deleted: Int = 0,
    val items: List<ItemDto> = emptyList(),
)

@Serializable
data class CustomFoodDto(val label: String = "", val unit: String = "each")

@Serializable
data class BenefitsDto(
    @SerialName("wic_on") val wicOn: Int = 0,
    @SerialName("snap_on") val snapOn: Int = 0,
    @SerialName("snap_balance") val snapBalance: Double = 0.0,
    @SerialName("cvb_balance") val cvbBalance: Double = 0.0,
    // the website may send numbers or strings here, so read them loosely
    @SerialName("wic_foods") val wicFoods: JsonElement = JsonObject(emptyMap()),
    @SerialName("wic_custom") val wicCustom: JsonElement = JsonObject(emptyMap()),
    @SerialName("tax_mfr") val taxMfr: Int = 1,
    val exists: Int = 0,
) {
    fun foods(): Map<String, String> =
        (wicFoods as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: "" } ?: emptyMap()

    fun custom(): Map<String, CustomFoodDto> =
        (wicCustom as? JsonObject)?.mapNotNull { (k, v) ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val label = o["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            k to CustomFoodDto(label, o["unit"]?.jsonPrimitive?.contentOrNull ?: "each")
        }?.toMap() ?: emptyMap()
}

@Serializable
data class SyncReq(
    val base: String,
    val lists: List<ListDto>,
    val items: List<ItemDto>,
    val benefits: BenefitsDto? = null,
)

@Serializable
data class IdMapDto(val lists: Map<String, Long> = emptyMap(), val items: Map<String, Long> = emptyMap())

@Serializable
data class SnapshotDto(
    val lists: List<ListDto> = emptyList(),
    val benefits: BenefitsDto = BenefitsDto(),
    @SerialName("can_edit_benefits") val canEditBenefits: Int = 0,
)

@Serializable
data class SyncRes(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("server_time") val serverTime: String = "",
    val conflicts: Int = 0,
    val map: IdMapDto = IdMapDto(),
    val snapshot: SnapshotDto = SnapshotDto(),
    val user: UserDto? = null,
)

/** Thrown when the website says this phone isn't signed in any more. */
class SignedOutException(message: String) : IOException(message)

class SyncApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun <Req, Res> post(
        baseUrl: String, action: String, token: String?,
        body: Req, reqSer: KSerializer<Req>, resSer: KSerializer<Res>,
    ): Res = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/app_api.php?action=" + action
        val b = Request.Builder().url(url).post(json.encodeToString(reqSer, body).toRequestBody(jsonType))
        if (!token.isNullOrEmpty()) {
            b.header("Authorization", "Bearer $token")
            b.header("X-App-Token", token)   // some hosts drop the Authorization header
        }
        client.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 && !token.isNullOrEmpty()) {
                val msg = runCatching { json.decodeFromString(SimpleRes.serializer(), text).error }.getOrNull()
                throw SignedOutException(msg ?: "This phone was signed out.")
            }
            try {
                json.decodeFromString(resSer, text)
            } catch (e: Exception) {
                throw IOException(if (resp.isSuccessful) "The website sent something unexpected." else "The website answered ${resp.code}.")
            }
        }
    }

    suspend fun login(baseUrl: String, username: String, password: String, device: String): LoginRes =
        post(baseUrl, "login", null, LoginReq(username, password, device), LoginReq.serializer(), LoginRes.serializer())

    suspend fun logout(baseUrl: String, token: String): SimpleRes =
        post(baseUrl, "logout", token, SimpleRes(true), SimpleRes.serializer(), SimpleRes.serializer())

    suspend fun sync(baseUrl: String, token: String, req: SyncReq): SyncRes =
        post(baseUrl, "sync", token, req, SyncReq.serializer(), SyncRes.serializer())
}
