package com.xnvalabs.smarteyex.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight

/** In-app privacy/data disclosure that mirrors SmartEyeX's actual controls. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LightBgWarm).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("‹", fontSize = 26.sp, color = TextPrimaryLight, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
            Text("Privacy & Data", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
        }
        Spacer(modifier = Modifier.height(16.dp))
        PolicySection("Apa yang diproses") {
            "SmartEyeX dapat memproses suara, gambar kamera, isi notifikasi, dan data yang kamu simpan sebagai memory hanya setelah fitur dan izin Android yang relevan diaktifkan."
        }
        PolicySection("Penyimpanan lokal") {
            "Data sensitif yang disimpan di perangkat dienkripsi menggunakan Android Keystore. Memory, PIN, reminder, kontak, pendidikan, dan board enterprise tidak disimpan sebagai teks biasa."
        }
        PolicySection("Cloud AI") {
            "Pesan, konteks memory, teks terjemahan, atau frame kamera hanya boleh dikirim ke backend ketika Cloud Processing aktif. Ketika Memory OFF, isi memory tidak dimasukkan ke payload XNAI."
        }
        PolicySection("Notifikasi") {
            "Isi notifikasi hanya diproses untuk Communication Assistant ketika Notification Content diaktifkan. Feed notifikasi SmartEyeX bersifat volatile dan dibatasi jumlahnya di memori proses."
        }
        PolicySection("Mic selalu aktif (opsional)") {
            "Jika kamu menyalakannya, mic mendengarkan terus dengan notifikasi permanen. Hanya ucapan yang diawali kata panggil \"SmartEyeX\" " +
                "(atau \"jawab <nama>\" tepat setelah pesan dari orang itu masuk) yang diproses; ucapan lain langsung dibuang dan tidak disimpan. " +
                "Suara dikenali oleh layanan pengenalan suara Android. Matikan lewat tombol di notifikasi, ketuk VOICE di layar XNAI, " +
                "atau ucapkan \"SmartEyeX matikan mic\"."
        }
        PolicySection("Kontrol pengguna") {
            "Kamu dapat mematikan fitur sensitif kapan saja dan memakai menu reset untuk menghapus memory, reminder, kontak, data pendidikan, data enterprise, dan feed notifikasi lokal."
        }
        PolicySection("Data tidak diperlukan") {
            "SmartEyeX tidak meminta akses kontak, panggilan telepon langsung, atau lokasi perangkat untuk fitur yang ada di build ini. Permission tambahan tidak diberikan hanya untuk membuat fitur terlihat lebih lengkap."
        }
        PolicySection("Penting untuk publikasi") {
            "Teks ini adalah disclosure in-app. Sebelum publikasi Google Play, halaman kebijakan privasi publik pada domain resmi XNVA harus memuat praktik data yang sama dan URL tersebut dicantumkan di Play Console."
        }
    }
}

@Composable
private fun PolicySection(title: String, body: () -> String) {
    Spacer(modifier = Modifier.height(12.dp))
    Column(
        modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
        Spacer(modifier = Modifier.height(6.dp))
        Text(body(), fontSize = 12.sp, lineHeight = 18.sp, color = TextMutedLight)
    }
}
