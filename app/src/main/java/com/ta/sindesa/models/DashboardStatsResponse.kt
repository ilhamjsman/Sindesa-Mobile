package com.ta.sindesa.models

import com.google.gson.annotations.SerializedName

data class DashboardStatsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("total_pengajuan") val totalPengajuan: Int,
    @SerializedName("proses_pengajuan") val prosesPengajuan: Int,
    @SerializedName("sering_digunakan") val seringDigunakan: List<String>?
)
