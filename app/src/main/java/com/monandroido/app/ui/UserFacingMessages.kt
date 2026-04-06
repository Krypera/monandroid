package com.monandroido.app.ui

import android.content.Context
import androidx.annotation.StringRes
import com.monandroido.data.model.LocalizedException

internal fun Throwable.userMessage(
    context: Context,
    @StringRes fallbackResId: Int,
): String = when (this) {
    is LocalizedException -> localizedText.resolve(context)
    else -> message ?: context.getString(fallbackResId)
}
