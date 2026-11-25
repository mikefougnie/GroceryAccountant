package com.godskingdomnow.groceryaccountant.calc

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The shopping calculator: coupons, WIC, SNAP and sales tax.
 * A line-for-line port of the website's assets/shop_engine.js, so the app and
 * the website always agree. All money is in cents (Long).
 *
 * How a line is paid, in register order:
 *  1. The coupon comes off the shelf price.
 *  2. WIC food benefit (milk, eggs, cereal…): WIC pays for whole items while the
 *     family has units of that food left. No tax.
 *  3. WIC fruit & vegetable money (a dollar balance): pays produce; anything over
 *     the balance goes to SNAP or you ("split tender").
 *  4. SNAP pays what's left of SNAP-eligible items, TAXABLE ITEMS FIRST (federal
 *     rule: use SNAP where it saves the most tax). No tax on the SNAP-paid part.
 *  5. You pay the rest, plus sales tax on the taxable part you pay.
 *     Manufacturer coupons: tax can still be due on the coupon's discount
 *     (state rules vary — [Settings.taxMfrCoupons]).
 */
object ShopEngine {

    const val CVB = "cvb" // WIC fruit & vegetable money

    data class WicFood(val key: String, val label: String, val unit: String)

    /** WIC foods with their usual units. Amounts vary by family and state. */
    val WIC_FOODS = listOf(
        WicFood("milk", "Milk", "gal"),
        WicFood("eggs", "Eggs", "dozen"),
        WicFood("cheese", "Cheese", "lb"),
        WicFood("yogurt", "Yogurt", "qt"),
        WicFood("cereal", "Cereal", "oz"),
        WicFood("grains", "Bread & whole grains", "oz"),
        WicFood("juice", "Juice", "oz"),
        WicFood("pbutter", "Peanut butter", "oz"),
        WicFood("beans", "Beans, peas & lentils", "lb"),
        WicFood("fish", "Canned fish", "oz"),
        WicFood("formula", "Infant formula", "cans"),
        WicFood("infcereal", "Infant cereal", "oz"),
        WicFood("babyfv", "Baby food fruits & veg", "oz"),
        WicFood("babymeat", "Baby food meat", "oz"),
    )

    fun wicFood(key: String): WicFood? = WIC_FOODS.firstOrNull { it.key == key }

