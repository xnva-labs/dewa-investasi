package id.fajar.zahra

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.fajar.zahra.camera.ProofResult
import id.fajar.zahra.data.AppRepository
import id.fajar.zahra.data.MissionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val repo: AppRepository, private val app: Application) : ViewModel() {
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val profile = repo.profile
    val missions = repo.missions
    val rewards = repo.rewards
    val points = repo.points
    val pointHistory = repo.pointHistory
    val events = repo.events
    val lists = repo.lists
    fun listItems(listId: Long) = repo.listItems(listId)
    val completedCount = missions
        .map { list -> list.sumOf { it.completionCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun saveProfile(name: String, age: Int, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            repo.saveProfile(name, age)
            repo.seedRewards()
            _actionMessage.value = "Profil tersimpan."
            onSuccess()
        }.onFailure { _actionMessage.value = it.message ?: "Profil belum dapat disimpan." }
    }

    fun addMission(title: String, description: String, category: String, points: Int, difficulty: Int, proof: String, target: String, whenAt: Long?, repeatRule: String) = viewModelScope.launch {
        runCatching {
            repo.addMission(title, description, category, points, difficulty, proof, target, whenAt, repeatRule)
            _actionMessage.value = "Misi ditambahkan."
        }.onFailure { _actionMessage.value = it.message ?: "Misi belum dapat dibuat." }
    }

    fun updateMission(current: MissionEntity, title: String, description: String, category: String, points: Int, difficulty: Int, proof: String, target: String, scheduledAt: Long?, repeatRule: String) = viewModelScope.launch {
        runCatching { repo.updateMission(current, title, description, category, points, difficulty, proof, target, scheduledAt, repeatRule) }
            .onSuccess { ok -> _actionMessage.value = if (ok) "Misi diperbarui." else "Misi tidak berubah." }
            .onFailure { _actionMessage.value = it.message ?: "Misi belum dapat diperbarui." }
    }

    fun complete(m: MissionEntity) = viewModelScope.launch {
        runCatching { repo.completeMission(m) }
            .onSuccess { ok -> _actionMessage.value = if (ok) "Misi selesai." else "Misi belum dapat diselesaikan. Periksa status misi atau proof." }
            .onFailure { _actionMessage.value = it.message ?: "Misi belum dapat diselesaikan." }
    }

    fun pause(id: Long) = viewModelScope.launch {
        _actionMessage.value = if (repo.pauseMission(id) > 0) "Misi dijeda." else "Misi tidak dapat dijeda."
    }

    fun resume(id: Long) = viewModelScope.launch {
        _actionMessage.value = if (repo.resumeMission(id) > 0) "Misi dilanjutkan." else "Misi tidak dapat dilanjutkan."
    }

    fun archive(m: MissionEntity) = viewModelScope.launch {
        repo.archiveMission(m)
        _actionMessage.value = "Misi diarsipkan. History tetap tersimpan."
    }

    fun addList(title: String, description: String) = viewModelScope.launch {
        runCatching { repo.addList(title, description) }
            .onSuccess { _actionMessage.value = "Daftar dibuat." }
            .onFailure { _actionMessage.value = it.message ?: "Daftar belum dapat dibuat." }
    }

    fun addListItem(listId: Long, title: String) = viewModelScope.launch {
        runCatching { repo.addListItem(listId, title) }
            .onSuccess { _actionMessage.value = "Item ditambahkan." }
            .onFailure { _actionMessage.value = it.message ?: "Item belum dapat ditambahkan." }
    }

    fun setListItemChecked(itemId: Long, checked: Boolean) = viewModelScope.launch {
        runCatching { repo.setListItemChecked(itemId, checked) }
            .onFailure { _actionMessage.value = it.message ?: "Status item belum dapat diubah." }
    }

    fun archiveList(id: Long) = viewModelScope.launch {
        runCatching { repo.archiveList(id) }
            .onSuccess { _actionMessage.value = "Daftar diarsipkan." }
            .onFailure { _actionMessage.value = it.message ?: "Daftar belum dapat diarsipkan." }
    }

    fun claimReward(id: Long) = viewModelScope.launch {
        _actionMessage.value = if (repo.claimReward(id)) "Reward diklaim." else "Reward belum dapat diklaim."
    }

    fun recordProof(result: ProofResult) = viewModelScope.launch {
        _actionMessage.value = if (repo.recordProof(result)) "Bukti tersimpan. Misi dapat diselesaikan." else "Bukti tidak cocok dengan misi."
    }

    fun seedRewards() = viewModelScope.launch {
        repo.seedRewards()
    }

    fun refreshReminders() = viewModelScope.launch {
        repo.rescheduleFutureReminders()
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        runCatching { repo.setNotifications(enabled) }
            .onSuccess { _actionMessage.value = if (enabled) "Reminder aktif." else "Reminder dinonaktifkan." }
            .onFailure { _actionMessage.value = it.message ?: "Pengaturan reminder gagal." }
    }

    fun resetApplication(onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.resetApplication() }
            .onSuccess { onDone() }
            .onFailure { _actionMessage.value = it.message ?: "Reset gagal." }
    }

    companion object {
        fun factory(repo: AppRepository, app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo, app) as T
        }
    }
}
