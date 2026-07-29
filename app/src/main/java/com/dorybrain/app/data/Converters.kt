package com.dorybrain.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCategory(category: Category): String = category.name

    @TypeConverter
    fun toCategory(name: String): Category =
        runCatching { Category.valueOf(name) }.getOrDefault(Category.OTHER)
}
