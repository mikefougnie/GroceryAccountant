@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import com.godskingdomnow.groceryaccountant.data.BenefitsEntity
import com.godskingdomnow.groceryaccountant.data.ItemEntity
import com.godskingdomnow.groceryaccountant.data.foodInfo
import com.godskingdomnow.groceryaccountant.data.money
import com.godskingdomnow.groceryaccountant.data.parseNumber
import com.godskingdomnow.groceryaccountant.data.trimNumber
import com.godskingdomnow.groceryaccountant.data.wicFoods
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ListScreen(vm: ListViewModel, back: () -> Unit, openBenefits: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val list = ui.list
    if (ui.loaded && list == null) { LaunchedEffect(Unit) { back() }; return }

    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var sheetItemId by remember { mutableStateOf<Long?>(null) }
    var sizeFirst by remember { mutableStateOf(false) }

    // local copy of the order while dragging; follows the database otherwise
    var order by remember { mutableStateOf(ui.items) }
    LaunchedEffect(ui.items) { order = ui.items }
    val lazyState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        val f = order.indexOfFirst { it.id == from.key }
        val t = order.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) order = order.toMutableList().apply { add(t, removeAt(f)) }
    }

    val b = ui.benefits
    val checkedCount = ui.items.count { it.checked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(list?.name ?: "", maxLines = 1)
                        val sub = listOfNotNull(list?.store?.takeIf { it.isNotBlank() }, if (list?.shared == true) "shared with family" else null)
                        if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "List options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Edit list (name, store, tax)") }, { menu = false; editing = true })
                            if ((b.wicOn || b.snapOn) && checkedCount > 0 && ui.canEditBenefits)
                                DropdownMenuItem({ Text("Done shopping — update balances") }, { menu = false; finishing = true })
                            DropdownMenuItem({ Text("Uncheck all") }, { menu = false; vm.uncheckAll() })
                            DropdownMenuItem({ Text("Remove checked items") }, { menu = false; vm.clearChecked() }, enabled = checkedCount > 0)
                            DropdownMenuItem({ Text("Sort by category") }, { menu = false; vm.sortByCategory() })
                            DropdownMenuItem({ Text("WIC, SNAP & coupons…") }, { menu = false; openBenefits() })
                            if (ui.session.signedIn && list?.shared == false)
                                DropdownMenuItem({ Text("Share with ${ui.session.familyName.ifEmpty { "family" }}") }, { menu = false; vm.shareList() })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Delete list", color = MaterialTheme.colorScheme.error) }, { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = { TotalsBar(ui.result, b) },
    ) { pad ->
        LazyColumn(
            state = lazyState,
            modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!b.wicOn && !b.snapOn && ui.canEditBenefits) item(key = "quick") {
                QuickBenefits(
                    onWic = { vm.editBenefits { it.copy(wicOn = true) }; openBenefits() },
                    onSnap = { vm.editBenefits { it.copy(snapOn = true) }; openBenefits() },
                )
            }
            if (ui.items.isEmpty()) item(key = "empty") {
                Text("Nothing here yet — add your first item below.", Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(order, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { dragging ->
                    Surface(tonalElevation = if (dragging) 6.dp else 0.dp, shadowElevation = if (dragging) 6.dp else 0.dp) {
                        ItemRow(
                            item = item, line = ui.result?.line(item.id), b = b,
                            handle = {
                                Icon(
                                    Icons.Rounded.DragIndicator, "Drag to reorder ${item.name}",
                                    Modifier.draggableHandle(onDragStopped = { vm.reorder(order.map { it.id }) }).padding(4.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onEdit = { change -> vm.editItem(item.id, change) },
                            onOpen = { sizeFirst = false; sheetItemId = item.id },
                            askSize = { sizeFirst = true; sheetItemId = item.id },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item(key = "add") { AddItemRow(vm::addItem) }
        }
    }

    // ---- item details ----
    val sheetItem = ui.items.firstOrNull { it.id == sheetItemId }
    if (sheetItem != null) {
        ItemSheet(
            item = sheetItem, line = ui.result?.line(sheetItem.id), b = b, focusSize = sizeFirst,
            otherLists = ui.otherLists.map { it.id to it.name },
            onEdit = { change -> vm.editItem(sheetItem.id, change) },
            onMove = { to -> vm.moveItem(sheetItem.id, to); sheetItemId = null },
            onDelete = { vm.deleteItem(sheetItem.id); sheetItemId = null },
            onDismiss = { sheetItemId = null },
        )
    }

    if (editing && list != null) {
        EditListDialog(list.name, list.store, list.taxRate, onDismiss = { editing = false }) { n, s, rate ->
            editing = false
            vm.editList { it.copy(name = n.ifBlank { it.name }, store = s, taxRate = rate) }
        }
    }
    if (confirmDelete && list != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${list.name}”?") },
            text = { Text(if (list.shared) "It’s removed for your whole family too." else "Its items are deleted too.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteList(); back() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (finishing && ui.result != null) {
        val u = ui.result!!.usedCart
        val bits = buildList {
            if (b.snapOn && u.snap > 0) add("${money(u.snap)} SNAP")
            if (b.wicOn && u.cvb > 0) add("${money(u.cvb)} WIC fruits & vegetables")
            u.wic.forEach { (k, v) -> val f = b.foodInfo(k); add("${trimNumber(v)} ${f.unit} WIC ${f.label.lowercase()}") }
        }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Update your balances?") },
            text = {
                Text(
                    (if (bits.isEmpty()) "No benefits were used by the checked items. Remove" else "Take ${bits.joinToString(", ")} off your balances, and remove") +
                        " the $checkedCount checked item${if (checkedCount == 1) "" else "s"} from this list?"
                )
            },
            confirmButton = { TextButton({ finishing = false; vm.finishTrip(u) }) { Text("Update") } },
            dismissButton = { TextButton({ finishing = false }) { Text("Cancel") } },
        )
    }
}

/* ---------------- one item: two short lines ---------------- */

@Composable
private fun ItemRow(
    item: ItemEntity, line: ShopEngine.Line?, b: BenefitsEntity,
    handle: @Composable () -> Unit,
    onEdit: ((ItemEntity) -> ItemEntity) -> Unit,
    onOpen: () -> Unit,
    askSize: (ItemEntity) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            handle()
            Checkbox(checked = item.checked, onCheckedChange = { c -> onEdit { it.copy(checked = c) } },
                modifier = Modifier.semantics { contentDescription = "Got ${item.name}" })
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 4.dp)) {
                Text(
                    item.name, style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = listOf(item.size, item.category).filter { it.isNotBlank() }
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PriceField(item.priceCents, { c -> onEdit { it.copy(priceCents = c) } }, Modifier.width(92.dp), "Price of ${item.name}")
        }
        Row(Modifier.padding(start = 76.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BenefitButtons(item, b, onChange = { updated -> onEdit { updated.copy(id = it.id) } }, askSize = askSize)
                Pill("Tax", item.taxable, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onEdit { it.copy(taxable = !it.taxable) } },
                    description = if (item.taxable) "Taxable: ${item.name}" else "Not taxed: ${item.name}")
                PayChips(item, line, b)
            }
            QtyStepper(item.qty, { q -> onEdit { it.copy(qty = q) } }, item.name)
        }
    }
}

@Composable
private fun QuickBenefits(onWic: () -> Unit, onSnap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Using benefits?", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = false, onClick = onWic, label = { Text("WIC") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = false, onClick = onSnap, label = { Text("SNAP") })
    }
}

@Composable
private fun AddItemRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val add = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it }, Modifier.weight(1f), placeholder = { Text("Add an item…") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = add) { Text("Add") }
    }
}

/* ---------------- item details sheet ---------------- */

@Composable
private fun ItemSheet(
    item: ItemEntity, line: ShopEngine.Line?, b: BenefitsEntity, focusSize: Boolean,
    otherLists: List<Pair<Long, String>>,
    onEdit: ((ItemEntity) -> ItemEntity) -> Unit,
    onMove: (Long) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge)
            TextFieldFor("Name", item.name) { v -> onEdit { it.copy(name = v.ifBlank { it.name }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldFor("Size (e.g. 2 lb)", item.size, Modifier.weight(1f)) { v -> onEdit { it.copy(size = v) } }
                TextFieldFor("Category", item.category, Modifier.weight(1f)) { v -> onEdit { it.copy(category = v) } }
            }
            SwitchRow("Taxable", item.taxable) { v -> onEdit { it.copy(taxable = v) } }

            if (b.wicOn) {
                Text("WIC", style = MaterialTheme.typography.labelLarge)
                WicPicker(item, b) { cat -> onEdit { it.copy(wicCat = cat, wicUnits = if (it.wicCat == cat) it.wicUnits else 1.0) } }
                val cat = item.wicCat
                if (cat != null && cat != ShopEngine.CVB) {
                    val f = b.foodInfo(cat)
                    TextFieldFor("How much ${f.label.lowercase()} one uses (${f.unit})", trimNumber(item.wicUnits),
                        keyboard = KeyboardType.Decimal, autoFocus = focusSize) { v ->
                        parseNumber(v)?.takeIf { it > 0 }?.let { u -> onEdit { it.copy(wicUnits = u) } }
                    }
                    Text("For example, a 12 oz box of cereal uses 12.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (cat == ShopEngine.CVB) {
                    Text("Paid from your fruits & vegetables money. If it runs out, SNAP or you pay the rest.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (b.snapOn) SwitchRow("SNAP-eligible (food)", item.snap) { v -> onEdit { it.copy(snap = v) } }

            Text("Coupon", style = MaterialTheme.typography.labelLarge)
            val types = listOf(null to "None", "amt" to "$ off", "pct" to "% off", "bogo" to "Buy 1 get 1")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = item.couponType == code,
                        onClick = { onEdit { it.copy(couponType = code) } },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (item.couponType == "amt" || item.couponType == "pct") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextFieldFor(if (item.couponType == "pct") "Percent off" else "Dollars off",
                        item.couponValue?.let { trimNumber(it) } ?: "", Modifier.weight(1f), keyboard = KeyboardType.Decimal) { v ->
                        onEdit { it.copy(couponValue = parseNumber(v)) }
                    }
                    if (item.couponType == "amt") {
                        FilterChip(selected = item.couponEach, onClick = { onEdit { it.copy(couponEach = !it.couponEach) } },
                            label = { Text("off each one") })
                    }
                }
            }
            if (item.couponType != null) {
                val makers = listOf(false to "Store coupon", true to "Manufacturer")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    makers.forEachIndexed { i, (mfr, label) ->
                        SegmentedButton(selected = item.couponMfr == mfr, onClick = { onEdit { it.copy(couponMfr = mfr) } },
                            shape = SegmentedButtonDefaults.itemShape(i, makers.size)) { Text(label) }
                    }
                }
            }

            if (line != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val parts = buildList {
                            add("Shelf ${money(line.gross)}")
                            if (line.coupon > 0) add("coupon −${money(line.coupon)}")
                            if (line.wicPay > 0 || line.wicQty > 0) add("WIC pays ${if (line.wicQty < line.qty) "${line.wicQty} of ${line.qty}" else "it"}")
                            if (line.cvbPay > 0) add("WIC F&V ${money(line.cvbPay)}")
                            if (line.snapPay > 0) add("SNAP ${money(line.snapPay)}")
                            if (line.tax > 0) add("tax ${money(line.tax)}")
                        }
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("You pay ${money(line.you)} for this item", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (otherLists.isNotEmpty()) {
                    var moveOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { moveOpen = true }) { Text("Move to…") }
                        DropdownMenu(moveOpen, { moveOpen = false }) {
                            otherLists.forEach { (id, name) -> DropdownMenuItem({ Text(name) }, { moveOpen = false; onMove(id) }) }
                        }
                    }
                }
                OutlinedButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun WicPicker(item: ItemEntity, b: BenefitsEntity, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val foods = b.wicFoods().keys.toList()
    val label = when (val c = item.wicCat) {
        null -> "Not WIC"
        ShopEngine.CVB -> "Fruits & vegetables ($)"
        else -> b.foodInfo(c).label
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true, label = { Text("Counts toward") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem({ Text("Not WIC") }, { open = false; onPick(null) })
            DropdownMenuItem({ Text("Fruits & vegetables ($)") }, { open = false; onPick(ShopEngine.CVB) })
            foods.forEach { k ->
                val f = b.foodInfo(k)
                DropdownMenuItem({ Text("${f.label} (${f.unit})") }, { open = false; onPick(k) })
            }
        }
    }
}

/* ---------------- small form helpers ---------------- */

@Composable
fun TextFieldFor(
    label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text, autoFocus: Boolean = false, onChange: (String) -> Unit,
) {
    // local text so typing isn't interrupted by saves; refreshed when the item changes elsewhere
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value }
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        text, { text = it; onChange(it) },
        modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }
}

@Composable
private fun EditListDialog(name: String, store: String, rate: Double, onDismiss: () -> Unit, onSave: (String, String, Double) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var s by remember { mutableStateOf(store) }
    var r by remember { mutableStateOf(if (rate == 0.0) "" else trimNumber(rate)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(s, { s = it }, label = { Text("Store") }, singleLine = true)
                OutlinedTextField(r, { r = it }, label = { Text("Sales tax rate (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Used for items you mark Tax.") })
            }
        },
        confirmButton = { TextButton({ onSave(n.trim(), s.trim(), (parseNumber(r) ?: 0.0).coerceIn(0.0, 100.0)) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
