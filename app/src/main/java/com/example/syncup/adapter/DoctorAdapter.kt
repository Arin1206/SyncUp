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

        // Bind doctor name and profile image
        holder.nameTextView.text = doctor.name

        // Load profile image with Glide
        Glide.with(holder.imageView.context)
            .load(doctor.imageUrl)  // This is the URL of the doctor's profile image
            .placeholder(R.drawable.doctor) // Placeholder image while loading
            .transform(CircleCrop())  // Apply circular crop to the image
            .into(holder.imageView)

        // Set item click listener
        holder.itemView.setOnClickListener {
            onDoctorClick(
                doctor.name,
                doctor.phoneNumber,  // Pass the doctor's phone number
                doctor.doctorUid,    // Ensure the correct doctor UID is passed
                patientId,           // Patient ID
                doctor.patientName,  // Assuming this is available in the Doctor object
                doctor.imageUrl      // Profile image URL
            )
            Log.d("DoctorAdapter", "Doctor clicked: ${doctor.name}, doctorUid: ${doctor.doctorUid}")  // Log UID for debugging
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

