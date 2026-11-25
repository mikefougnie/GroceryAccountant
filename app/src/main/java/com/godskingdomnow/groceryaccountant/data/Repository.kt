package com.godskingdomnow.groceryaccountant.data

import androidx.room.withTransaction
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import kotlinx.coroutines.flow.Flow

/**
 * All changes to lists, items and WIC/SNAP go through here. Every change marks
 * the row dirty; the sync only sends rows of lists that are shared with the
 * family, so phone-only lists never leave the phone.
 */
class Repository(private val db: AppDatabase, private val onChanged: () -> Unit) {

    private val dao = db.dao()
    private fun now() = System.currentTimeMillis()

    val lists: Flow<List<ShopListEntity>> = dao.observeLists()
    val allItems: Flow<List<ItemEntity>> = dao.observeAllItems()
    val benefits: Flow<BenefitsEntity?> = dao.observeBenefits()
    fun list(id: Long): Flow<ShopListEntity?> = dao.observeList(id)
    fun items(listId: Long): Flow<List<ItemEntity>> = dao.observeItems(listId)

    /* ---------------- lists ---------------- */

    suspend fun createList(name: String, store: String, shared: Boolean): Long {
        val id = dao.insertList(
            ShopListEntity(
                name = name.trim().ifEmpty { "Shopping list" },
                store = store.trim(),
                taxRate = dao.lastTaxRate() ?: 0.0,   // start with the rate you used last
                sortOrder = dao.maxListSort() + 1,
                shared = shared,
            )
        )
        onChanged()
        return id
    }

    suspend fun editList(id: Long, change: (ShopListEntity) -> ShopListEntity) {
        val l = dao.getList(id) ?: return
        dao.updateList(change(l).copy(dirty = true, updatedAt = now()))
        onChanged()
    }

    suspend fun deleteList(id: Long) {
        val l = dao.getList(id) ?: return
        db.withTransaction {
            dao.deleteItemsOfList(id)
            if (l.shared && l.remoteId != null) {
                // keep a marker until the website hears about it (it removes the items too)
                dao.updateList(l.copy(deleted = true, dirty = true, updatedAt = now()))
            } else {
                dao.deleteListRow(id)
            }
        }
        onChanged()
    }

    /** Share a phone-only list with the family (it uploads on the next sync). */
    suspend fun shareList(id: Long) {
        val l = dao.getList(id) ?: return
        db.withTransaction {
            dao.updateList(l.copy(shared = true, dirty = true, updatedAt = now()))
            dao.updateItems(dao.itemsOf(id).map { it.copy(dirty = true, updatedAt = now()) })
        }
        onChanged()
    }

    suspend fun shareAllLists() {
        dao.allLists().filter { !it.shared && !it.deleted }.forEach { shareList(it.id) }
    }

    suspend fun unsharedListCount(): Int = dao.allLists().count { !it.shared && !it.deleted }

    /* ---------------- items ---------------- */

    suspend fun addItem(listId: Long, name: String): Long {
        val n = name.trim()
        if (n.isEmpty()) return 0
        val id = dao.insertItem(ItemEntity(listId = listId, name = n, sortOrder = dao.maxItemSort(listId) + 1))
        onChanged()
        return id
    }

    /** Add several items at once (text-message import). Returns how many were added. */
    suspend fun addParsed(listId: Long, items: List<ImportParser.Parsed>): Int {
        if (items.isEmpty()) return 0
        db.withTransaction {
            var sort = dao.maxItemSort(listId)
            items.forEach { p ->
                dao.insertItem(ItemEntity(listId = listId, name = p.name, qty = p.qty, size = p.size,
                    priceCents = p.priceCents, sortOrder = ++sort))
            }
        }
        onChanged()
        return items.size
    }

    suspend fun editItem(id: Long, change: (ItemEntity) -> ItemEntity) {
        val i = dao.getItem(id) ?: return
        val updated = change(i)
        if (updated == i) return
        dao.updateItem(updated.copy(dirty = true, updatedAt = now()))
        onChanged()
    }

    suspend fun deleteItem(id: Long) {
        val i = dao.getItem(id) ?: return
        if (i.remoteId != null) dao.updateItem(i.copy(deleted = true, dirty = true, updatedAt = now()))
        else dao.deleteItemRow(id)
        onChanged()
    }

