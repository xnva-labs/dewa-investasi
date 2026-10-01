package com.xnvalabs.investmenttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xnvalabs.investmenttracker.model.Asset
import com.xnvalabs.investmenttracker.model.AssetClass
import com.xnvalabs.investmenttracker.model.Currency
import com.xnvalabs.investmenttracker.model.TrackerType
import java.math.BigDecimal

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: Int,
    val ticker: String,
    val name: String,
    val assetClass: String,   // disimpan sebagai nama enum (mis. "SAHAM")
    val trackerType: String,  // disimpan sebagai nama enum (mis. "INVESTASI")
    val qty: BigDecimal,
    val avgPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val currency: String = "USD" // nama enum Currency ("USD" / "IDR")
)

fun AssetEntity.toAsset(): Asset = Asset(
    id = id,
    ticker = ticker,
    name = name,
    assetClass = runCatching { AssetClass.valueOf(assetClass) }.getOrDefault(AssetClass.LAINNYA),
    trackerType = runCatching { TrackerType.valueOf(trackerType) }.getOrDefault(TrackerType.INVESTASI),
    qty = qty,
    avgPrice = avgPrice,
    currentPrice = currentPrice,
    currency = runCatching { Currency.valueOf(currency) }.getOrDefault(Currency.USD)
)

fun Asset.toEntity(): AssetEntity = AssetEntity(
    id = id,
    ticker = ticker,
    name = name,
    assetClass = assetClass.name,
    trackerType = trackerType.name,
    qty = qty,
    avgPrice = avgPrice,
    currentPrice = currentPrice,
    currency = currency.name
)
