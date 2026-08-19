package com.damsan.green.data.model

data class WasteAiReview(
    val isTrash: Boolean = false,
    val trashName: String = "",
    val category: String = "",
    val reviewStatus: String = "needs_review",
    val wasteType: String = "unknown",
    val detectedItems: Int = 0,
    val estimatedKg: Double = 0.0,
    val confidence: Int = 0,
    val reason: String = "",
    val warnings: String = "",
    val autoApproved: Boolean = false
) {
    val isUsable: Boolean
        get() = reviewStatus != "failed" && confidence > 0
}
