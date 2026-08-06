package com.ta.sindesa.models

import com.google.gson.annotations.SerializedName

data class DetailPengajuanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: DetailPengajuanData?
)

data class DetailPengajuanData(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("jenis_surat") val jenisSurat: String,
    @SerializedName("status") val status: String,
    @SerializedName("data_tambahan") val dataTambahan: Map<String, Any>?
)
