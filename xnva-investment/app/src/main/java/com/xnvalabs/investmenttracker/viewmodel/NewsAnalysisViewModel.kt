package com.xnvalabs.investmenttracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xnvalabs.investmenttracker.data.remote.AiAnalysisRepository
import com.xnvalabs.investmenttracker.data.remote.NewsAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State untuk fitur "Tanya AI" di tab Sentiment: cari berita real-time
 * (Google News RSS) soal satu aset, lalu minta Gemini merumuskan faktor
 * pendukung naik/turun — HANYA berdasarkan berita itu, bukan prediksi pasti.
 */
class NewsAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiAnalysisRepository()
    val isConfigured: Boolean get() = repository.isConfigured

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _result = MutableStateFlow<NewsAnalysisResult?>(null)
    val result: StateFlow<NewsAnalysisResult?> = _result.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun analyze(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (!repository.isConfigured) {
            _errorMessage.value = "GEMINI_API_KEY belum diset di build. Lihat PANDUAN-GITHUB-BUILD.md."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val analysis = repository.analyze(trimmed)
            _isLoading.value = false
            if (analysis == null) {
                _errorMessage.value = "Gagal ambil analisis. Cek koneksi internet, atau coba lagi sebentar lagi."
            } else {
                _result.value = analysis
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
