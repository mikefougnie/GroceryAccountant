package com.godskingdomnow.groceryaccountant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godskingdomnow.groceryaccountant.AppContainer
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import com.godskingdomnow.groceryaccountant.data.BenefitsEntity
import com.godskingdomnow.groceryaccountant.data.ImportParser
import com.godskingdomnow.groceryaccountant.data.ItemEntity
import com.godskingdomnow.groceryaccountant.data.ShopListEntity
import com.godskingdomnow.groceryaccountant.data.settingsFor
import com.godskingdomnow.groceryaccountant.data.toEngine
import com.godskingdomnow.groceryaccountant.sync.Session
import com.godskingdomnow.groceryaccountant.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/* ======================= import from a text message ======================= */

data class ImportRow(val p: ImportParser.Parsed, val selected: Boolean, val onList: Boolean)

data class ImportUi(
    val text: String = "",
    val rows: List<ImportRow> = emptyList(),
    val lists: List<ShopListEntity> = emptyList(),
    /** null = make a new list */
    val targetId: Long? = null,
    val newName: String = "",
    val session: Session = Session(),
) {
    val count get() = rows.count { it.selected }
}

class ImportViewModel(private val c: AppContainer, startList: Long?) : ViewModel() {
    private val text = MutableStateFlow("")
    private val target = MutableStateFlow(startList)
    private val newName = MutableStateFlow("From text " + SimpleDateFormat("MMM d", Locale.US).format(Date()))
    /** index -> ticked/unticked by hand (cleared when the text changes) */
    private val overrides = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    init {
        // take shared text now, and any more that is shared while this screen is open
        viewModelScope.launch {
            c.pendingImport.filterNotNull().collect { setText(it); c.pendingImport.value = null }
        }
    }

    private val parsed = text.map { ImportParser.parse(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val targetItems = target.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.repo.items(id) }

    val ui: StateFlow<ImportUi> = combine(
        combine(text, parsed, overrides) { a, b, c2 -> Triple(a, b, c2) },
        combine(target, newName, targetItems) { a, b, c2 -> Triple(a, b, c2) },
        c.repo.lists, c.sessions.session,
    ) { (t, ps, ov), (tid, name, existing), lists, session ->
        val have = existing.filter { !it.checked }.map { it.name.lowercase() }.toSet()
        ImportUi(
            text = t,
            rows = ps.mapIndexed { i, p ->
                val dup = p.name.lowercase() in have
                ImportRow(p, ov[i] ?: (p.likely && !dup), dup)
            },
            lists = lists, targetId = tid?.takeIf { id -> lists.any { it.id == id } }, newName = name, session = session,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportUi(targetId = startList))

    fun setText(t: String) { text.value = t; overrides.value = emptyMap() }
    fun toggle(i: Int) { val cur = ui.value.rows.getOrNull(i) ?: return; overrides.value = overrides.value + (i to !cur.selected) }
    fun setTarget(id: Long?) { target.value = id }
    fun setNewName(n: String) { newName.value = n }

    /** Adds the ticked items; calls back with the list they went to. */
    fun import(share: Boolean, then: (Long) -> Unit) = viewModelScope.launch {
        val u = ui.value
        val picked = u.rows.filter { it.selected }.map { it.p }
        if (picked.isEmpty()) return@launch
        val id = u.targetId ?: c.repo.createList(u.newName, "", share)
        c.repo.addParsed(id, picked)
        then(id)
    }
}
