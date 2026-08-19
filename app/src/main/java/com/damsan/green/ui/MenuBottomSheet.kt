package com.damsan.green.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.damsan.green.R
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.ui.auth.LoginActivity
import com.damsan.green.ui.intro.SchoolInfoActivity
import com.damsan.green.ui.leaderboard.LeaderboardActivity
import com.damsan.green.ui.profile.ProfileActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MenuBottomSheet : BottomSheetDialogFragment() {

    private val repo = FirebaseRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_menu, container, false)

        view.findViewById<LinearLayout>(R.id.menuProfile).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.menuLeaderboard).setOnClickListener {
            startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
            dismiss()
        }

        view.findViewById<LinearLayout>(R.id.menuSchoolInfo).setOnClickListener {
            startActivity(Intent(requireContext(), SchoolInfoActivity::class.java))
            dismiss()
        }

        view.findViewById<View>(R.id.menuLogout).setOnClickListener {
            dismiss()
            (activity as? AppCompatActivity)?.showDamSanConfirmDialog(
                title = "Đăng xuất",
                message = "Bạn chắc chắn muốn đăng xuất khỏi Dam San Green?",
                iconRes = R.drawable.ic_logout,
                positiveText = "Đăng xuất",
                negativeText = "Huỷ",
                danger = true
            ) {
                repo.logout()
                startActivity(
                    Intent(requireContext(), LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                requireActivity().finish()
            }
        }

        return view
    }
}
