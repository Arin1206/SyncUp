package com.example.syncup.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R

class DoctorAdapter(private var doctorList: List<Doctor>) :
    RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

    class DoctorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val doctorImage: ImageView = view.findViewById(R.id.doctor_image)
        val doctorName: TextView = view.findViewById(R.id.doctor_name)
        val doctorDescription: TextView = view.findViewById(R.id.doctor_description)
        val requestButton: TextView = view.findViewById(R.id.request_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doctor, parent, false)
        return DoctorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        val doctor = doctorList[position]
        holder.doctorName.text = doctor.name
        holder.doctorDescription.text = doctor.description
        holder.doctorImage.setImageResource(doctor.imageRes)
    }

    fun updateList(newList: List<Doctor>) {
        doctorList = newList  // 🔹 Sekarang `doctorList` sudah benar!
        notifyDataSetChanged() // 🔹 Memberi tahu adapter bahwa data berubah
    }

    override fun getItemCount() = doctorList.size
}
