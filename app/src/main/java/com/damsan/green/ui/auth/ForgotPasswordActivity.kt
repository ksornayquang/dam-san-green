package com.damsan.green.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.damsan.green.R
import com.damsan.green.data.repository.AuthService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private val authService = AuthService()

    private val classOptions = listOf(
        "10A1", "10A2", "10A3", "10A4", "10A5", "10A6",
        "11A1", "11A2", "11A3", "11A4", "11A5", "11A6",
        "12A1", "12A2", "12A3", "12A4", "12A5", "12A6"
    )

    private lateinit var spinnerClass: Spinner
    private lateinit var tvResetEmail: TextView
    private lateinit var btnSendReset: FrameLayout
    private lateinit var tvSendResetText: TextView
    private lateinit var progressReset: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        spinnerClass = findViewById(R.id.spinnerResetClass)
        tvResetEmail = findViewById(R.id.tvResetEmail)
        btnSendReset = findViewById(R.id.btnSendReset)
        tvSendResetText = findViewById(R.id.tvSendResetText)
        progressReset = findViewById(R.id.progressReset)

        setupClassPicker()
        setupClickListeners()
    }

    private fun setupClassPicker() {
        val adapter = ClassSpinnerAdapter(this, classOptions)
        spinnerClass.adapter = adapter
        spinnerClass.fitClassDropdownToField()
        val defaultIndex = classOptions.indexOf("11A1")
        spinnerClass.setSelection(defaultIndex)
        adapter.setSelectedPosition(defaultIndex)
        updateEmailPreview()

        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                adapter.setSelectedPosition(position)
                updateEmailPreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        btnSendReset.setOnClickListener {
            val email = getSelectedClassEmail()
            setLoading(true)

            lifecycleScope.launch {
                val result = authService.sendPasswordReset(email)
                setLoading(false)

                result.fold(
                    onSuccess = {
                        showSnackbar("Đã gửi email khôi phục tới $email", false)
                    },
                    onFailure = { error ->
                        showSnackbar("Không gửi được email: ${error.message}", true)
                    }
                )
            }
        }
    }

    private fun updateEmailPreview() {
        tvResetEmail.text = getSelectedClassEmail()
    }

    private fun getSelectedClassEmail(): String {
        val selectedClass = spinnerClass.selectedItem?.toString() ?: "11A1"
        return "${selectedClass.lowercase()}@damsan.edu.vn"
    }

    private fun setLoading(isLoading: Boolean) {
        btnSendReset.isEnabled = !isLoading
        tvSendResetText.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressReset.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(
            ContextCompat.getColor(this, if (isError) R.color.ds_error else R.color.ds_success)
        )
        snackbar.show()
    }
}
