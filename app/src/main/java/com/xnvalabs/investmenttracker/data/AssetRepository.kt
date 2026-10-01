package com.xnvalabs.investmenttracker.data

import com.xnvalabs.investmenttracker.data.local.AssetDao
import com.xnvalabs.investmenttracker.data.local.toAsset
import com.xnvalabs.investmenttracker.data.local.toEntity
import com.xnvalabs.investmenttracker.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Satu-satunya sumber kebenaran untuk data Asset. Semua baca/tulis
 * portfolio HARUS lewat repository ini (bukan langsung ke DAO atau ke
 * network), supaya kalau nanti sumber data berubah (mis. tambah sync ke
 * cloud), cuma class ini yang perlu disentuh — UI & ViewModel tidak perlu
 * tahu detail Room di baliknya.
 */
class AssetRepository(private val dao: AssetDao) {

    fun observeAssets(): Flow<List<Asset>> =
        dao.observeAll().map { entities -> entities.map { it.toAsset() } }

    suspend fun seedIfEmpty(seed: List<Asset>) {
        if (dao.count() == 0) {
            dao.insertAll(seed.map { it.toEntity() })
        }
    }

    suspend fun upsert(asset: Asset) = dao.upsert(asset.toEntity())

    suspend fun delete(asset: Asset) = dao.deleteById(asset.id)
}
