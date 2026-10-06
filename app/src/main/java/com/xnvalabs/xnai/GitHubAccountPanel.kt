package com.xnvalabs.xnai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Shared GitHub account + token slot. Used in Perpustakaan, Cara Berfikir and Pengaturan. */
@Composable
fun GitHubAccountPanel(settings: XnaiSettings) {
    var accountId by rememberSaveable { mutableStateOf("primary") }
    var owner by rememberSaveable { mutableStateOf(settings.github.owner) }
    var repo by rememberSaveable { mutableStateOf(settings.github.repo) }
    var branch by rememberSaveable { mutableStateOf(settings.github.branch.ifBlank { "main" }) }
    var token by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GitHub sync (multi-akun)", fontWeight = FontWeight.Bold)
            Text("File JSON/MD maks 20 MiB lalu otomatis ganti file baru; tiap upload maks 50 MiB. Isi file hanya simbol. Token disimpan terenkripsi (Keystore), tidak pernah masuk file.")
            OutlinedTextField(accountId, { accountId = it }, label = { Text("ID akun (primary, backup1, …)") }, singleLine = true)
            OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, singleLine = true)
            OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, singleLine = true)
            OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true)
            OutlinedTextField(
                token, { token = it }, label = { Text("Token (ghp_…)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val id = accountId.trim()
                    if (id.isBlank() || owner.isBlank() || repo.isBlank() || token.isBlank()) {
                        message = "Isi ID akun, owner, repository, dan token."
                    } else {
                        try {
                            settings.saveGitHubAccount(GitHubAccount(id, owner.trim(), repo.trim(), branch.trim().ifBlank { "main" }, "XNAI_LIBRARY", true), token)
                            if (id == "primary") {
                                settings.github = settings.github.copy(owner = owner.trim(), repo = repo.trim(), branch = branch.trim().ifBlank { "main" }, enabled = true)
                                settings.setGitHubToken(token)
                            }
                            token = ""
                            tick++
                            message = "Akun '$id' tersimpan."
                        } catch (e: Throwable) {
                            message = e.message ?: "Gagal menyimpan akun."
                        }
                    }
                }) { Text("Simpan akun") }
                OutlinedButton(onClick = {
                    val id = accountId.trim()
                    settings.removeGitHubAccount(id)
                    if (id == "primary") { settings.clearGitHubToken(); settings.github = settings.github.copy(enabled = false) }
                    tick++
                    message = "Akun '$id' dihapus."
                }) { Text("Hapus akun") }
            }
            val accounts = remember(tick) { settings.githubAccounts() }
            accounts.forEach { a ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${a.id}: ${a.owner}/${a.repo}@${a.branch}")
                    TextButton(onClick = { settings.setGitHubAccountEnabled(a.id, !a.enabled); tick++ }) {
                        Text(if (a.enabled) "Matikan" else "Aktifkan")
                    }
                }
            }
            if (message.isNotBlank()) Text(message, color = XNACyan)
        }
    }
}
