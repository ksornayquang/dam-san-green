package com.damsan.green.data.model

/**
 * Hệ thống nhiệm vụ hàng ngày — xoay vòng theo ngày.
 * Mỗi ngày hiển thị 1 nhiệm vụ chính (dựa trên dayOfYear % tổng số nhiệm vụ).
 */
data class DailyMission(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,          // Số lượng cần hoàn thành
    val unit: String,         // Đơn vị: "báo cáo", "chai nhựa", "ảnh", etc.
    val bonusPoints: Int = 10 // Điểm thưởng mỗi lần hoàn thành
) {
    val progressText: String get() = "$unit"

    companion object {
        /**
         * Danh sách nhiệm vụ đa dạng cho học sinh nội trú.
         * Bao gồm các hoạt động thường ngày tại KTX, sân trường, nhà ăn, etc.
         */
        val ALL_MISSIONS = listOf(
            // === KÝ TÚC XÁ ===
            DailyMission(
                id = "ktx_cleanup",
                title = "Dọn rác khu Ký túc xá",
                description = "Thu gom rác thải xung quanh khu KTX",
                target = 2,
                unit = "báo cáo"
            ),
            DailyMission(
                id = "ktx_plastic",
                title = "Thu gom chai nhựa KTX",
                description = "Nhặt chai nhựa, lon nước quanh KTX",
                target = 3,
                unit = "chai nhựa"
            ),
            DailyMission(
                id = "ktx_garden",
                title = "Chăm sóc vườn rau KTX",
                description = "Tưới cây, nhổ cỏ vườn rau ký túc xá",
                target = 1,
                unit = "báo cáo"
            ),

            // === SÂN TRƯỜNG ===
            DailyMission(
                id = "yard_sweep",
                title = "Quét lá sân trường",
                description = "Quét sạch lá rụng khu vực sân chính",
                target = 2,
                unit = "khu vực"
            ),
            DailyMission(
                id = "yard_trash",
                title = "Nhặt rác sân trường",
                description = "Thu gom rác vương vãi quanh sân trường",
                target = 3,
                unit = "báo cáo"
            ),
            DailyMission(
                id = "tree_water",
                title = "Tưới cây xanh sân trường",
                description = "Tưới nước cho cây xanh, bồn hoa sân trường",
                target = 2,
                unit = "cây/bồn hoa"
            ),

            // === NHÀ ĂN ===
            DailyMission(
                id = "canteen_cleanup",
                title = "Dọn vệ sinh nhà ăn",
                description = "Thu gom rác, lau bàn sau bữa ăn",
                target = 1,
                unit = "báo cáo"
            ),
            DailyMission(
                id = "canteen_sort",
                title = "Phân loại rác nhà ăn",
                description = "Phân loại chai nhựa, hộp xốp, thức ăn thừa",
                target = 2,
                unit = "loại rác"
            ),

            // === LỚP HỌC ===
            DailyMission(
                id = "class_clean",
                title = "Vệ sinh lớp học",
                description = "Quét dọn, lau bảng, sắp xếp bàn ghế",
                target = 1,
                unit = "báo cáo"
            ),
            DailyMission(
                id = "class_recycle",
                title = "Thu gom giấy tái chế",
                description = "Gom giấy vụn, vở cũ để tái chế",
                target = 2,
                unit = "báo cáo"
            ),

            // === KHU THỂ THAO ===
            DailyMission(
                id = "sport_cleanup",
                title = "Dọn rác khu thể thao",
                description = "Nhặt chai nước, túi nilon quanh sân bóng",
                target = 2,
                unit = "báo cáo"
            ),

            // === KHU VỰC CHUNG ===
            DailyMission(
                id = "fence_clean",
                title = "Dọn rác hàng rào",
                description = "Nhặt rác dọc hàng rào, cổng trường",
                target = 2,
                unit = "báo cáo"
            ),
            DailyMission(
                id = "plant_tree",
                title = "Trồng cây xanh",
                description = "Trồng thêm cây xanh hoặc chăm sóc vườn trường",
                target = 1,
                unit = "cây"
            ),
            DailyMission(
                id = "drain_clean",
                title = "Thông cống thoát nước",
                description = "Vệ sinh rãnh thoát nước quanh trường",
                target = 1,
                unit = "báo cáo"
            )
        )

        /**
         * Lấy nhiệm vụ hôm nay — xoay vòng theo ngày trong năm.
         * Mỗi ngày sẽ có 1 nhiệm vụ khác nhau.
         */
        fun getTodayMission(): DailyMission {
            val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            return ALL_MISSIONS[dayOfYear % ALL_MISSIONS.size]
        }

        /**
         * Lấy nhiệm vụ phụ hôm nay (nhiệm vụ tiếp theo trong danh sách).
         * Để hiển thị thêm 1 nhiệm vụ bonus nếu hoàn thành nhiệm vụ chính.
         */
        fun getTodayBonusMission(): DailyMission {
            val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            return ALL_MISSIONS[(dayOfYear + 1) % ALL_MISSIONS.size]
        }

        /**
         * Lấy danh sách tất cả nhiệm vụ (cho UI xem toàn bộ).
         */
        fun getAllMissions(): List<DailyMission> = ALL_MISSIONS
    }
}
