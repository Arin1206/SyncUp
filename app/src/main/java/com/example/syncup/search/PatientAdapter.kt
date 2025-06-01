package com.example.syncup.search

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PatientAdapter(patientList: List<PatientData>) :
    RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    private var patientList: MutableList<PatientData> = patientList.toMutableList()
    private val assignedPatients = mutableSetOf<String>()
    private var hideAddButton: Boolean = false
    inner class PatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val patientName: TextView = itemView.findViewById(R.id.patient_name)
        val ageGender: TextView = itemView.findViewById(R.id.age_gender)
        val heartRate: TextView = itemView.findViewById(R.id.heart_rate)
        val bloodPressure: TextView = itemView.findViewById(R.id.blood_pressure)
        val profile: ImageView = itemView.findViewById(R.id.profile_image)
        val statusindicator:TextView = itemView.findViewById(R.id.status_indicator)
        val addbutton:TextView = itemView.findViewById(R.id.request_button)

    }

    fun setHideAddButton(hide: Boolean) {
        hideAddButton = hide
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient, parent, false)
        return PatientViewHolder(view)
    }

    fun setAssignedPatients(assignedList: List<String>) {
        assignedPatients.clear()
        assignedPatients.addAll(assignedList)
        notifyDataSetChanged()
    }


    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        val patient = patientList[position]
        holder.addbutton.visibility = if (hideAddButton) View.GONE else View.VISIBLE
        holder.patientName.text = patient.name
        holder.ageGender.text = "Age: ${patient.age}  ${patient.gender}"
        holder.heartRate.text = "Heart rate ${patient.heartRate} Bpm"
        if (!patient.photoUrl.isNullOrEmpty()) {
            Glide.with(holder.profile.context)
                .load(patient.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.account_circle)
                .into(holder.profile)

        } else {
            holder.profile.setImageResource(R.drawable.account_circle)
        }

        val context = holder.itemView.context

        getActualDoctorUID { doctorUid ->
            if (doctorUid == null) return@getActualDoctorUID

            FirebaseFirestore.getInstance()
                .collection("assigned_patient")
                .whereEqualTo("patientId", patient.id)
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { documents ->
                    val alreadyAssigned = !documents.isEmpty

                    if (alreadyAssigned) {
                        val bgDrawable = ContextCompat.getDrawable(context, R.drawable.button_background)?.mutate()
                        val wrappedDrawable = bgDrawable?.let { DrawableCompat.wrap(it) }

                        wrappedDrawable?.let {
                            DrawableCompat.setTint(it, ContextCompat.getColor(context, R.color.light_gray))
                            holder.addbutton.background = it
                        }
                        holder.addbutton.text = "Assigned"
                        holder.addbutton.isEnabled = false

                        holder.addbutton.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        holder.addbutton.setOnClickListener(null) // disable click listener
                    } else {
                        val bgDrawable = ContextCompat.getDrawable(context, R.drawable.button_background)?.mutate()
                        val wrappedDrawable = bgDrawable?.let { DrawableCompat.wrap(it) }

                        wrappedDrawable?.let {
                            DrawableCompat.setTint(it, ContextCompat.getColor(context, R.color.purple_dark))
                            holder.addbutton.background = it
                        }
                        holder.addbutton.text = "Add"
                        holder.addbutton.isEnabled = true
                        holder.addbutton.setTextColor(ContextCompat.getColor(context, android.R.color.white))

                        holder.addbutton.setOnClickListener {
                            val dialog = AlertDialog.Builder(context)
                                .setTitle("Konfirmasi Penambahan")
                                .setMessage("Apakah Anda ingin menambahkan ${patient.name} sebagai pasien Anda?")
                                .setPositiveButton("Ya") { _, _ ->
                                    val assignedPatient = hashMapOf(
                                        "patientId" to patient.id,
                                        "doctorUid" to doctorUid,
                                        "name" to patient.name,
                                        "age" to patient.age,
                                        "gender" to patient.gender,
                                        "heartRate" to patient.heartRate,
                                        "systolicBP" to patient.systolicBP,
                                        "diastolicBP" to patient.diastolicBP,
                                        "photoUrl" to patient.photoUrl,
                                        "email" to patient.email,
                                        "phoneNumber" to patient.phoneNumber
                                    )

                                    FirebaseFirestore.getInstance()
                                        .collection("assigned_patient")
                                        .add(assignedPatient)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "${patient.name} telah ditambahkan sebagai pasien Anda.", Toast.LENGTH_SHORT).show()
                                            notifyItemChanged(position)
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Gagal menambahkan pasien: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .setNegativeButton("Batal", null)
                                .create()
                            assignedPatients.add(patient.id)
                            notifyItemChanged(position)
                            dialog.setOnShowListener {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            }

                            dialog.show()
                        }
                    }

                }
        }
        val systolic = patient.systolicBP
        val diastolic = patient.diastolicBP
        val bloodPressureText = if (systolic != "None" && diastolic != "None") {
            "Blood Pressure $systolic/$diastolic"
        } else {
            "Blood Pressure None"
        }
        holder.bloodPressure.text = bloodPressureText

        val heartRate = patient.heartRate.toIntOrNull()
        val ageInt = patient.age.toIntOrNull()

        val (colorRes, statusText) = if (heartRate != null && ageInt != null) {
            val maxWarning = 220 - ageInt
            val minWarning = (maxWarning * 0.8).toInt()

            when {
                heartRate >= maxWarning -> R.color.red to "Danger"
                heartRate < minWarning -> R.color.green to "Healthy"
                else -> R.color.yellow to "Warning"
            }
        } else {
            R.color.light_gray to "Unknown"
        }

        // Update status view (text + background tint)
        holder.statusindicator.text = statusText
        holder.statusindicator.setTextColor(ContextCompat.getColor(context, android.R.color.black))

        // Jika ingin mewarnai background drawable status
        val background = ContextCompat.getDrawable(context, R.drawable.rounded_status_bg)?.mutate()
        val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
        wrappedDrawable?.let {
            DrawableCompat.setTint(it, ContextCompat.getColor(context, colorRes))
            holder.statusindicator.background = it
        }
    }

    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_doctor_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_doctor_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
        }
    }

    override fun getItemCount(): Int = patientList.size


    fun updateList(newList: List<PatientData>) {
        Log.d("PatientAdapter", "Update List called with ${newList.size} items")
        patientList.clear()
        patientList.addAll(newList)
        notifyDataSetChanged()
    }


}