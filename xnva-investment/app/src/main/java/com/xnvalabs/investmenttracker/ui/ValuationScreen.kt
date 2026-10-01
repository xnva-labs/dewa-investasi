package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ============================================================================
// TAB 3 — VALUATION CALCULATOR (P/E)
// ============================================================================

@Composable
fun ValuationScreen() {
    var priceInput by remember { mutableStateOf("") }
    var epsInput by remember { mutableStateOf("") }

    val price = priceInput.toDoubleOrNull()
    val eps = epsInput.toDoubleOrNull()
    val peRatio = if (price != null && eps != null && eps != 0.0) price / eps else null

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Kalkulator P/E Ratio",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "P/E Ratio = Harga Saham \u00F7 EPS (Earnings Per Share)",
            color = Color.Gray,
            fontSize = 12.sp
        )

        OutlinedTextField(
            value = priceInput,
            onValueChange = { priceInput = it },
            label = { Text("Harga Saham") },
            leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = epsInput,
            onValueChange = { epsInput = it },
            label = { Text("EPS (Earnings Per Share)") },
            leadingIcon = { Icon(Icons.Filled.ShowChart, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (peRatio != null) {
            val (statusLabel, statusColor, statusDesc) = when {
                peRatio < 0 -> Triple("Negatif (Rugi)", Color(0xFFEF4444), "EPS negatif berarti perusahaan sedang merugi. P/E tidak relevan dipakai sendirian di sini.")
                peRatio < 15 -> Triple("Cenderung Murah", Color(0xFF22C55E), "P/E di bawah 15x sering dianggap relatif murah, tapi tetap bandingkan dengan industri sejenis dan pertumbuhannya.")
                peRatio <= 25 -> Triple("Wajar / Fair Value", Color(0xFFF59E0B), "P/E di kisaran 15x-25x umumnya dianggap wajar untuk banyak sektor, tergantung pertumbuhan earnings.")
                else -> Triple("Cenderung Mahal", Color(0xFFEF4444), "P/E di atas 25x sering dianggap premium/mahal, biasanya butuh ekspektasi pertumbuhan tinggi untuk membenarkannya.")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Hasil P/E Ratio", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        String.format(Locale.US, "%.2fx", peRatio),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(statusDesc, color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\u26A0\uFE0F Catatan: P/E tinggi tidak otomatis buruk, dan P/E rendah tidak otomatis murah — harus dibandingkan dengan pertumbuhan, industri, dan kualitas bisnis.",
                        color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
