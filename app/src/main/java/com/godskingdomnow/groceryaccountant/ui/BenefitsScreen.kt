@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.groceryaccountant.calc.ShopEngine
import com.godskingdomnow.groceryaccountant.data.CustomFood
import com.godskingdomnow.groceryaccountant.data.encodeCustom
import com.godskingdomnow.groceryaccountant.data.encodeFoods
import com.godskingdomnow.groceryaccountant.data.parseNumber
import com.godskingdomnow.groceryaccountant.data.wicCustom
import com.godskingdomnow.groceryaccountant.data.wicFoods
import com.godskingdomnow.groceryaccountant.ui.theme.LocalBenefitColors

@Composable
fun BenefitsScreen(vm: BenefitsViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val b = ui.benefits
    val can = ui.canEdit
    val bc = LocalBenefitColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WIC, SNAP & coupons") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!can) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("A parent in ${ui.session.familyName.ifEmpty { "your family" }} manages these. They update here when you sync.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Turn on only what your household uses. Everything here stays on this phone unless you sign in to share with your family.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            /* ---------- SNAP ---------- */
            SwitchRow("I use SNAP (EBT food)", b.snapOn, enabled = can) { v -> vm.edit { it.copy(snapOn = v) } }
            if (b.snapOn) {
                MoneyRow("SNAP balance", b.snapBalanceCents, can) { c -> vm.edit { it.copy(snapBalanceCents = c) } }
                Text("SNAP pays for food items first, starting with taxable ones (SNAP purchases aren’t taxed).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            /* ---------- WIC ---------- */
            SwitchRow("I use WIC", b.wicOn, enabled = can) { v -> vm.edit { it.copy(wicOn = v) } }
            if (b.wicOn) {
                MoneyRow("Fruits & vegetables money", b.cvbBalanceCents, can) { c -> vm.edit { it.copy(cvbBalanceCents = c) } }
                Text("Food left on your WIC card this month", style = MaterialTheme.typography.titleSmall, color = bc.wic,
                    fontWeight = FontWeight.SemiBold)
                Text("Leave blank for foods you don’t get. Only foods with an amount show up when you tag an item.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val foods = b.wicFoods()
                val custom = b.wicCustom()
                val setAmount = { key: String, text: String ->
                    vm.edit { cur ->
                        val m = cur.wicFoods().toMutableMap()
                        val n = parseNumber(text)
                        if (n == null || n <= 0) m.remove(key) else m[key] = text.trim()
                        cur.copy(wicFoodsJson = encodeFoods(m))
                    }
                }
                ShopEngine.WIC_FOODS.forEach { f ->
                    FoodRow("${f.label} (${f.unit})", foods[f.key] ?: "", can) { setAmount(f.key, it) }
                }
                custom.forEach { (key, cf) ->
                    FoodRow("${cf.label} (${cf.unit})", foods[key] ?: "", can, onRemove = if (can) ({
                        vm.edit { cur ->
                            cur.copy(
                                wicFoodsJson = encodeFoods(cur.wicFoods() - key),
                                wicCustomJson = encodeCustom(cur.wicCustom() - key),
                            )
                        }
                    }) else null) { setAmount(key, it) }
                }
                if (can) AddCustomFood { label, unit ->
                    vm.edit { cur ->
                        val key = "c_" + Integer.toHexString((label + unit + System.nanoTime()).hashCode()).takeLast(6)
                        cur.copy(wicCustomJson = encodeCustom(cur.wicCustom() + (key to CustomFood(label, unit.ifBlank { "each" }))))
                    }
                }
            }
            HorizontalDivider()

            /* ---------- coupons ---------- */
            Text("Coupons", style = MaterialTheme.typography.titleSmall, color = bc.coupon, fontWeight = FontWeight.SemiBold)
            SwitchRow("Charge tax on manufacturer-coupon savings", b.taxMfr, enabled = can) { v -> vm.edit { it.copy(taxMfr = v) } }
            Text("Most states tax the price before a manufacturer coupon. Store coupons always lower the taxed amount.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun MoneyRow(label: String, cents: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (enabled) PriceField(cents, { onChange(it ?: 0) }, Modifier.width(120.dp), label)
        else Text(com.godskingdomnow.groceryaccountant.data.money(cents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FoodRow(label: String, amount: String, enabled: Boolean, onRemove: (() -> Unit)? = null, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (enabled) TextFieldFor("Amount", amount, Modifier.width(110.dp), keyboard = KeyboardType.Decimal, onChange = onChange)
        else Text(amount.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove $label") }
    }
}

@Composable
private fun AddCustomFood(onAdd: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label, { label = it }, Modifier.weight(1.4f), label = { Text("Other WIC food") }, singleLine = true)
        OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Unit") }, singleLine = true)
        Button(onClick = { if (label.isNotBlank()) { onAdd(label.trim(), unit.trim()); label = ""; unit = "" } }) { Text("Add") }
    }
}
