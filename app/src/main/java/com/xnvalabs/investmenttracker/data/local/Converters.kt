package com.xnvalabs.investmenttracker.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room tidak tahu cara menyimpan BigDecimal secara native, jadi dikonversi
 * ke String (toPlainString) supaya presisi penuh tidak hilang — beda
 * dengan konversi ke Double yang berisiko rounding error untuk nominal
 * uang.
 */
class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }
}
