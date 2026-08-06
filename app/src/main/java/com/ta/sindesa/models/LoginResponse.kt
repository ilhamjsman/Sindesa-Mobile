package com.ta.sindesa.models

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: UserData?,
    @SerializedName("foto_profil") val fotoProfilUpdate: String?
)

data class UserData(
    @SerializedName("user") val user: User,
    @SerializedName("token") val token: String?
)

data class User(
    @SerializedName("id") val id: Int,
    @SerializedName("nama") val nama: String,
    @SerializedName("nik") val nik: String,
    @SerializedName("email") val email: String?,
    @SerializedName("no_kk") val noKk: String?,
    @SerializedName("agama") val agama: String?,
    @SerializedName("jenis_kelamin") val jenisKelamin: String?,
    @SerializedName("tempat_lahir") val tempatLahir: String?,
    @SerializedName("tanggal_lahir") val tanggalLahir: String?,
    @SerializedName("status_perkawinan") val statusPerkawinan: String?,
    @SerializedName("pekerjaan") val pekerjaan: String?,
    @SerializedName("kewarganegaraan") val kewarganegaraan: String?,
    @SerializedName("alamat_lengkap") val alamatLengkap: String?,
    @SerializedName("rt_rw") val rtRw: String?,
    @SerializedName("provinsi") val provinsi: String?,
    @SerializedName("kota") val kota: String?,
    @SerializedName("kecamatan") val kecamatan: String?,
    @SerializedName("kelurahan_desa") val kelurahanDesa: String?,
    @SerializedName("provinsi_code") val provinsiCode: String?,
    @SerializedName("kota_code") val kotaCode: String?,
    @SerializedName("kecamatan_code") val kecamatanCode: String?,
    @SerializedName("kelurahan_desa_code") val kelurahanDesaCode: String?,
    @SerializedName("no_hp") val noHp: String?,
    @SerializedName("foto_profil") val fotoProfil: String?,
    @SerializedName("status") val status: String?
)