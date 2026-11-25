package com.godskingdomnow.groceryaccountant.calc

import com.godskingdomnow.groceryaccountant.calc.ShopEngine.Coupon
import com.godskingdomnow.groceryaccountant.calc.ShopEngine.CouponType
import com.godskingdomnow.groceryaccountant.calc.ShopEngine.Item
import com.godskingdomnow.groceryaccountant.calc.ShopEngine.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same checks the website's calculator passes (see assets/shop_engine.js). */
class ShopEngineTest {

    private fun c(d: Double) = Math.round(d * 100)

    @Test fun noBenefitsMatchesPlainCalculator() {
        val r = ShopEngine.compute(
            listOf(Item(1, 699, 3, taxable = true), Item(2, 450)),
            Settings(taxRatePercent = 7.0),
        )
        assertEquals(c(25.47), r.totals.net)
        assertEquals(c(1.47), r.totals.tax)
        assertEquals(c(26.94), r.totals.you)
        assertEquals(r.totals.totalAllCash, r.totals.you)
    }

    @Test fun coupons() {
        assertEquals(300L, ShopEngine.couponOff(Coupon(CouponType.AMOUNT, 1.0, each = true), 299, 3, 897))
        assertEquals(100L, ShopEngine.couponOff(Coupon(CouponType.AMOUNT, 1.0), 299, 3, 897))
        assertEquals(200L, ShopEngine.couponOff(Coupon(CouponType.PERCENT, 25.0), 400, 2, 800))
        assertEquals(350L, ShopEngine.couponOff(Coupon(CouponType.BOGO), 350, 3, 1050))
        assertEquals(299L, ShopEngine.couponOff(Coupon(CouponType.AMOUNT, 50.0), 299, 1, 299))
    }

    @Test fun storeVersusManufacturerCouponTax() {
        val store = ShopEngine.compute(listOf(Item(1, 1000, taxable = true, coupon = Coupon(CouponType.AMOUNT, 2.0))), Settings(10.0))
        assertEquals(800L, store.totals.net); assertEquals(80L, store.totals.tax)
        val mfr = ShopEngine.compute(listOf(Item(1, 1000, taxable = true, coupon = Coupon(CouponType.AMOUNT, 2.0, manufacturer = true))), Settings(10.0))
        assertEquals(800L, mfr.totals.net); assertEquals(100L, mfr.totals.tax)
        val off = ShopEngine.compute(listOf(Item(1, 1000, taxable = true, coupon = Coupon(CouponType.AMOUNT, 2.0, manufacturer = true))),
            Settings(10.0, taxMfrCoupons = false))
        assertEquals(80L, off.totals.tax)
    }

    @Test fun snapPaysTaxableItemsFirstAndIsNeverTaxed() {
        val r = ShopEngine.compute(
            listOf(Item(1, 1000), Item(2, 1000, taxable = true), Item(3, 1000, taxable = true, snap = false)),
            Settings(10.0, snapOn = true, snapBalanceCents = 1500),
        )
        assertEquals(1000L, r.line(2)!!.snapPay)   // soda first
        assertEquals(500L, r.line(1)!!.snapPay)
        assertEquals(0L, r.line(2)!!.tax)
        assertEquals(100L, r.line(3)!!.tax)
        assertEquals(0L, r.line(3)!!.snapPay)
        assertEquals(1600L, r.totals.you)
        assertEquals(0L, r.snapLeft)
    }

    @Test fun snapWithManufacturerCouponTaxesOnlyTheCoupon() {
        val r = ShopEngine.compute(listOf(Item(1, 500, taxable = true, coupon = Coupon(CouponType.AMOUNT, 1.0, manufacturer = true))),
            Settings(10.0, snapOn = true, snapBalanceCents = 5000))
        assertEquals(400L, r.line(1)!!.snapPay); assertEquals(10L, r.line(1)!!.tax); assertEquals(10L, r.totals.you)
    }

    @Test fun wicFoodsWholeItemsWhileUnitsLast() {
        val r = ShopEngine.compute(
            listOf(Item(1, 349, 3, wicCat = "milk", wicUnits = 1.0), Item(2, 400, 3, snap = false, wicCat = "cereal", wicUnits = 12.0)),
            Settings(wicOn = true, snapOn = true, snapBalanceCents = 200, wicFoods = mapOf("milk" to 2.0, "cereal" to 36.0)),
        )
        val milk = r.line(1)!!
        assertEquals(2, milk.wicQty); assertEquals(698L, milk.wicPay); assertEquals(200L, milk.snapPay); assertEquals(149L, milk.you)
        assertEquals(3, r.line(2)!!.wicQty); assertEquals(0L, r.line(2)!!.you)
        assertEquals(0.0, r.wicLeft["milk"]!!, 1e-9); assertEquals(0.0, r.wicLeft["cereal"]!!, 1e-9)
        val big = ShopEngine.compute(listOf(Item(1, 400, wicCat = "cereal", wicUnits = 18.0)), Settings(wicOn = true, wicFoods = mapOf("cereal" to 12.0)))
        assertEquals(0, big.line(1)!!.wicQty); assertEquals(400L, big.line(1)!!.you)
        val wicOff = ShopEngine.compute(listOf(Item(1, 300, wicCat = "milk")), Settings(wicOn = false, wicFoods = mapOf("milk" to 4.0)))
        assertEquals(300L, wicOff.line(1)!!.you)
    }

    @Test fun fruitAndVegMoneySplitTender() {
        val r = ShopEngine.compute(
            listOf(Item(1, 500, 2, wicCat = ShopEngine.CVB), Item(2, 199, wicCat = ShopEngine.CVB)),
            Settings(wicOn = true, snapOn = true, cvbBalanceCents = 900, snapBalanceCents = 100),
        )
        assertEquals(900L, r.line(1)!!.cvbPay); assertEquals(100L, r.line(1)!!.snapPay); assertEquals(0L, r.line(1)!!.you)
        assertEquals(0L, r.line(2)!!.cvbPay); assertEquals(199L, r.line(2)!!.you)
        assertEquals(0L, r.cvbLeft)
    }

    @Test fun cartVersusStillToGet() {
        val r = ShopEngine.compute(
            listOf(Item(1, 1000, checked = true), Item(2, 1000), Item(3, 300, 2, checked = true, wicCat = "milk")),
            Settings(snapOn = true, snapBalanceCents = 1200, wicOn = true, wicFoods = mapOf("milk" to 5.0)),
        )
        assertEquals(0L, r.totals.youCart); assertEquals(800L, r.totals.youLeft)
        assertEquals(1000L, r.usedCart.snap); assertEquals(0L, r.usedCart.cvb); assertEquals(2.0, r.usedCart.wic["milk"]!!, 1e-9)
    }

    @Test fun paymentsAlwaysAddUp() {
        val r = ShopEngine.compute(
            listOf(
                Item(1, 777, 3, taxable = true, coupon = Coupon(CouponType.PERCENT, 10.0)),
                Item(2, 250, 4, wicCat = ShopEngine.CVB),
                Item(3, 400, 2, wicCat = "eggs"),
                Item(4, 999, taxable = true, snap = false),
            ),
            Settings(6.25, wicOn = true, snapOn = true, snapBalanceCents = 1000, cvbBalanceCents = 600, wicFoods = mapOf("eggs" to 1.0)),
        )
        val t = r.totals
        assertEquals(t.net + t.tax, t.you + t.wic + t.cvb + t.snap)
        assertTrue(t.tax <= t.taxAllCash)
    }
}
