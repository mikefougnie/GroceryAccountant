package com.godskingdomnow.groceryaccountant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godskingdomnow.groceryaccountant.AppContainer
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import com.godskingdomnow.groceryaccountant.data.BenefitsEntity
import com.godskingdomnow.groceryaccountant.data.ItemEntity
import com.godskingdomnow.groceryaccountant.data.ShopListEntity
import com.godskingdomnow.groceryaccountant.data.settingsFor
import com.godskingdomnow.groceryaccountant.data.toEngine
import com.godskingdomnow.groceryaccountant.sync.Session
import com.godskingdomnow.groceryaccountant.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ======================= home: all lists ======================= */

data class ListCard(val list: ShopListEntity, val itemCount: Int, val left: Int, val youPay: Long)

data class ListsUi(
    val loaded: Boolean = false,
    val cards: List<ListCard> = emptyList(),
    val session: Session = Session(),
    val syncing: Boolean = false,
)

class ListsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<ListsUi> = combine(c.repo.lists, c.repo.allItems, c.repo.benefits, c.sessions.session, c.sync.syncing) {
            lists, items, ben, session, syncing ->
        val byList = items.groupBy { it.listId }
        ListsUi(
            loaded = true,
            cards = lists.map { l ->
                val its = byList[l.id].orEmpty()
                val r = ShopEngine.compute(its.map { it.toEngine() }, settingsFor(l, ben))
                ListCard(l, its.size, its.count { !it.checked }, r.totals.you)
            },
            session = session, syncing = syncing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListsUi())

    fun createList(name: String, store: String, shared: Boolean, then: (Long) -> Unit) = viewModelScope.launch {
        then(c.repo.createList(name, store, shared))
    }
    fun syncNow() = viewModelScope.launch { c.sync.syncNow() }
}

/* ======================= one list ======================= */

data class ListUi(
    val loaded: Boolean = false,
    val list: ShopListEntity? = null,
    val items: List<ItemEntity> = emptyList(),
    val benefits: BenefitsEntity = BenefitsEntity(),
    val result: ShopEngine.Result? = null,
    val otherLists: List<ShopListEntity> = emptyList(),
    val session: Session = Session(),
) {
    /** WIC/SNAP settings can be changed here (signed-in kids see them read-only, like the website). */
    val canEditBenefits get() = !session.signedIn || session.isParent
}

class ListViewModel(private val c: AppContainer, val listId: Long) : ViewModel() {
    val ui: StateFlow<ListUi> = combine(c.repo.list(listId), c.repo.items(listId), c.repo.benefits, c.repo.lists, c.sessions.session) {
            list, items, ben, lists, session ->
        val b = ben ?: BenefitsEntity()
        ListUi(
            loaded = true, list = list, items = items, benefits = b,
            result = ShopEngine.compute(items.map { it.toEngine() }, settingsFor(list, b)),
            otherLists = lists.filter { it.id != listId }, session = session,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListUi())

    private fun go(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun addItem(name: String) = go { c.repo.addItem(listId, name) }
    fun editItem(id: Long, change: (ItemEntity) -> ItemEntity) = go { c.repo.editItem(id, change) }
    fun deleteItem(id: Long) = go { c.repo.deleteItem(id) }
    fun moveItem(id: Long, to: Long) = go { c.repo.moveItem(id, to) }
    fun reorder(ids: List<Long>) = go { c.repo.reorder(listId, ids) }
    fun editList(change: (ShopListEntity) -> ShopListEntity) = go { c.repo.editList(listId, change) }
    fun deleteList() = go { c.repo.deleteList(listId) }
    fun shareList() = go { c.repo.shareList(listId) }
    fun uncheckAll() = go { c.repo.uncheckAll(listId) }
    fun clearChecked() = go { c.repo.clearChecked(listId) }
    fun sortByCategory() = go { c.repo.sortByCategory(listId) }
    fun finishTrip(used: ShopEngine.Used) = go { c.repo.finishTrip(listId, used) }
    fun editBenefits(change: (BenefitsEntity) -> BenefitsEntity) = go { c.repo.editBenefits(change) }
}

/* ======================= WIC / SNAP settings ======================= */

data class BenefitsUi(val benefits: BenefitsEntity = BenefitsEntity(), val session: Session = Session()) {
    val canEdit get() = !session.signedIn || session.isParent
}

class BenefitsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<BenefitsUi> = combine(c.repo.benefits, c.sessions.session) { b, s -> BenefitsUi(b ?: BenefitsEntity(), s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BenefitsUi())

    fun edit(change: (BenefitsEntity) -> BenefitsEntity) = viewModelScope.launch { c.repo.editBenefits(change) }
}

/* ======================= account / sync ======================= */

data class AccountUi(val session: Session = Session(), val syncing: Boolean = false)

class AccountViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<AccountUi> = combine(c.sessions.session, c.sync.syncing) { s, busy -> AccountUi(s, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUi())

    /** onDone(error or null, how many phone-only lists could be shared) */
    fun signIn(url: String, user: String, pass: String, onDone: (String?, Int) -> Unit) = viewModelScope.launch {
        when (val r = c.sync.signIn(url, user, pass)) {
            is SyncManager.Outcome.Failed -> onDone(r.message, 0)
            else -> onDone(null, c.repo.unsharedListCount())
        }
    }

    fun shareAllThenSync() = viewModelScope.launch { c.repo.shareAllLists(); c.sync.syncNow() }
    fun syncNow() = viewModelScope.launch { c.sync.syncNow() }
    fun signOut(keepFamilyLists: Boolean) = viewModelScope.launch {
        c.sync.signOut()
        c.repo.forgetFamily(keepFamilyLists)
    }
}
