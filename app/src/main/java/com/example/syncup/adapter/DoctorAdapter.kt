package com.example.syncup.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.syncup.R
import com.example.syncup.model.Doctor

class DoctorAdapter(
    private val doctorList: List<Doctor>,
    private val patientId: String,
    private val onDoctorClick: (String, String, String, String, String, String) -> Unit  // Click listener for navigating
) : RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doctor_home, parent, false)
        return DoctorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        val doctor = doctorList[position]
        holder.nameTextView.text = doctor.name

        Glide.with(holder.imageView.context)
            .load(doctor.imageUrl)
            .placeholder(R.drawable.doctor)
            .transform(CircleCrop())
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onDoctorClick(
                doctor.name,
                doctor.phoneNumber,
                doctor.doctorUid,
                patientId,
                doctor.patientName,
                doctor.imageUrl
            )
            Log.d(
                "DoctorAdapter",
                "Doctor clicked: ${doctor.name}, doctorUid: ${doctor.doctorUid}"
            )
        }


    }

    override fun getItemCount(): Int {
        return doctorList.size
    }

    class DoctorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.doctor_name)
        val imageView: ImageView = itemView.findViewById(R.id.doctor_image)
    }
}

