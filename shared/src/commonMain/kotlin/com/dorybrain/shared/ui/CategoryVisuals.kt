package com.dorybrain.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dorybrain.shared.model.Category
import com.dorybrain.shared.ui.theme.CategoryAmber
import com.dorybrain.shared.ui.theme.CategoryBlue
import com.dorybrain.shared.ui.theme.CategoryEmerald
import com.dorybrain.shared.ui.theme.CategoryGreen
import com.dorybrain.shared.ui.theme.CategoryOrange
import com.dorybrain.shared.ui.theme.CategoryRed
import com.dorybrain.shared.ui.theme.CategorySlate
import com.dorybrain.shared.ui.theme.CategoryViolet

/**
 * The colour and glyph each bucket is drawn with. Kept in the UI layer so the
 * data model stays free of Compose types.
 */
val Category.accentColor: Color
    get() = when (this) {
        Category.WORK -> CategoryBlue
        Category.PERSONAL -> CategoryGreen
        Category.SHOPPING -> CategoryOrange
        Category.IDEAS -> CategoryViolet
        Category.HEALTH -> CategoryRed
        Category.FINANCE -> CategoryEmerald
        Category.REMINDERS -> CategoryAmber
        Category.OTHER -> CategorySlate
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.WORK -> Icons.Filled.Work
        Category.PERSONAL -> Icons.Filled.Person
        Category.SHOPPING -> Icons.Filled.ShoppingCart
        Category.IDEAS -> Icons.Filled.Lightbulb
        Category.HEALTH -> Icons.Filled.Favorite
        Category.FINANCE -> Icons.Filled.AttachMoney
        Category.REMINDERS -> Icons.Filled.Notifications
        Category.OTHER -> Icons.Filled.MoreHoriz
    }
