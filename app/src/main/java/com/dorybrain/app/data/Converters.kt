package com.dorybrain.app.data

import androidx.room.TypeConverter
import com.dorybrain.shared.model.Category

class Converters {
    @TypeConverter
    fun fromCategory(category: Category): String = category.name

    @TypeConverter
    fun toCategory(name: String): Category = Category.fromName(name)
}
