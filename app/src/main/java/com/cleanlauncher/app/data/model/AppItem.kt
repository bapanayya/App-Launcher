package com.cleanlauncher.app.data.model

import android.graphics.drawable.Drawable

data class AppItem(
    val id: String,
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    val category: AppCategory,
    val isFavorite: Boolean = false,
    val isSystemApp: Boolean = false,
    val installTime: Long = 0L
)
