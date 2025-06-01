package com.example.syncup.home

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.databinding.ItemPatientBinding
import com.example.syncup.search.PatientData

class PatientAdapter(private val patients: MutableList<PatientData>) :
    RecyclerView.Adapter<PatientAdapter.PatientViewHolder>()  {

    inner class PatientViewHolder(val binding: ItemPatientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(patient: PatientData) {
            binding.patientName.text = patient.name
            binding.ageGender.text = "Age : ${patient.age}  ${patient.gender}"
            binding.heartRate.text = "Heart rate ${patient.heartRate} Bpm"
            if (!patient.photoUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(patient.photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.account_circle) // ganti dengan icon placeholder jika kamu punya
                    .into(binding.profileImage)
            } else {
                binding.profileImage.setImageResource(R.drawable.account_circle) // fallback
            }
            val bloodPressure = if (patient.systolicBP != "None" && patient.diastolicBP != "None") {
                "${patient.systolicBP}/${patient.diastolicBP}"
            } else {
                "None"
            }
            binding.bloodPressure.text = "Blood Pressure $bloodPressure"

            val heartRateInt = patient.heartRate.toIntOrNull() ?: 0
            val status = when {
                heartRateInt >= 100 -> "Danger"
                heartRateInt in 90..99 -> "Warning"
                else -> "Health"
            }
            binding.statusIndicator.text = status

            val drawable = binding.statusIndicator.background.mutate()
            val wrappedDrawable = DrawableCompat.wrap(drawable)

            val color = when (status.lowercase()) {
                "danger" -> ContextCompat.getColor(binding.root.context, R.color.red)
                "warning" -> ContextCompat.getColor(binding.root.context, R.color.dark_overlay)
                "health" -> ContextCompat.getColor(binding.root.context, R.color.green)
                else -> ContextCompat.getColor(binding.root.context, R.color.light_gray)
            }

            DrawableCompat.setTint(wrappedDrawable, color)
            binding.statusIndicator.background = wrappedDrawable
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PatientViewHolder(binding)
    }

    fun updateList(newPatients: List<PatientData>) {
        patients.clear()
        patients.addAll(newPatients)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(patients[position])
    }

    override fun getItemCount(): Int = patients.size
}
