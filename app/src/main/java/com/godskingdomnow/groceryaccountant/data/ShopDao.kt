package com.godskingdomnow.groceryaccountant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {

    // ---- lists ----
    @Query("SELECT * FROM lists WHERE deleted = 0 ORDER BY sortOrder, id")
    fun observeLists(): Flow<List<ShopListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observeList(id: Long): Flow<ShopListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: Long): ShopListEntity?

    @Query("SELECT * FROM lists")
    suspend fun allLists(): List<ShopListEntity>

    @Query("SELECT * FROM lists WHERE remoteId = :rid LIMIT 1")
    suspend fun listByRemote(rid: Long): ShopListEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM lists")
    suspend fun maxListSort(): Int

    @Query("SELECT taxRate FROM lists WHERE deleted = 0 ORDER BY id DESC LIMIT 1")
    suspend fun lastTaxRate(): Double?

    @Insert
    suspend fun insertList(list: ShopListEntity): Long

    @Update
    suspend fun updateList(list: ShopListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteListRow(id: Long)

    // ---- items ----
    @Query("SELECT * FROM items WHERE listId = :listId AND deleted = 0 ORDER BY sortOrder, id")
    fun observeItems(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deleted = 0 ORDER BY sortOrder, id")
    fun observeAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE listId = :listId AND deleted = 0 ORDER BY sortOrder, id")
    suspend fun itemsOf(listId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE listId IN (:listIds)")
    suspend fun itemsOfLists(listIds: List<Long>): List<ItemEntity>

    @Query("SELECT * FROM items WHERE remoteId = :rid LIMIT 1")
    suspend fun itemByRemote(rid: Long): ItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM items WHERE listId = :listId")
    suspend fun maxItemSort(listId: Long): Int

    @Insert
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Update
    suspend fun updateItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemRow(id: Long)

    @Query("DELETE FROM items WHERE listId = :listId")
    suspend fun deleteItemsOfList(listId: Long)

    // ---- WIC / SNAP ----
    @Query("SELECT * FROM benefits WHERE id = 1")
    fun observeBenefits(): Flow<BenefitsEntity?>

    @Query("SELECT * FROM benefits WHERE id = 1")
    suspend fun getBenefits(): BenefitsEntity?

    @Upsert
    suspend fun upsertBenefits(b: BenefitsEntity)
}
