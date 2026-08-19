package com.damsan.green.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.damsan.green.R

fun AppCompatActivity.showDamSanInfoDialog(
    title: String,
    message: String,
    iconRes: Int = R.drawable.ic_info,
    buttonText: String = "Đã hiểu"
): AlertDialog {
    return showDamSanActionDialog(
        title = title,
        message = message,
        iconRes = iconRes,
        positiveText = buttonText
    )
}

fun AppCompatActivity.showDamSanConfirmDialog(
    title: String,
    message: String,
    iconRes: Int = R.drawable.ic_info,
    positiveText: String,
    negativeText: String = "Huỷ",
    danger: Boolean = false,
    onConfirm: () -> Unit
): AlertDialog {
    return showDamSanActionDialog(
        title = title,
        message = message,
        iconRes = iconRes,
        positiveText = positiveText,
        negativeText = negativeText,
        danger = danger
    ) {
        onConfirm()
    }
}

fun AppCompatActivity.showDamSanActionDialog(
    title: String,
    message: String,
    iconRes: Int = R.drawable.ic_info,
    positiveText: String = "Đã hiểu",
    negativeText: String? = null,
    danger: Boolean = false,
    contentView: View? = null,
    dismissOnPositive: Boolean = true,
    onPositive: (AlertDialog) -> Unit = {}
): AlertDialog {
    fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(this@showDamSanActionDialog, R.drawable.bg_dialog_surface)
        setPadding(20.dp(), 20.dp(), 20.dp(), 18.dp())
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val iconFrame = FrameLayout(this).apply {
        background = ContextCompat.getDrawable(this@showDamSanActionDialog, R.drawable.bg_history_summary)
        layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp())
    }

    iconFrame.addView(
        ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@showDamSanActionDialog,
                    if (danger) R.color.ds_error else R.color.ds_primary
                )
            )
            layoutParams = FrameLayout.LayoutParams(22.dp(), 22.dp(), Gravity.CENTER)
        }
    )

    val titleView = TextView(this).apply {
        text = title
        setTextColor(ContextCompat.getColor(this@showDamSanActionDialog, R.color.ds_text_primary))
        textSize = 18f
        typeface = ResourcesCompat.getFont(this@showDamSanActionDialog, R.font.be_vietnam_pro_bold)
            ?: Typeface.DEFAULT_BOLD
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12.dp()
        }
    }

    header.addView(iconFrame)
    header.addView(titleView)
    root.addView(header)

    TextView(this).apply {
        text = message
        setTextColor(ContextCompat.getColor(this@showDamSanActionDialog, R.color.ds_text_secondary))
        textSize = 13.5f
        typeface = ResourcesCompat.getFont(this@showDamSanActionDialog, R.font.be_vietnam_pro_regular)
            ?: Typeface.DEFAULT
        setLineSpacing(3.dp().toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 14.dp()
        }
    }.also(root::addView)

    contentView?.let { view ->
        root.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 14.dp()
            }
        )
    }

    val buttonRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 18.dp()
        }
    }

    val dialog = AlertDialog.Builder(this)
        .setView(root)
        .create()

    fun makeButton(text: String, primary: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            textSize = 13.5f
            typeface = ResourcesCompat.getFont(this@showDamSanActionDialog, R.font.be_vietnam_pro_semibold)
                ?: Typeface.DEFAULT_BOLD
            setTextColor(
                ContextCompat.getColor(
                    this@showDamSanActionDialog,
                    when {
                        primary -> R.color.white
                        else -> R.color.ds_primary
                    }
                )
            )
            background = ContextCompat.getDrawable(
                this@showDamSanActionDialog,
                when {
                    primary && danger -> R.drawable.bg_button_red
                    primary -> R.drawable.bg_btn_solid_green
                    else -> R.drawable.bg_btn_secondary
                }
            )
            layoutParams = LinearLayout.LayoutParams(0, 48.dp(), 1f)
        }
    }

    negativeText?.let {
        val negativeButton = makeButton(it, primary = false)
        negativeButton.setOnClickListener { dialog.dismiss() }
        buttonRow.addView(negativeButton)
    }

    val positiveButton = makeButton(positiveText, primary = true).apply {
        if (negativeText != null) {
            (layoutParams as LinearLayout.LayoutParams).marginStart = 10.dp()
        }
    }
    positiveButton.setOnClickListener {
        onPositive(dialog)
        if (dismissOnPositive) dialog.dismiss()
    }
    buttonRow.addView(positiveButton)
    root.addView(buttonRow)

    dialog.setOnShowListener {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.5f)
            setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
    dialog.show()
    return dialog
}
