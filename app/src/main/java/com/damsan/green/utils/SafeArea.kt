package com.damsan.green.utils

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Applies system-bar insets without relying on a device-specific top margin. */
object SafeArea {
    fun apply(activity: Activity, root: View, keepExistingPadding: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val initial = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                initial.left,
                if (keepExistingPadding) initial.top + bars.top else bars.top,
                initial.right,
                if (keepExistingPadding) initial.bottom + bars.bottom else bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