    /** Moving between lists = a fresh copy in the other list (simplest to sync correctly). */
    suspend fun moveItem(id: Long, toListId: Long) {
        val i = dao.getItem(id) ?: return
        db.withTransaction {
            dao.insertItem(i.copy(id = 0, remoteId = null, listId = toListId, dirty = true, deleted = false,
                sortOrder = dao.maxItemSort(toListId) + 1, updatedAt = now()))
            if (i.remoteId != null) dao.updateItem(i.copy(deleted = true, dirty = true, updatedAt = now()))
            else dao.deleteItemRow(id)
        }
        onChanged()
    }

    suspend fun reorder(listId: Long, orderedIds: List<Long>) {
        val items = dao.itemsOf(listId).associateBy { it.id }
        val changed = orderedIds.mapIndexedNotNull { idx, id ->
            items[id]?.takeIf { it.sortOrder != idx + 1 }?.copy(sortOrder = idx + 1, dirty = true, updatedAt = now())
        }
        if (changed.isNotEmpty()) { dao.updateItems(changed); onChanged() }
    }

    suspend fun uncheckAll(listId: Long) {
        val changed = dao.itemsOf(listId).filter { it.checked }.map { it.copy(checked = false, dirty = true, updatedAt = now()) }
        if (changed.isNotEmpty()) { dao.updateItems(changed); onChanged() }
    }

    suspend fun clearChecked(listId: Long) {
        dao.itemsOf(listId).filter { it.checked }.forEach { deleteItem(it.id) }
    }

    suspend fun sortByCategory(listId: Long) {
        val sorted = dao.itemsOf(listId).sortedWith(compareBy({ it.category.lowercase() }, { it.name.lowercase() }))
        reorder(listId, sorted.map { it.id })
    }

    /* ---------------- WIC / SNAP ---------------- */

    suspend fun editBenefits(change: (BenefitsEntity) -> BenefitsEntity) {
        val b = dao.getBenefits() ?: BenefitsEntity()
        dao.upsertBenefits(change(b).copy(id = 1, dirty = true, updatedAt = now()))
        onChanged()
    }

    /** After shopping: take what the checked items used off the balances, then remove them. */
    suspend fun finishTrip(listId: Long, used: ShopEngine.Used) {
        db.withTransaction {
            val b = dao.getBenefits() ?: BenefitsEntity()
            val foods = b.wicFoods().toMutableMap()
            used.wic.forEach { (k, v) ->
                val have = parseNumber(foods[k] ?: "") ?: return@forEach
                foods[k] = trimNumber(maxOf(0.0, Math.round((have - v) * 100) / 100.0))
            }
            dao.upsertBenefits(
                b.copy(
                    snapBalanceCents = if (b.snapOn) maxOf(0, b.snapBalanceCents - used.snap) else b.snapBalanceCents,
                    cvbBalanceCents = if (b.wicOn) maxOf(0, b.cvbBalanceCents - used.cvb) else b.cvbBalanceCents,
                    wicFoodsJson = encodeFoods(foods),
                    dirty = true, updatedAt = now(),
                )
            )
            dao.itemsOf(listId).filter { it.checked }.forEach { i ->
                if (i.remoteId != null) dao.updateItem(i.copy(deleted = true, dirty = true, updatedAt = now()))
                else dao.deleteItemRow(i.id)
            }
        }
        onChanged()
    }

    /* ---------------- signing out ---------------- */

    /** keep = true: family lists stay as phone-only copies; false: they are removed from the phone. */
    suspend fun forgetFamily(keep: Boolean) {
        db.withTransaction {
            dao.allLists().filter { it.shared }.forEach { l ->
                if (!keep || l.deleted) {
                    dao.deleteItemsOfList(l.id); dao.deleteListRow(l.id)
                } else {
                    dao.updateList(l.copy(shared = false, remoteId = null, dirty = true))
                    val items = dao.itemsOfLists(listOf(l.id))   // includes items marked deleted
                    items.filter { it.deleted }.forEach { dao.deleteItemRow(it.id) }
                    dao.updateItems(items.filter { !it.deleted }.map { it.copy(remoteId = null, dirty = true) })
                }
            }
            // tombstones of deleted items in shared lists are gone with their lists above
        }
    }
}
