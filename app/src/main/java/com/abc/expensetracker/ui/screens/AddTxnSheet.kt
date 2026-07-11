package com.abc.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abc.expensetracker.sms.parser.Direction
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.util.Money

/** Manual entry for the rare cash spend that never hits SMS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxnSheet(vm: TxnListVm, onDismiss: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(Direction.DEBIT) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Add transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == Direction.DEBIT,
                    onClick = { direction = Direction.DEBIT },
                    label = { Text("Spent") },
                )
                FilterChip(
                    selected = direction == Direction.CREDIT,
                    onClick = { direction = Direction.CREDIT },
                    label = { Text("Received") },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount (₹)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = merchant, onValueChange = { merchant = it },
                label = { Text("Merchant / person") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Account", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts) { a ->
                    FilterChip(
                        selected = accountId == a.id,
                        onClick = { accountId = a.id },
                        label = { Text(a.name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val paise = Money.parse(amount) ?: return@Button
                    val cat = categoryId ?: return@Button
                    vm.addManual(paise, direction, merchant.trim(), cat, accountId, note)
                    onDismiss()
                },
                enabled = Money.parse(amount) != null && categoryId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(Modifier.height(32.dp))
        }
    }
}
