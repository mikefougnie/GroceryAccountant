package com.godskingdomnow.groceryaccountant.data

import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.abs

/* ---------- money ---------- */

/** "$1,234.56" (or "−$3.00" for negatives). */
fun money(cents: Long): String {
    val s = String.format(Locale.US, "$%,.2f", abs(cents) / 100.0)
    return if (cents < 0) "−$s" else s
}

/** "4.99", "$4.99", "4,99" -> 499 cents; blank or nonsense -> null. */
fun parseCents(text: String): Long? {
    val t = text.trim().replace("$", "").replace(",", ".").replace(" ", "")
    if (t.isEmpty()) return null
    val d = t.toDoubleOrNull() ?: return null
    if (d < 0) return null
    return Math.round(d * 100).coerceAtMost(9_999_999_99L)
}

fun centsText(cents: Long?): String = if (cents == null) "" else String.format(Locale.US, "%.2f", cents / 100.0)

fun parseNumber(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

/** 2.0 -> "2", 2.5 -> "2.5" */
fun trimNumber(d: Double): String =
    if (d == Math.floor(d) && abs(d) < 1e12) d.toLong().toString() else String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

/* ---------- WIC foods saved as JSON on the benefits row ---------- */

@Serializable
data class CustomFood(val label: String, val unit: String = "each")

private val json = Json { ignoreUnknownKeys = true }
private val foodsSer = MapSerializer(String.serializer(), String.serializer())
private val customSer = MapSerializer(String.serializer(), CustomFood.serializer())

fun BenefitsEntity.wicFoods(): Map<String, String> =
    runCatching { json.decodeFromString(foodsSer, wicFoodsJson) }.getOrDefault(emptyMap())

fun BenefitsEntity.wicCustom(): Map<String, CustomFood> =
    runCatching { json.decodeFromString(customSer, wicCustomJson) }.getOrDefault(emptyMap())

fun encodeFoods(m: Map<String, String>): String = json.encodeToString(foodsSer, m)
fun encodeCustom(m: Map<String, CustomFood>): String = json.encodeToString(customSer, m)

/** Label and unit for a WIC food key (built-in or one the family added). */
fun BenefitsEntity.foodInfo(key: String): ShopEngine.WicFood {
    ShopEngine.wicFood(key)?.let { return it }
    val c = wicCustom()[key]
    return if (c != null) ShopEngine.WicFood(key, c.label, c.unit) else ShopEngine.WicFood(key, key, "")
}

/* ---------- to the calculator ---------- */

fun ItemEntity.toEngine() = ShopEngine.Item(
    id = id,
    priceCents = priceCents ?: 0,
    qty = qty,
    taxable = taxable,
    checked = checked,
    snap = snap,
    wicCat = wicCat,
    wicUnits = wicUnits,
    coupon = ShopEngine.CouponType.of(couponType)?.let {
        ShopEngine.Coupon(it, couponValue ?: 0.0, couponEach, couponMfr)
    },
)

fun settingsFor(list: ShopListEntity?, b: BenefitsEntity?) = ShopEngine.Settings(
    taxRatePercent = list?.taxRate ?: 0.0,
    wicOn = b?.wicOn == true,
    snapOn = b?.snapOn == true,
    snapBalanceCents = b?.snapBalanceCents ?: 0,
    cvbBalanceCents = b?.cvbBalanceCents ?: 0,
    wicFoods = b?.wicFoods()?.mapNotNull { (k, v) -> parseNumber(v)?.let { k to it } }?.toMap() ?: emptyMap(),
    taxMfrCoupons = b?.taxMfr ?: true,
)
