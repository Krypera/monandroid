package com.monandroido.data.model

import android.content.Context
import androidx.annotation.StringRes

data class LocalizedText(
    @StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String = context.getString(resId, *formatArgs.toTypedArray())
}

open class LocalizedException(
    val localizedText: LocalizedText,
) : IllegalArgumentException()
