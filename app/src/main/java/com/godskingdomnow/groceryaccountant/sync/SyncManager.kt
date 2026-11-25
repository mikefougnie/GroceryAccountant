package com.godskingdomnow.groceryaccountant.sync

import android.os.Build
import androidx.room.withTransaction
import com.godskingdomnow.groceryaccountant.data.AppDatabase
import com.godskingdomnow.groceryaccountant.data.BenefitsEntity
import com.godskingdomnow.groceryaccountant.data.CustomFood
import com.godskingdomnow.groceryaccountant.data.ItemEntity
import com.godskingdomnow.groceryaccountant.data.ShopListEntity
import com.godskingdomnow.groceryaccountant.data.encodeCustom
import com.godskingdomnow.groceryaccountant.data.encodeFoods
import com.godskingdomnow.groceryaccountant.data.parseCents
import com.godskingdomnow.groceryaccountant.data.wicCustom
import com.godskingdomnow.groceryaccountant.data.wicFoods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Optional sync with the family app. Nothing here runs unless the phone is signed in.
 *
 * One sync = send the phone's changes, then take the website's full copy of the
 * family's lists. The website applies a phone change only if that row wasn't changed
 * on the website since this phone's last sync; otherwise the website's version wins.
 */
class SyncManager(
    private val db: AppDatabase,
    private val api: SyncApi,
    private val sessions: SessionStore,
    private val scope: CoroutineScope,
) {
    private val dao = db.dao()
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Called after every local change: sync a few seconds later (changes are batched). */
    fun requestSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(3000)
            if (sessions.get().signedIn) syncNow()
        }
    }

    sealed interface Outcome {
        data object Ok : Outcome
        data class Failed(val message: String) : Outcome
        data object NotSignedIn : Outcome
    }

    suspend fun signIn(baseUrl: String, username: String, password: String): Outcome {
        val url = normalizeUrl(baseUrl)
        return try {
            val r = api.login(url, username.trim(), password, "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            val token = r.token
            if (!r.ok || token.isNullOrEmpty()) return Outcome.Failed(r.error ?: "Couldn’t sign in.")
            val u = r.user ?: UserDto()
            sessions.update {
                it.copy(baseUrl = url, token = token, username = u.username, displayName = u.displayName,
                    role = u.role, familyName = u.familyName, serverBase = "", lastError = "", lastConflicts = 0)
            }
            Outcome.Ok
        } catch (e: IOException) {
            Outcome.Failed(networkMessage(e))
        }
    }

    suspend fun signOut() {
        val s = sessions.get()
        if (s.signedIn) runCatching { api.logout(s.baseUrl, s.token) }   // best effort; works offline too
        sessions.update { it.copy(token = "", serverBase = "", lastSyncAt = 0, lastError = "", role = "", familyName = "", displayName = "") }
    }

    suspend fun syncNow(): Outcome = mutex.withLock {
        val s = sessions.get()
        if (!s.signedIn) return Outcome.NotSignedIn
        _syncing.value = true
        try {
            runSync(s)
            Outcome.Ok
        } catch (e: SignedOutException) {
            sessions.update { it.copy(token = "", lastError = e.message ?: "Signed out.") }
            Outcome.Failed(e.message ?: "Signed out.")
        } catch (e: IOException) {
            val msg = networkMessage(e)
            sessions.update { it.copy(lastError = msg) }
            Outcome.Failed(msg)
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun runSync(s: Session) {
        // ---------- 1. what the phone changed ----------
        val sharedLists = dao.allLists().filter { it.shared }
        val sentLists = sharedLists.filter { it.dirty || it.remoteId == null }
        val listById = sharedLists.associateBy { it.id }
        val sharedItems = if (sharedLists.isEmpty()) emptyList() else dao.itemsOfLists(sharedLists.map { it.id })
        val sentItems = sharedItems.filter { (it.dirty || it.remoteId == null) && listById[it.listId]?.deleted == false }
            .filter { !(it.deleted && it.remoteId == null) }
        val ben = dao.getBenefits()
        val sendBenefits = ben != null && ben.dirty && s.isParent

        val req = SyncReq(
            base = s.serverBase,
            lists = sentLists.filter { !(it.deleted && it.remoteId == null) }.map { it.toDto() },
            items = sentItems.map { it.toDto(listById.getValue(it.listId)) },
            benefits = if (sendBenefits) ben!!.toDto() else null,
        )

        // ---------- 2. send it, get the family's lists back ----------
        val res = api.sync(s.baseUrl, s.token, req)
        if (!res.ok) throw IOException(res.error ?: "The website couldn’t sync.")

        // ---------- 3. take the website's copy ----------
        db.withTransaction {
            // ids for new rows; clear "dirty" on rows that didn't change while we were syncing
            sentLists.forEach { sent ->
                val cur = dao.getList(sent.id) ?: return@forEach
                if (sent.deleted) { dao.deleteListRow(sent.id); return@forEach }
                val rid = res.map.lists["L${sent.id}"] ?: cur.remoteId
                dao.updateList(cur.copy(remoteId = rid, dirty = cur.dirty && cur.updatedAt != sent.updatedAt))
            }
            dao.allLists().filter { it.shared && it.deleted && it.remoteId == null }.forEach { dao.deleteListRow(it.id) }
            sentItems.forEach { sent ->
                val cur = dao.getItem(sent.id) ?: return@forEach
                if (sent.deleted) { dao.deleteItemRow(sent.id); return@forEach }
                val rid = res.map.items["I${sent.id}"] ?: cur.remoteId
                dao.updateItem(cur.copy(remoteId = rid, dirty = cur.dirty && cur.updatedAt != sent.updatedAt))
            }

            // lists
            val serverListIds = HashSet<Long>()
            val serverItemIds = HashSet<Long>()
            res.snapshot.lists.forEach { sl ->
                val rid = sl.rid ?: return@forEach
                serverListIds += rid
                val local = dao.listByRemote(rid)
                val localId = when {
                    local == null -> dao.insertList(
                        ShopListEntity(remoteId = rid, name = sl.name, store = sl.store, taxRate = sl.taxRate,
                            sortOrder = sl.sort, shared = true, dirty = false)
                    )
                    local.dirty -> local.id          // edited again during the sync: send next time
                    else -> {
                        dao.updateList(local.copy(name = sl.name, store = sl.store, taxRate = sl.taxRate,
                            sortOrder = sl.sort, shared = true, deleted = false))
                        local.id
                    }
                }
                // items of this list
                sl.items.forEach { si ->
                    val irid = si.rid ?: return@forEach
                    serverItemIds += irid
                    val li = dao.itemByRemote(irid)
                    when {
                        li == null -> dao.insertItem(si.toEntity(localId))
                        li.dirty -> Unit
                        else -> dao.updateItem(si.toEntity(localId).copy(id = li.id))
                    }
                }
            }
            // removed on the website (and not changed on the phone since)
            dao.allLists().filter { it.shared && it.remoteId != null && it.remoteId !in serverListIds && !it.dirty }
                .forEach { dao.deleteItemsOfList(it.id); dao.deleteListRow(it.id) }
            val stillShared = dao.allLists().filter { it.shared }.map { it.id }
            if (stillShared.isNotEmpty()) {
                dao.itemsOfLists(stillShared).filter { it.remoteId != null && it.remoteId !in serverItemIds && !it.dirty }
                    .forEach { dao.deleteItemRow(it.id) }
            }

            // WIC / SNAP
            val sb = res.snapshot.benefits
            val curB = dao.getBenefits()
            val sentClean = curB != null && sendBenefits && curB.updatedAt == ben!!.updatedAt
            when {
                sb.exists == 0 && curB != null && (curB.wicOn || curB.snapOn) && s.isParent ->
                    // the family hasn't set up WIC/SNAP on the website yet: share the phone's
                    if (!sendBenefits) dao.upsertBenefits(curB.copy(dirty = true)) else Unit
                sb.exists == 0 -> Unit
                curB != null && curB.dirty && !sentClean && s.isParent -> Unit   // changed during sync: send next time
                else -> dao.upsertBenefits(sb.toEntity(curB))
            }
        }

        val stillDirtyBenefits = s.isParent && res.snapshot.benefits.exists == 0 && dao.getBenefits()?.dirty == true
        sessions.update {
            it.copy(serverBase = res.serverTime, lastSyncAt = System.currentTimeMillis(), lastError = "",
                lastConflicts = res.conflicts.toLong(),
                displayName = res.user?.displayName ?: it.displayName, role = res.user?.role ?: it.role,
                familyName = res.user?.familyName ?: it.familyName)
        }
        if (stillDirtyBenefits) requestSoon()
    }

    /* ---------- conversions ---------- */

    private fun ShopListEntity.toDto() = ListDto(
        cid = "L$id", rid = remoteId, name = name, store = store, taxRate = taxRate, sort = sortOrder,
        deleted = if (deleted) 1 else 0,
    )

    private fun ItemEntity.toDto(list: ShopListEntity) = ItemDto(
        cid = "I$id", rid = remoteId, listCid = "L${list.id}", listRid = list.remoteId,
        name = name, size = size, category = category,
        price = priceCents?.let { it / 100.0 }, qty = qty, taxable = if (taxable) 1 else 0,
        checked = if (checked) 1 else 0, sort = sortOrder, snap = if (snap) 1 else 0,
        wicCat = wicCat, wicUnits = wicUnits, couponType = couponType, couponValue = couponValue,
        couponEach = if (couponEach) 1 else 0, couponMfr = if (couponMfr) 1 else 0,
        deleted = if (deleted) 1 else 0,
    )

    private fun ItemDto.toEntity(localListId: Long) = ItemEntity(
        remoteId = rid, listId = localListId, name = name, size = size, category = category,
        priceCents = price?.let { Math.round(it * 100) }, qty = qty.coerceIn(1, 999),
        taxable = taxable == 1, checked = checked == 1, sortOrder = sort, snap = snap == 1,
        wicCat = wicCat, wicUnits = if (wicUnits > 0) wicUnits else 1.0,
        couponType = couponType, couponValue = couponValue, couponEach = couponEach == 1, couponMfr = couponMfr == 1,
        dirty = false,
    )

    private fun BenefitsEntity.toDto() = BenefitsDto(
        wicOn = if (wicOn) 1 else 0, snapOn = if (snapOn) 1 else 0,
        snapBalance = snapBalanceCents / 100.0, cvbBalance = cvbBalanceCents / 100.0,
        wicFoods = api.json.parseToJsonElement(encodeFoods(wicFoods())),
        wicCustom = api.json.parseToJsonElement(encodeCustom(wicCustom())),
        taxMfr = if (taxMfr) 1 else 0,
    )

    private fun BenefitsDto.toEntity(cur: BenefitsEntity?) = (cur ?: BenefitsEntity()).copy(
        wicOn = wicOn == 1, snapOn = snapOn == 1,
        snapBalanceCents = parseCents(snapBalance.toString()) ?: 0,
        cvbBalanceCents = parseCents(cvbBalance.toString()) ?: 0,
        wicFoodsJson = encodeFoods(foods()),
        wicCustomJson = encodeCustom(custom().mapValues { CustomFood(it.value.label, it.value.unit) }),
        taxMfr = taxMfr == 1,
        dirty = false,
    )

    companion object {
        fun normalizeUrl(u: String): String {
            var url = u.trim().ifEmpty { Session.DEFAULT_BASE_URL }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://") // signing in only over HTTPS
            return if (url.endsWith("/")) url else "$url/"
        }

        fun networkMessage(e: IOException): String = when (e) {
            is java.net.UnknownHostException -> "No internet connection (or the website address is wrong)."
            is java.net.SocketTimeoutException -> "The website took too long to answer."
            else -> e.message ?: "Couldn’t reach the website."
        }
    }
}