    enum class CouponType(val code: String) {
        AMOUNT("amt"), PERCENT("pct"), BOGO("bogo");
        companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } }
    }

    data class Coupon(
        val type: CouponType,
        val value: Double = 0.0,        // dollars for AMOUNT, percent for PERCENT
        val each: Boolean = false,      // AMOUNT: off each one instead of off the line
        val manufacturer: Boolean = false,
    )

    data class Item(
        val id: Long,
        val priceCents: Long,           // per item
        val qty: Int = 1,
        val taxable: Boolean = false,
        val checked: Boolean = false,
        val snap: Boolean = true,       // SNAP-eligible
        val wicCat: String? = null,     // CVB, a WIC food key, or null
        val wicUnits: Double = 1.0,     // how much of that WIC food one item uses
        val coupon: Coupon? = null,
    )

    data class Settings(
        val taxRatePercent: Double = 0.0,
        val wicOn: Boolean = false,
        val snapOn: Boolean = false,
        val snapBalanceCents: Long = 0,
        val cvbBalanceCents: Long = 0,
        val wicFoods: Map<String, Double> = emptyMap(), // key -> amount on the card
        val taxMfrCoupons: Boolean = true,
    )

    data class Line(
        val id: Long, val qty: Int, val checked: Boolean, val taxable: Boolean,
        val gross: Long, val coupon: Long, val mfr: Boolean, val net: Long,
        var wicCat: String? = null, var wicQty: Int = 0, var wicUnits: Double = 0.0,
        var wicPay: Long = 0, var cvbPay: Long = 0, var snapPay: Long = 0,
        var rest: Long = 0, val snapOk: Boolean = false, var tax: Long = 0, var you: Long = 0,
    )

    data class Totals(
        val gross: Long, val coupons: Long, val net: Long, val taxAllCash: Long, val tax: Long,
        val wic: Long, val cvb: Long, val snap: Long, val you: Long, val youCart: Long, val youLeft: Long,
        val totalAllCash: Long,
    )

    data class Used(val snap: Long, val cvb: Long, val wic: Map<String, Double>)

    data class Result(
        val lines: List<Line>, val totals: Totals,
        val snapLeft: Long, val cvbLeft: Long, val wicLeft: Map<String, Double>,
        val usedCart: Used,
    ) {
        private val byId = lines.associateBy { it.id }
        fun line(id: Long): Line? = byId[id]
    }

    /** JavaScript's Math.round (halves go up), so results match the website exactly. */
    private fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

    fun couponOff(c: Coupon?, priceCents: Long, qty: Int, grossCents: Long): Long {
        if (c == null) return 0
        val off = when (c.type) {
            CouponType.AMOUNT -> jsRound(c.value * 100) * (if (c.each) qty else 1)
            CouponType.PERCENT -> jsRound(grossCents * min(100.0, max(0.0, c.value)) / 100.0)
            CouponType.BOGO -> (qty / 2) * priceCents
        }
        return max(0, min(grossCents, off))
    }

    fun compute(items: List<Item>, s: Settings): Result {
        val rate = max(0.0, s.taxRatePercent) / 100.0
        var snapLeft = if (s.snapOn) max(0, s.snapBalanceCents) else 0
        var cvbLeft = if (s.wicOn) max(0, s.cvbBalanceCents) else 0
        val wicLeft = HashMap<String, Double>()
        if (s.wicOn) s.wicFoods.forEach { (k, v) -> wicLeft[k] = max(0.0, v) }

        // ---- pass 1: coupons, WIC foods, WIC fruit & veg (list order) ----
        val lines = items.map { it ->
            val qty = max(1, it.qty)
            val price = max(0, it.priceCents)
            val gross = price * qty
            val off = couponOff(it.coupon, price, qty, gross)
            val net = gross - off
            val l = Line(
                id = it.id, qty = qty, checked = it.checked, taxable = it.taxable,
                gross = gross, coupon = off, mfr = it.coupon?.manufacturer == true && off > 0, net = net,
                rest = net, snapOk = s.snapOn && it.snap,
            )
            val cat = if (s.wicOn) it.wicCat else null
            if (cat == CVB) {
                l.wicCat = CVB
                val use = min(cvbLeft, l.rest)
                l.cvbPay = use; l.rest -= use; cvbLeft -= use
            } else if (cat != null && wicLeft.containsKey(cat)) {
                l.wicCat = cat
                val per = if (it.wicUnits > 0) it.wicUnits else 1.0
                val have = wicLeft.getValue(cat)
                val can = min(qty, floor((have + 1e-9) / per).toInt())   // WIC pays for whole items only
                if (can > 0) {
                    val share = jsRound(net.toDouble() * can / qty)
                    l.wicQty = can; l.wicUnits = can * per; l.wicPay = share; l.rest -= share
                    wicLeft[cat] = Math.round((have - can * per) * 1000) / 1000.0
                }
            }
            l
        }

        // ---- pass 2: SNAP — taxable eligible items first, then the rest ----
        fun useSnap(l: Line) {
            if (!l.snapOk || l.rest <= 0 || snapLeft <= 0) return
            val use = min(snapLeft, l.rest)
            l.snapPay += use; l.rest -= use; snapLeft -= use
        }
        lines.filter { it.taxable }.forEach(::useSnap)
        lines.filter { !it.taxable }.forEach(::useSnap)

        // ---- pass 3: tax on what you pay, and totals ----
        var gross = 0L; var coupons = 0L; var net = 0L; var taxAll = 0L; var tax = 0L
        var wic = 0L; var cvb = 0L; var snap = 0L; var you = 0L; var youCart = 0L; var youLeft = 0L
        lines.forEach { l ->
            val mfrBaseAll = if (l.mfr && s.taxMfrCoupons) l.coupon else 0
            val lineTaxAll = if (l.taxable) jsRound((l.net + mfrBaseAll) * rate) else 0
            // WIC-paid items are never taxed; manufacturer-coupon tax only on the non-WIC part
            val nonWicShare = if (l.qty > 0) (l.qty - l.wicQty).toDouble() / l.qty else 1.0
            val mfrBase = if (l.mfr && s.taxMfrCoupons) jsRound(l.coupon * nonWicShare) else 0
            l.tax = if (l.taxable) jsRound((l.rest + mfrBase) * rate) else 0
            l.you = l.rest + l.tax
            gross += l.gross; coupons += l.coupon; net += l.net
            taxAll += lineTaxAll; tax += l.tax
            wic += l.wicPay; cvb += l.cvbPay; snap += l.snapPay; you += l.you
            if (l.checked) youCart += l.you else youLeft += l.you
        }

        // what the checked (in-cart) items use up — for "Done shopping"
        var uSnap = 0L; var uCvb = 0L
        val uWic = LinkedHashMap<String, Double>()
        lines.filter { it.checked }.forEach { l ->
            uSnap += l.snapPay; uCvb += l.cvbPay
            val c = l.wicCat
            if (c != null && c != CVB && l.wicUnits > 0) uWic[c] = (uWic[c] ?: 0.0) + l.wicUnits
        }

        return Result(
            lines = lines,
            totals = Totals(gross, coupons, net, taxAll, tax, wic, cvb, snap, you, youCart, youLeft, net + taxAll),
            snapLeft = snapLeft, cvbLeft = cvbLeft, wicLeft = wicLeft,
            usedCart = Used(uSnap, uCvb, uWic),
        )
    }
}
