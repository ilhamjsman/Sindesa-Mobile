package com.ta.sindesa.models

import com.google.gson.annotations.SerializedName

data class RiwayatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<RiwayatData>?
)

data class RiwayatData(
    @SerializedName("id") val id: Int,
    @SerializedName("jenis_surat") val jenisSurat: String,
    @SerializedName("jenis_surat_raw") val jenisSuratRaw: String?,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("status") val status: String,
    @SerializedName("status_raw") val statusRaw: String?,
    @SerializedName("nomor_surat") val nomorSurat: String?,
    @SerializedName("metode_ttd") val metodeTtd: String?,
    @SerializedName("metode_ttd_label") val metodeTtdLabel: String?,
    @SerializedName("keterangan") val keterangan: String?,
    @SerializedName("pesan_penolakan") val pesanPenolakan: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("file_surat") val fileSurat: String?,
    @SerializedName("updated_at") val updatedAt: String?
)