package com.godskingdomnow.groceryaccountant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Everything is stored on the phone. Signing in to the family app is optional;
 * when signed in, "shared" lists also sync with the website.
 *
 * Sync bookkeeping on lists and items:
 *   remoteId  – the website's id once the row has been synced (null = not yet)
 *   dirty     – changed on the phone since the last sync
 *   deleted   – removed on the phone, kept until the website hears about it
 *   updatedAt – phone clock, used to tell whether a row changed during a sync
 */

@Entity(tableName = "lists")
data class ShopListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long? = null,
    val name: String,
    val store: String = "",
    val taxRate: Double = 0.0,          // percent, e.g. 7.0
    val sortOrder: Int = 0,
    val shared: Boolean = false,        // synced with the family app
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = ShopListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("listId"), Index("remoteId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long? = null,
    val listId: Long,
    val name: String,
    val size: String = "",              // "2 lb", "12 oz box"…
    val category: String = "",
    val priceCents: Long? = null,       // per item; null = no price yet
    val qty: Int = 1,
    val taxable: Boolean = false,
    val checked: Boolean = false,
    val sortOrder: Int = 0,
    val snap: Boolean = true,           // SNAP-eligible
    val wicCat: String? = null,         // "cvb" (fruit & veg money), a WIC food key, or null
    val wicUnits: Double = 1.0,         // how much of that WIC food one item uses
    val couponType: String? = null,     // "amt" | "pct" | "bogo"
    val couponValue: Double? = null,
    val couponEach: Boolean = false,
    val couponMfr: Boolean = false,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** WIC / SNAP settings — one row. Amounts of WIC foods are kept as typed text. */
@Entity(tableName = "benefits")
data class BenefitsEntity(
    @PrimaryKey val id: Int = 1,
    val wicOn: Boolean = false,
    val snapOn: Boolean = false,
    val snapBalanceCents: Long = 0,
    val cvbBalanceCents: Long = 0,      // WIC fruits & vegetables money
    val wicFoodsJson: String = "{}",    // {"milk":"4","cereal":"36"}
    val wicCustomJson: String = "{}",   // {"c_ab12":{"label":"Tofu","unit":"lb"}}
    val taxMfr: Boolean = true,         // tax manufacturer-coupon discounts
    val dirty: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
