package com.ta.sindesa

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ta.sindesa.models.RiwayatData
import android.content.res.ColorStateList
import android.graphics.Color

class RiwayatAdapter(private var list: List<RiwayatData>) : RecyclerView.Adapter<RiwayatAdapter.ViewHolder>() {

    private var filteredList: List<RiwayatData> = list

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJenisSurat: TextView = view.findViewById(R.id.tvJenisSurat)
        val tvTanggal: TextView = view.findViewById(R.id.tvTanggal)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvKeterangan: TextView = view.findViewById(R.id.tvKeterangan)
        val btnAksi: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnAksi)
    }

    private var onDetailClickListener: ((RiwayatData) -> Unit)? = null

    fun setOnDetailClickListener(listener: (RiwayatData) -> Unit) {
        onDetailClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_riwayat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        holder.tvJenisSurat.text = item.jenisSurat
        holder.tvTanggal.text = item.tanggal
        holder.tvStatus.text = item.status
        
        if (!item.keterangan.isNullOrEmpty()) {
            holder.tvKeterangan.visibility = View.VISIBLE
            holder.tvKeterangan.text = item.keterangan
        } else {
            holder.tvKeterangan.visibility = View.GONE
        }

        // Background Tint based on status
        val statusRaw = item.statusRaw ?: item.status.lowercase()
        when {
            statusRaw.contains("selesai") -> holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1a5e35"))
            statusRaw.contains("ditolak") -> holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
            statusRaw.contains("kades") -> holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))
            else -> holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1D4ED8"))
        }

        holder.btnAksi.setOnClickListener {
            onDetailClickListener?.invoke(item)
        }
    }

    override fun getItemCount(): Int = filteredList.size

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            list
        } else {
            list.filter {
                it.jenisSurat.contains(query, ignoreCase = true) || 
                it.status.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun updateData(newList: List<RiwayatData>) {
        list = newList
        filteredList = newList
        notifyDataSetChanged()
    }

    fun sort(which: Int) {
        filteredList = when (which) {
            0 -> list.sortedByDescending { it.id } // Terbaru
            1 -> list.sortedBy { it.id } // Terlama
            2 -> list.filter { (it.statusRaw ?: it.status.lowercase()).contains("selesai") }
            3 -> list.filter { 
                val raw = it.statusRaw ?: it.status.lowercase()
                raw.contains("menunggu") || raw.contains("kades")
            }
            4 -> list.filter { (it.statusRaw ?: it.status.lowercase()).contains("ditolak") }
            else -> list
        }
        notifyDataSetChanged()
    }
}
