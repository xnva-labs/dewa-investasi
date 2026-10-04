package com.xnvalabs.smarteyex.ui.screens.age

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.smarteyex.data.age.AgeBand
import com.xnvalabs.smarteyex.data.age.AgeRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight

/**
 * Gerbang usia. Hanya meminta TAHUN lahir (netral, tanpa menyebut batas umur), dan memakai kelompok umur
 * untuk membatasi fitur. [fromSettings] = dibuka dari Privacy Control: layar tidak otomatis menutup dan
 * menampilkan status.
 */
@Composable
fun AgeGateScreen(fromSettings: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AgeRepository.init(context) }
    BackHandler(enabled = fromSettings) { onDone() }

    val band = AgeRepository.band.value
    val consent = AgeRepository.parentConsent.value
    var yearText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(band, consent) {
        if (!fromSettings && (band == AgeBand.ADULT || (band == AgeBand.TEEN && consent))) onDone()
    }

    Box(modifier = Modifier.fillMaxSize().background(LightBgWarm)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            when {
                band == AgeBand.UNKNOWN -> {
                    Heading("Satu hal dulu")
                    Body("Kamu lahir tahun berapa? Yang disimpan cuma tahunnya, terenkripsi di HP ini, buat nyesuaiin fitur yang pas untuk usiamu.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(16.dp)) {
                        if (yearText.isEmpty()) Text("mis. 2009", fontSize = 16.sp, color = TextMutedLight)
                        BasicTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryLight),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GateButton("Lanjut", enabled = yearText.length == 4) {
                        val year = yearText.toIntOrNull()
                        if (year == null) {
                            error = "Tahun lahir tidak valid."
                        } else {
                            AgeRepository.submitBirthYear(year)
                                .onSuccess {
                                    error = null
                                    PrivacyRepository.enforceAgePolicy()
                                }
                                .onFailure { error = it.message ?: "Tidak bisa menyimpan tahun lahir." }
                        }
                    }
                }

                band == AgeBand.CHILD -> {
                    Heading("Sampai ketemu nanti")
                    Body("SmartEyeX untuk usia 13 tahun ke atas. Kamu bisa tutup aplikasi ini dengan aman.")
                }

                band == AgeBand.TEEN && !consent -> {
                    Heading("Mode remaja")
                    Body("Fitur AI cloud (chat dan terjemahan) butuh persetujuan orang tua atau wali. Kamera ke cloud, pengenalan wajah, dan membaca notifikasi tidak tersedia untuk remaja.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Body("Orang tua/wali: dengan menyetujui, kamu mengizinkan pesan chat dan teks terjemahan anak dikirim ke server XNAI untuk dijawab. Tidak ada foto yang dikirim. Persetujuan bisa dicabut kapan saja di Privacy Control.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Konfirmasi ini dilakukan di perangkat dan belum memverifikasi identitas orang tua.",
                        fontSize = 11.sp,
                        color = TextMutedLight,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GateButton("Saya orang tua/wali dan menyetujui", enabled = true) {
                        if (AgeRepository.confirmParentConsent()) {
                            error = null
                            if (fromSettings) onDone()
                        } else {
                            error = "Persetujuan belum tersimpan. Coba lagi."
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GateButton("Lanjut tanpa AI cloud", enabled = true) { onDone() }
                    error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = AccentOrange)
                    }
                }

                band == AgeBand.TEEN -> {
                    Heading("Mode remaja")
                    Body("Persetujuan orang tua/wali aktif. Chat dan terjemahan AI tersedia; fitur sensitif tetap dimatikan.")
                    Spacer(modifier = Modifier.height(16.dp))
                    GateButton("Cabut persetujuan", enabled = true) {
                        if (AgeRepository.revokeParentConsent()) PrivacyRepository.enforceAgePolicy()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GateButton("Kembali", enabled = true) { onDone() }
                }

                else -> {
                    Heading("Kelompok usia: dewasa")
                    Body("Semua fitur tersedia. Fitur sensitif tetap mati sampai kamu mengaktifkannya sendiri di Privacy Control.")
                    Spacer(modifier = Modifier.height(16.dp))
                    GateButton("Kembali", enabled = true) { onDone() }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 14.sp, color = TextPrimaryLight)
}

@Composable
private fun GateButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightSurface, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) AccentOrange else TextMutedLight)
    }
}
