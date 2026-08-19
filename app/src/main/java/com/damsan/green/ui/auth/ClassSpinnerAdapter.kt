package com.damsan.green.ui.auth

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.damsan.green.R

class ClassSpinnerAdapter(
    context: Context,
    private val classes: List<String>
) : ArrayAdapter<String>(context, 0, classes) {

    private val inflater = LayoutInflater.from(context)
    private var selectedPosition = 0

    fun setSelectedPosition(position: Int) {
        selectedPosition = position.coerceIn(classes.indices)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_class_spinner_selected, parent, false)
        val className = getItem(position).orEmpty()
        view.findViewById<TextView>(R.id.tvSelectedClassName).text = className
        view.findViewById<TextView>(R.id.tvSelectedClassGrade).text = "Khối ${className.take(2)}"
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_class_spinner_dropdown, parent, false)
        val className = getItem(position).orEmpty()
        view.isSelected = position == selectedPosition
        view.findViewById<TextView>(R.id.tvDropdownClassName).text = "Lớp $className"
        view.findViewById<TextView>(R.id.tvDropdownClassGrade).text = "K${className.take(2)}"
        view.findViewById<TextView>(R.id.tvDropdownClassHint).text = classHint(className)
        view.findViewById<ImageView>(R.id.ivDropdownSelected).visibility =
            if (position == selectedPosition) View.VISIBLE else View.INVISIBLE
        return view
    }

    private fun classHint(className: String): String {
        return when (className.take(2)) {
            "10" -> "Trực nhật khu học tập"
            "11" -> "Phụ trách mảng xanh"
            "12" -> "Thi đua môi trường"
            else -> "Dam San Green"
        }
    }
}

fun Spinner.fitClassDropdownToField() {
    post {
        dropDownWidth = width
        dropDownHorizontalOffset = -paddingStart
    }
}
