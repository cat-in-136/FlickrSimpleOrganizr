package io.github.cat_in_136.flickrsimpleorganizr

import android.content.Context
import android.support.annotation.StringRes
import android.support.v7.app.AlertDialog
import kotlin.coroutines.experimental.suspendCoroutine

internal suspend fun alert(context: Context, @StringRes messageId: Int, @StringRes titleId: Int = -1) {
    AlertDialog.Builder(context)
            .setMessage(messageId)
            .setPositiveButton(android.R.string.ok, null)
            .apply {
                if (titleId != -1) {
                    this.setTitle(titleId)
                }
                alert(this)
            }
}

internal suspend fun alert(context: Context, message: String, title: String? = null) {
    AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .apply {
                if (title != null) {
                    this.setTitle(title)
                }
                alert(this)
            }
}

private suspend fun alert(builder: AlertDialog.Builder) = suspendCoroutine<Nothing?> { continuation ->
    builder.setOnDismissListener({ continuation.resume(null) })
            .show()
}
