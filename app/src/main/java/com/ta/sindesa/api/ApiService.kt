package com.ta.sindesa.api

import com.ta.sindesa.models.LoginResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    @FormUrlEncoded
    @POST("login")
    fun login(
        @Field("email_nik") emailNik: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @FormUrlEncoded
    @POST("login_warga.php")
    fun loginWarga(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @Multipart
    @POST("register_warga.php")
    fun registerWarga(
        @Part("nama") nama: RequestBody,
        @Part("nik") nik: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("kewarganegaraan") kewarganegaraan: RequestBody,
        @Part("alamat_lengkap") alamatLengkap: RequestBody,
        @Part("rt_rw") rtRw: RequestBody,
        @Part("provinsi") provinsi: RequestBody,
        @Part("kota") kota: RequestBody,
        @Part("kecamatan") kecamatan: RequestBody,
        @Part("kelurahan_desa") kelurahanDesa: RequestBody,
        @Part("no_hp") noHp: RequestBody,
        @Part("email") email: RequestBody,
        @Part("password") password: RequestBody,
        @Part foto_ktp: MultipartBody.Part?,
        @Part("recaptcha_token") recaptchaToken: RequestBody? = null
    ): Call<LoginResponse>

    @FormUrlEncoded
    @POST("register")
    fun register(
        @Field("nama") nama: String,
        @Field("nik") nik: String,
        @Field("no_kk") noKk: String,
        @Field("pekerjaan") pekerjaan: String,
        @Field("no_hp") noHp: String,
        @Field("password") password: String
    ): Call<LoginResponse>

    @Multipart
    @POST("submit_ktp.php")
    fun submitKtp(
        @Part("nik") nik: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_ktp_lama: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: tujuan → tujuan_pengajuan, no_kk_lama → kk_lama, nama → nama_lengkap, nama_kpl_keluarga → nama_kepala_keluarga
    @Multipart
    @POST("submit_kk.php")
    fun submitKk(
        @Part("tujuan_pengajuan") tujuanPengajuan: RequestBody,
        @Part("nik") nik: RequestBody,
        @Part("kk_lama") kkLama: RequestBody,
        @Part("nama_lengkap") namaLengkap: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("nama_kepala_keluarga") namaKepalaKeluarga: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("rt") rt: RequestBody,
        @Part("rw") rw: RequestBody,
        @Part berkas_kk_lama: MultipartBody.Part?,
        @Part berkas_nikah: MultipartBody.Part?,
        @Part berkas_lain: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: Added kewarganegaraan, pekerjaan, alamat_dusun, rt, rw; alamat → alamat_dusun
    @Multipart
    @POST("submit_skck.php")
    fun submitSkck(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("kewarganegaraan") kewarganegaraan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat_dusun") alamatDusun: RequestBody,
        @Part("rt") rt: RequestBody,
        @Part("rw") rw: RequestBody,
        @Part("keperluan") keperluan: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: no_kk_kk → no_kk, nik_kk → nik_kepala_keluarga, nama_kk → nama_kepala_keluarga
    @Multipart
    @POST("submit_sktm.php")
    fun submitSktm(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("nik_kepala_keluarga") nikKepalaKeluarga: RequestBody,
        @Part("nama_kepala_keluarga") namaKepalaKeluarga: RequestBody,
        @Part("tempat_lahir_kk") tempatLahirKk: RequestBody,
        @Part("tanggal_lahir_kk") tanggalLahirKk: RequestBody,
        @Part("jenis_kelamin_kk") jenisKelaminKk: RequestBody,
        @Part("agama_kk") agamaKk: RequestBody,
        @Part("pekerjaan_kk") pekerjaanKk: RequestBody,
        @Part("alamat_kk") alamatKk: RequestBody,
        @Part("keperluan") keperluan: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_dusun: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: Added no_kk, pekerjaan; alamat → alamat_dusun
    @Multipart
    @POST("submit_usaha.php")
    fun submitUsaha(
        @Part("nik") nik: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat_dusun") alamatDusun: RequestBody,
        @Part("rt") rt: RequestBody,
        @Part("rw") rw: RequestBody,
        @Part("jenis_usaha") jenisUsaha: RequestBody,
        @Part("usaha_sampingan") usahaSampingan: RequestBody,
        @Part("alamat_usaha") alamatUsaha: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_usaha: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    @Multipart
    @POST("submit_pindah.php")
    fun submitPindah(
        @Part("nik") nik: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("pendidikan") pendidikan: RequestBody,
        @Part("dusun_asal") dusunAsal: RequestBody,
        @Part("rt_asal") rtAsal: RequestBody,
        @Part("rw_asal") rwAsal: RequestBody,
        @Part("alamat_tujuan") alamatTujuan: RequestBody,
        @Part("rt_tujuan") rt_tujuan: RequestBody,
        @Part("rw_tujuan") rw_tujuan: RequestBody,
        @Part("desa_tujuan") desaTujuan: RequestBody,
        @Part("kec_tujuan") kecTujuan: RequestBody,
        @Part("kab_tujuan") kabTujuan: RequestBody,
        @Part("prov_tujuan") provTujuan: RequestBody,
        @Part("pos_tujuan") posTujuan: RequestBody,
        @Part("alasan_pindah") alasanPindah: RequestBody,
        @Part("tanggal_pindah") tanggalPindah: RequestBody,
        @Part("keluarga_ikut") keluargaIkut: RequestBody?,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_lain: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: no_kk_almarhum → kk_almarhum, tanggal_wafat → tanggal_kematian, umur → umur_kematian
    // FIXED: Added nik_pelapor so backend can lookup user by pelapor (not almarhum)
    @Multipart
    @POST("submit_kematian.php")
    fun submitKematian(
        @Part("nik_almarhum") nikAlmarhum: RequestBody,
        @Part("kk_almarhum") kkAlmarhum: RequestBody,
        @Part("nama_almarhum") namaAlmarhum: RequestBody,
        @Part("tempat_lahir_almarhum") tempatLahirAlmarhum: RequestBody,
        @Part("tanggal_lahir_almarhum") tanggalLahirAlmarhum: RequestBody,
        @Part("jenis_kelamin_almarhum") jenisKelaminAlmarhum: RequestBody,
        @Part("agama_almarhum") agamaAlmarhum: RequestBody,
        @Part("kewarganegaraan_almarhum") kewarganegaraanAlmarhum: RequestBody,
        @Part("status_perkawinan_almarhum") statusPerkawinanAlmarhum: RequestBody,
        @Part("pekerjaan_almarhum") pekerjaanAlmarhum: RequestBody,
        @Part("alamat_almarhum") alamatAlmarhum: RequestBody,
        @Part("tanggal_kematian") tanggalKematian: RequestBody,
        @Part("umur_kematian") umurKematian: RequestBody,
        @Part("tempat_kematian") tempatKematian: RequestBody,
        @Part("sebab_kematian") sebabKematian: RequestBody,
        @Part("nama_pelapor") namaPelapor: RequestBody,
        @Part("hubungan_pelapor") hubunganPelapor: RequestBody,
        @Part("nik_pelapor") nikPelapor: RequestBody,
        @Part berkas_ktp_almarhum: MultipartBody.Part?,
        @Part berkas_kk_almarhum: MultipartBody.Part?,
        @Part berkas_ktp_pelapor: MultipartBody.Part?,
        @Part berkas_rs: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: Added anak_ke
    @Multipart
    @POST("submit_akta_lahir.php")
    fun submitAktaLahir(
        @Part("nama_anak") namaAnak: RequestBody,
        @Part("anak_ke") anakKe: RequestBody,
        @Part("tempat_lahir_anak") tempatLahirAnak: RequestBody,
        @Part("tanggal_lahir_anak") tanggalLahirAnak: RequestBody,
        @Part("jenis_kelamin_anak") jenisKelaminAnak: RequestBody,
        @Part("agama_anak") agamaAnak: RequestBody,
        @Part("kewarganegaraan_anak") kewarganegaraanAnak: RequestBody,
        @Part("alamat_anak") alamatAnak: RequestBody,
        @Part("nama_ayah") namaAyah: RequestBody,
        @Part("nik_ayah") nikAyah: RequestBody,
        @Part("nama_ibu") namaIbu: RequestBody,
        @Part("nik_ibu") nikIbu: RequestBody,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_saksi: MultipartBody.Part?,
        @Part("edit_id") editId: RequestBody? = null,
        @Part("nik_pemohon") nikPemohon: RequestBody? = null,
        @Part("nama_pemohon") namaPemohon: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: barang_hilang → rincian_hilang
    @Multipart
    @POST("submit_kehilangan.php")
    fun submitKehilangan(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("rincian_hilang") rincianHilang: RequestBody,
        @Part("waktu_hilang") waktuHilang: RequestBody,
        @Part("lokasi_hilang") lokasiHilang: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_bukti: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: no_dokumen2 → nomor_dok2, perbedaan → data_berbeda, acuan → acuan_kebenaran; added alamat_dok2
    @Multipart
    @POST("submit_beda_nama.php")
    fun submitBedaNama(
        @Part("nik_dok1") nikDok1: RequestBody,
        @Part("nama_dok1") namaDok1: RequestBody,
        @Part("tempat_lahir_dok1") tempatLahirDok1: RequestBody,
        @Part("tanggal_lahir_dok1") tanggalLahirDok1: RequestBody,
        @Part("jenis_kelamin_dok1") jenisKelaminDok1: RequestBody,
        @Part("alamat_dok1") alamatDok1: RequestBody,
        @Part("nama_dokumen2") namaDokumen2: RequestBody,
        @Part("nomor_dok2") nomorDok2: RequestBody,
        @Part("nama_dok2") namaDok2: RequestBody,
        @Part("tempat_lahir_dok2") tempatLahirDok2: RequestBody,
        @Part("tanggal_lahir_dok2") tanggalLahirDok2: RequestBody,
        @Part("jenis_kelamin_dok2") jenisKelaminDok2: RequestBody,
        @Part("alamat_dok2") alamatDok2: RequestBody,
        @Part("data_berbeda") dataBerbeda: RequestBody,
        @Part("acuan_kebenaran") acuanKebenaran: RequestBody,
        @Part berkas_dok1: MultipartBody.Part?,
        @Part berkas_dok2: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    @Multipart
    @POST("submit_domisili.php")
    fun submitDomisili(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("kewarganegaraan") kewarganegaraan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_lain: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    @Multipart
    @POST("submit_janda_duda.php")
    fun submitJandaDuda(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("penyebab_status") penyebabStatus: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("nama_mantan") namaMantan: RequestBody,
        @Part("tahun_berpisah") tahunBerpisah: RequestBody,
        @Part("alamat_mantan") alamatMantan: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_bukti: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: penghasilan → jumlah_penghasilan, tanggungan → jumlah_tanggungan, nama_ditanggung → nama_tanggungan
    @Multipart
    @POST("submit_penghasilan.php")
    fun submitPenghasilan(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("jumlah_penghasilan") jumlahPenghasilan: RequestBody,
        @Part("jumlah_tanggungan") jumlahTanggungan: RequestBody,
        @Part("nama_tanggungan") namaTanggungan: RequestBody,
        @Part berkas_kk_ktp: MultipartBody.Part?,
        @Part berkas_anak: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // FIXED: Added tempat_lahir_bapak, tanggal_lahir_bapak, pekerjaan_bapak, alamat_bapak, tempat_lahir_ibu, tanggal_lahir_ibu, pekerjaan_ibu, alamat_ibu
    @Multipart
    @POST("submit_belum_menikah.php")
    fun submitBelumMenikah(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("nik_bapak") nikBapak: RequestBody,
        @Part("nama_bapak") namaBapak: RequestBody,
        @Part("tempat_lahir_bapak") tempatLahirBapak: RequestBody,
        @Part("tanggal_lahir_bapak") tanggalLahirBapak: RequestBody,
        @Part("agama_bapak") agamaBapak: RequestBody,
        @Part("pekerjaan_bapak") pekerjaanBapak: RequestBody,
        @Part("alamat_bapak") alamatBapak: RequestBody,
        @Part("nik_ibu") nikIbu: RequestBody,
        @Part("nama_ibu") namaIbu: RequestBody,
        @Part("tempat_lahir_ibu") tempatLahirIbu: RequestBody,
        @Part("tanggal_lahir_ibu") tanggalLahirIbu: RequestBody,
        @Part("agama_ibu") agamaIbu: RequestBody,
        @Part("pekerjaan_ibu") pekerjaanIbu: RequestBody,
        @Part("alamat_ibu") alamatIbu: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_kk: MultipartBody.Part?,
        @Part berkas_ortu: MultipartBody.Part?,
        @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    @Multipart
    @POST("submit_izin_keramaian.php")
    fun submitIzinKeramaian(
        @Part("nik") nik: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("alamat") alamat: RequestBody,
        @Part("jenis_acara") jenisAcara: RequestBody,
        @Part("tanggal_mulai") tanggalMulai: RequestBody,
        @Part("tanggal_selesai") tanggalSelesai: RequestBody,
        @Part("lokasi_acara") lokasiAcara: RequestBody,
        @Part berkas_ktp: MultipartBody.Part?,
        @Part berkas_pengantar: MultipartBody.Part?
    , @Part("edit_id") editId: RequestBody? = null
    ): Call<com.ta.sindesa.models.LoginResponse>

    // SECURITY: User diidentifikasi dari Bearer token, bukan NIK di query string
    @GET("get_riwayat.php")
    fun getRiwayat(): Call<com.ta.sindesa.models.RiwayatResponse>

    @GET("get_detail_pengajuan.php")
    fun getDetailPengajuan(
        @Query("id") id: Int
    ): Call<com.ta.sindesa.models.DetailPengajuanResponse>

    @FormUrlEncoded
    @POST("delete_pengajuan.php")
    fun deletePengajuan(
        @Field("id") id: Int
    ): Call<com.ta.sindesa.models.LoginResponse>

    // SECURITY: User diidentifikasi dari Bearer token, bukan NIK di query string
    @GET("get_profil.php")
    fun getProfil(): Call<com.ta.sindesa.models.LoginResponse>

    @Multipart
    @POST("update_profil.php")
    fun updateProfil(
        @Part("new_nik") newNik: RequestBody,
        @Part("no_kk") noKk: RequestBody,
        @Part("nama") nama: RequestBody,
        @Part("email") email: RequestBody,
        @Part("no_hp") noHp: RequestBody,
        @Part("tempat_lahir") tempatLahir: RequestBody,
        @Part("tanggal_lahir") tanggalLahir: RequestBody,
        @Part("jenis_kelamin") jenisKelamin: RequestBody,
        @Part("agama") agama: RequestBody,
        @Part("status_perkawinan") statusPerkawinan: RequestBody,
        @Part("pekerjaan") pekerjaan: RequestBody,
        @Part("kewarganegaraan") kewarganegaraan: RequestBody,
        @Part("alamat_lengkap") alamatLengkap: RequestBody,
        @Part("rt_rw") rtRw: RequestBody,
        @Part("provinsi") provinsi: RequestBody,
        @Part("kota") kota: RequestBody,
        @Part("kecamatan") kecamatan: RequestBody,
        @Part("kelurahan_desa") kelurahanDesa: RequestBody,
        @Part("password") password: RequestBody,
        @Part fotoProfil: MultipartBody.Part?
    ): Call<com.ta.sindesa.models.LoginResponse>


    // Region Data Endpoints
    @GET("get_provinces.php")
    fun getProvinces(): Call<List<com.ta.sindesa.models.Region>>

    @GET("get_cities.php")
    fun getCities(@Query("province_id") provinceCode: String): Call<List<com.ta.sindesa.models.Region>>

    @GET("get_districts.php")
    fun getDistricts(@Query("regency_id") cityCode: String): Call<List<com.ta.sindesa.models.Region>>

    @GET("get_villages.php")
    fun getVillages(@Query("district_id") districtCode: String): Call<List<com.ta.sindesa.models.Region>>

    // SECURITY: User diidentifikasi dari Bearer token, bukan NIK di query string
    @GET("dashboard_stats.php")
    fun getDashboardStats(): Call<com.ta.sindesa.models.DashboardStatsResponse>
}