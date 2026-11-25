package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import com.godskingdomnow.groceryaccountant.data.BenefitsEntity
import com.godskingdomnow.groceryaccountant.data.ItemEntity
import com.godskingdomnow.groceryaccountant.data.centsText
import com.godskingdomnow.groceryaccountant.data.foodInfo
import com.godskingdomnow.groceryaccountant.data.money
import com.godskingdomnow.groceryaccountant.data.parseCents
import com.godskingdomnow.groceryaccountant.data.wicFoods
import com.godskingdomnow.groceryaccountant.ui.theme.LocalBenefitColors

/* ---------------- the running total at the bottom of a list ---------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TotalsBar(r: ShopEngine.Result?, benefits: BenefitsEntity) {
    val t = r?.totals
    val bc = LocalBenefitColors.current
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Labeled("Items", money(t?.net ?: 0))
                Labeled("Tax", money(t?.taxAllCash ?: 0))
                Labeled("Total", money(t?.totalAllCash ?: 0))
            }
            if (t != null) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (t.coupons > 0) Text("Coupons −${money(t.coupons)}", color = bc.coupon, style = MaterialTheme.typography.bodySmall)
                    if (benefits.wicOn && t.wic + t.cvb > 0) Text("WIC −${money(t.wic + t.cvb)}", color = bc.wic, style = MaterialTheme.typography.bodySmall)
                    if (benefits.snapOn && t.snap > 0) Text("SNAP −${money(t.snap)}", color = bc.snap, style = MaterialTheme.typography.bodySmall)
                    if (t.taxAllCash > t.tax) Text("Tax saved ${money(t.taxAllCash - t.tax)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("YOU PAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(t?.you ?: 0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("In cart: ${money(t?.youCart ?: 0)}", style = MaterialTheme.typography.bodySmall)
                    Text("Still to get: ${money(t?.youLeft ?: 0)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------- price box: type freely, saved as you type ---------------- */

@Composable
fun PriceField(cents: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, label: String = "Price") {
    var text by remember { mutableStateOf(centsText(cents)) }
    var focused by remember { mutableStateOf(false) }
    // pick up changes made elsewhere (sync, another screen) while not typing here
    LaunchedEffect(cents) { if (!focused) text = centsText(cents) }
    Row(
        modifier
            .border(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("0.00", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    val clean = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10)
                    text = clean
                    onChange(parseCents(clean))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = label }
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) text = centsText(parseCents(text))   // tidy to 0.00 when leaving
                    },
            )
        }
    }
}

/* ---------------- quantity − 2 + ---------------- */

@Composable
fun QtyStepper(qty: Int, onChange: (Int) -> Unit, name: String) {
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clickable { onChange((qty - 1).coerceAtLeast(1)) }.semantics { contentDescription = "Fewer $name" },
            contentAlignment = Alignment.Center) { Icon(Icons.Filled.Remove, null, Modifier.size(16.dp)) }
        Text("$qty", Modifier.width(26.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(32.dp).clickable { onChange((qty + 1).coerceAtMost(999)) }.semantics { contentDescription = "More $name" },
            contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }
    }
}

/* ---------------- small pill used for WIC / SNAP / Tax / info ---------------- */

@Composable
fun Pill(
    text: String, on: Boolean, color: Color, soft: Color,
    onClick: (() -> Unit)? = null, strike: Boolean = false, description: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val base = Modifier
        .then(if (on) Modifier.background(soft, shape) else Modifier)
        .border(BorderStroke(1.dp, if (on) color else MaterialTheme.colorScheme.outline), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .padding(horizontal = 8.dp, vertical = 2.dp)
    Text(
        text, base,
        color = if (on) color else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )
}

@Composable
fun InfoChip(text: String, color: Color, soft: Color) {
    Text(
        text, Modifier.background(soft, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
    )
}

/** The one-tap WIC and SNAP buttons (only for programs the family has). */
@Composable
fun BenefitButtons(item: ItemEntity, b: BenefitsEntity, onChange: (ItemEntity) -> Unit, askSize: (ItemEntity) -> Unit) {
    val bc = LocalBenefitColors.current
    if (b.wicOn) {
        var open by remember { mutableStateOf(false) }
        val foods = b.wicFoods().keys
        val cur = item.wicCat?.takeIf { it == ShopEngine.CVB || it in foods }
        val label = when (cur) { null -> "WIC"; ShopEngine.CVB -> "F&V"; else -> b.foodInfo(cur).label }
        Box {
            Pill("$label ▾", cur != null, bc.wic, bc.wicSoft, onClick = { open = true }, description = "WIC for ${item.name}: $label")
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Not WIC") }, onClick = { open = false; onChange(item.copy(wicCat = null)) })
                DropdownMenuItem(text = { Text("Fruits & vegetables ($)") }, onClick = { open = false; onChange(item.copy(wicCat = ShopEngine.CVB, wicUnits = 1.0)) })
                if (foods.isNotEmpty()) HorizontalDivider()
                foods.forEach { k ->
                    val f = b.foodInfo(k)
                    DropdownMenuItem(text = { Text("${f.label} (${f.unit})") }, onClick = {
                        open = false
                        val updated = item.copy(wicCat = k, wicUnits = if (item.wicCat == k) item.wicUnits else 1.0)
                        onChange(updated)
                        // foods counted by weight need the package size
                        if (f.unit.lowercase() in setOf("oz", "lb", "qt")) askSize(updated)
                    })
                }
            }
        }
    }
    if (b.snapOn) {
        Pill("SNAP", item.snap, bc.snap, bc.snapSoft, onClick = { onChange(item.copy(snap = !item.snap)) }, strike = !item.snap,
            description = if (item.snap) "Counts toward SNAP: ${item.name}" else "Not SNAP: ${item.name}")
    }
}

/** What pays for this item, in a few words. */
@Composable
fun PayChips(item: ItemEntity, line: ShopEngine.Line?, b: BenefitsEntity) {
    if (line == null) return
    val bc = LocalBenefitColors.current
    if (line.coupon > 0) InfoChip("−${money(line.coupon)} coupon", bc.coupon, bc.couponSoft)
    if (b.wicOn && item.wicCat != null) {
        when {
            item.wicCat == ShopEngine.CVB && line.cvbPay > 0 -> InfoChip("F&V pays ${money(line.cvbPay)}", bc.wic, bc.wicSoft)
            item.wicCat == ShopEngine.CVB && line.net > 0 -> InfoChip("F&V used up", bc.wic, bc.wicSoft)
            line.wicQty > 0 -> InfoChip(
                if (line.wicQty < line.qty) "WIC pays ${line.wicQty} of ${line.qty}" else if (line.wicPay > 0) "WIC pays ${money(line.wicPay)}" else "WIC covers it",
                bc.wic, bc.wicSoft)
            line.net > 0 && item.wicCat != ShopEngine.CVB -> InfoChip("not enough WIC left", bc.wic, bc.wicSoft)
        }
    }
    if (b.snapOn && line.snapPay > 0) InfoChip("SNAP pays ${money(line.snapPay)}", bc.snap, bc.snapSoft)
    if ((line.wicPay + line.cvbPay + line.snapPay) > 0 && line.you > 0)
        InfoChip("you ${money(line.you)}", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
fun SectionGap() = Spacer(Modifier.height(12.dp))
