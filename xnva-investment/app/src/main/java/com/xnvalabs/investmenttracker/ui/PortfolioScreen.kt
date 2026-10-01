package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.investmenttracker.model.Asset
import com.xnvalabs.investmenttracker.model.AssetClass
import com.xnvalabs.investmenttracker.model.Currency
import com.xnvalabs.investmenttracker.model.TrackerType
import com.xnvalabs.investmenttracker.model.pnlInIdr
import com.xnvalabs.investmenttracker.model.valueInIdr
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

// ============================================================================
// TAB 1 — PORTFOLIO & TRACKER
// - Total saldo & P/L dihitung dalam RUPIAH: aset USD dikonversi pakai kurs
//   USD/IDR; aset IDR dipakai apa adanya.
// - Tiap kartu aset menampilkan nilai dalam mata uang aslinya.
// - Refresh live: Crypto (Binance/CoinGecko) + Saham/ETF US (Finnhub),
//   hanya untuk aset berdenominasi USD.
// ============================================================================

fun formatRupiahLike(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
    format.maximumFractionDigits = 2
    return format.format(value)
}

/** Format nominal sesuai mata uangnya: "Rp 1.234.567" atau "US$ 1,234.56". */
fun formatMoney(value: BigDecimal, currency: Currency): String = when (currency) {
    Currency.IDR -> "Rp ${formatRupiahLike(value)}"
    Currency.USD -> {
        val format = NumberFormat.getNumberInstance(Locale.US)
        format.maximumFractionDigits = 2
        "US$ ${format.format(value)}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    assets: List<Asset>,
    usdIdrRate: BigDecimal?,
    isRefreshing: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onAddAsset: (Asset) -> Unit,
    onDeleteAsset: (Asset) -> Unit,
    onSimulate: () -> Unit,
    onRefreshCrypto: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<TrackerType?>(null) }

    val filteredAssets = if (filterType == null) assets else assets.filter { it.trackerType == filterType }

    // Aset USD tanpa kurs TIDAK dihitung sebagai 0 — dikeluarkan dan diberi peringatan.
    val totalValue = assets.mapNotNull { it.valueInIdr(usdIdrRate) }
        .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    val totalPnl = assets.mapNotNull { it.pnlInIdr(usdIdrRate) }
        .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    val excludedCount = assets.count { it.valueInIdr(usdIdrRate) == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Saldo Portfolio (Rupiah)", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Rp ${formatRupiahLike(totalValue)}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    val pnlColor = if (totalPnl >= BigDecimal.ZERO) Color(0xFF22C55E) else Color(0xFFEF4444)
                    val sign = if (totalPnl >= BigDecimal.ZERO) "+" else ""
                    Text(
                        "$sign Rp ${formatRupiahLike(totalPnl)} P/L keseluruhan",
                        color = pnlColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (usdIdrRate != null) {
                        Text(
                            "Kurs USD/IDR: Rp ${formatRupiahLike(usdIdrRate)}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    if (excludedCount > 0) {
                        Text(
                            "Kurs USD/IDR belum tersedia — $excludedCount aset USD belum ikut dihitung. Tekan Refresh saat online.",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(errorMessage, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismissError) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onRefreshCrypto,
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Mengambil harga live & kurs...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Harga Live & Kurs", fontSize = 12.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSimulate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simulasikan Alert", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah Aset")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("Semua") }
                )
                FilterChip(
                    selected = filterType == TrackerType.INVESTASI,
                    onClick = { filterType = TrackerType.INVESTASI },
                    label = { Text("Investasi") }
                )
                FilterChip(
                    selected = filterType == TrackerType.TRADING,
                    onClick = { filterType = TrackerType.TRADING },
                    label = { Text("Trading") }
                )
            }
        }

        item {
            Text(
                "Daftar Aset (${filteredAssets.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(filteredAssets, key = { it.id }) { asset ->
            AssetCard(asset = asset, onDelete = onDeleteAsset)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        AddAssetDialog(
            existingTickers = assets.map { it.ticker.uppercase() }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { newAsset ->
                onAddAsset(newAsset)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AssetCard(asset: Asset, onDelete: (Asset) -> Unit) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    val pnlColor = if (asset.pnl >= BigDecimal.ZERO) Color(0xFF22C55E) else Color(0xFFEF4444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    asset.ticker.take(2),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${asset.assetClass.label} \u2022 ${asset.trackerType.label} \u2022 ${asset.currency.code}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    "Qty: ${asset.qty.stripTrailingZeros().toPlainString()}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(asset.marketValue, asset.currency),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                val sign = if (asset.pnl >= BigDecimal.ZERO) "+" else ""
                Text(
                    "$sign${String.format(Locale.US, "%.2f", asset.pnlPercent)}%",
                    color = pnlColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = { showConfirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus ${asset.ticker}", tint = Color.Gray)
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus ${asset.ticker}?") },
            text = { Text("Aset ini akan dihapus permanen dari portfolio (termasuk dari database lokal).") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(asset)
                    showConfirmDelete = false
                }) { Text("Hapus", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Batal") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetDialog(existingTickers: Set<String>, onDismiss: () -> Unit, onAdd: (Asset) -> Unit) {
    var ticker by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var assetClass by remember { mutableStateOf(AssetClass.SAHAM) }
    var trackerType by remember { mutableStateOf(TrackerType.INVESTASI) }
    var currency by remember { mutableStateOf(Currency.USD) }
    var classMenuExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Aset Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bisa untuk saham, ETF, crypto, forex/mata uang apa pun — tidak dibatasi.",
                    fontSize = 11.sp, color = Color.Gray
                )
                OutlinedTextField(
                    value = ticker, onValueChange = { ticker = it.uppercase(); errorText = null },
                    label = { Text("Ticker (mis. AAPL, BTC, BBCA)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama Aset") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it; errorText = null },
                    label = { Text("Jumlah / Qty") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it; errorText = null },
                    label = { Text("Harga per unit (${currency.code})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mata uang:", fontSize = 12.sp, color = Color.Gray)
                    FilterChip(
                        selected = currency == Currency.USD,
                        onClick = { currency = Currency.USD },
                        label = { Text("USD") }
                    )
                    FilterChip(
                        selected = currency == Currency.IDR,
                        onClick = { currency = Currency.IDR },
                        label = { Text("IDR") }
                    )
                }

                Box {
                    OutlinedButton(onClick = { classMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kelas: ${assetClass.label}")
                    }
                    DropdownMenu(expanded = classMenuExpanded, onDismissRequest = { classMenuExpanded = false }) {
                        AssetClass.values().forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = {
                                assetClass = c; classMenuExpanded = false
                            })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = trackerType == TrackerType.INVESTASI,
                        onClick = { trackerType = TrackerType.INVESTASI },
                        label = { Text("Investasi") }
                    )
                    FilterChip(
                        selected = trackerType == TrackerType.TRADING,
                        onClick = { trackerType = TrackerType.TRADING },
                        label = { Text("Trading") }
                    )
                }

                if (errorText != null) {
                    Text(errorText!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                }

                val hint = when {
                    assetClass == AssetClass.CRYPTO && currency == Currency.USD ->
                        "Harga live via Binance (pair <TICKER>USDT) lalu CoinGecko. Ticker umum seperti BTC, ETH, SOL didukung."
                    (assetClass == AssetClass.SAHAM || assetClass == AssetClass.ETF) && currency == Currency.USD ->
                        "Harga live via Finnhub hanya untuk saham/ETF US (mis. AAPL, NVDA, VOO). Saham IDX belum didukung — pilih IDR dan isi harga manual."
                    else ->
                        "Harga live hanya untuk Crypto/Saham/ETF berdenominasi USD. Aset ini harganya tetap sesuai input."
                }
                Text(hint, fontSize = 10.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toBigDecimalOrNull()
                val p = price.toBigDecimalOrNull()
                errorText = when {
                    ticker.isBlank() -> "Ticker wajib diisi."
                    ticker.uppercase() in existingTickers -> "Ticker \"$ticker\" sudah ada di portfolio."
                    q == null || q <= BigDecimal.ZERO -> "Qty harus angka lebih dari 0."
                    p == null || p <= BigDecimal.ZERO -> "Harga harus angka lebih dari 0."
                    else -> null
                }
                if (errorText == null && q != null && p != null) {
                    onAdd(
                        Asset(
                            id = 0,
                            ticker = ticker,
                            name = name.ifBlank { ticker },
                            assetClass = assetClass,
                            trackerType = trackerType,
                            qty = q,
                            avgPrice = p,
                            currentPrice = p,
                            currency = currency
                        )
                    )
                }
            }) { Text("Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
