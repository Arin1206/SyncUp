package com.example.syncup.adapter

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.chat.RoomChatDoctorFragment
import com.example.syncup.history.HistoryPatientFragment
import com.example.syncup.model.WeekHealthItemDoctor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WeekHealthDoctorAdapter(private var items: List<WeekHealthItemDoctor>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WeekHealthItemDoctor.WeekHeader -> VIEW_TYPE_HEADER
            is WeekHealthItemDoctor.DataItem -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_week_header, parent, false)
                WeekHeaderViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_patient, parent, false)
                DataViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WeekHealthItemDoctor.WeekHeader -> (holder as WeekHeaderViewHolder).bind(item)
            is WeekHealthItemDoctor.DataItem -> (holder as DataViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<WeekHealthItemDoctor>) {
        items = newItems
        notifyDataSetChanged()
    }

    class WeekHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val weekTitleTextView: TextView = view.findViewById(R.id.tv_week_header)

        fun bind(item: WeekHealthItemDoctor.WeekHeader) {
            weekTitleTextView.text = item.week
        }
    }

    class DataViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val patientName: TextView = view.findViewById(R.id.patient_name)
        private val ageGender: TextView = view.findViewById(R.id.age_gender)
        private val heartRateTextView: TextView = view.findViewById(R.id.heart_rate)
        private val bloodPressureTextView: TextView = view.findViewById(R.id.blood_pressure)
        private val statusIndicator: TextView = view.findViewById(R.id.status_indicator)
        private val profileImage: ImageView = view.findViewById(R.id.profile_image)
        private val addButton: TextView = view.findViewById(R.id.request_button)
        private val chatIcon: ImageView = view.findViewById(R.id.chat_icon)

        private val handler = Handler(Looper.getMainLooper())
        private val updateTimeRunnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 1000)
            }
        }

        fun bind(item: WeekHealthItemDoctor.DataItem) {
            val patient = item.data
            patientName.text = patient.name
            ageGender.text = "Age: ${patient.age}  ${patient.gender}"
            heartRateTextView.text = "Heart rate ${patient.heartRate} Bpm"

            val systolic = patient.systolicBP
            val diastolic = patient.diastolicBP
            bloodPressureTextView.text = if (systolic != "None" && diastolic != "None") {
                "Blood Pressure $systolic/$diastolic"
            } else {
                "Blood Pressure None"
            }

            if (!patient.photoUrl.isNullOrEmpty()) {
                Glide.with(profileImage.context)
                    .load(patient.photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.account_circle)
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.account_circle)
            }

            val heartRate = patient.heartRate.toIntOrNull()
            val age = patient.age.toIntOrNull()
            val (colorRes, statusText) = if (heartRate != null && age != null) {
                val max = 220 - age
                val min = (max * 0.8).toInt()
                when {
                    heartRate >= max -> R.color.red to "Danger"
                    heartRate < min -> R.color.green to "Healthy"
                    else -> R.color.yellow to "Warning"
                }
            } else {
                R.color.light_gray to "Unknown"
            }

            val context = statusIndicator.context
            statusIndicator.text = statusText
            statusIndicator.setTextColor(ContextCompat.getColor(context, android.R.color.black))

            val background = ContextCompat.getDrawable(context, R.drawable.rounded_status_bg)?.mutate()
            val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
            wrappedDrawable?.let {
                DrawableCompat.setTint(it, ContextCompat.getColor(context, colorRes))
                statusIndicator.background = it
            }

            addButton.visibility = View.GONE
            handler.removeCallbacks(updateTimeRunnable)
            handler.post(updateTimeRunnable)

            // Check if the patient is assigned or not
            itemView.setOnClickListener {
                val context = it.context
                val patientAssignedRef = FirebaseFirestore.getInstance()
                    .collection("assigned_patient")
                    .whereEqualTo("patientId", patient.id)

                patientAssignedRef.get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            // Patient is not assigned, show a dialog to add
                            val dialog = AlertDialog.Builder(context)
                                .setTitle("Konfirmasi Penambahan")
                                .setMessage("Apakah Anda ingin menambahkan ${patient.name} sebagai pasien Anda?")
                                .setPositiveButton("Ya") { _, _ ->
                                    getActualDoctorUID { doctorUid, doctorName ->
                                        if (doctorUid != null) {
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
                                                    // Update button appearance
                                                    val bgDrawable =
                                                        ContextCompat.getDrawable(context, R.drawable.button_background)?.mutate()
                                                    val wrappedDrawable = bgDrawable?.let { DrawableCompat.wrap(it) }

                                                    wrappedDrawable?.let {
                                                        DrawableCompat.setTint(it, ContextCompat.getColor(context, R.color.light_gray))
                                                        addButton.background = it
                                                    }
                                                    addButton.text = "Assigned"
                                                    addButton.isEnabled = false
                                                    addButton.setTextColor(ContextCompat.getColor(context, android.R.color.white))

                                                    // Show success message
                                                    Toast.makeText(context, "${patient.name} telah ditambahkan sebagai pasien Anda.", Toast.LENGTH_SHORT).show()

                                                    // Navigate to HistoryPatientFragment
                                                    navigateToHistoryPatientFragment(context, patient.id, isFromDoctorFragment = true)
                                                }
                                                .addOnFailureListener { e ->
                                                    Toast.makeText(context, "Gagal menambahkan pasien: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                        } else {
                                            Toast.makeText(context, "Doctor UID is unavailable. Please try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .setNegativeButton("Batal", null)
                                .create()

                            // Show the dialog
                            dialog.show()
                        } else {
                            // Patient already assigned, navigate to HistoryPatientFragment
                            navigateToHistoryPatientFragment(context, patient.id, isFromDoctorFragment = true)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Gagal memeriksa status penugasan pasien: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        private fun getActualDoctorUID(onResult: (String?, String?) -> Unit) {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser ?: return onResult(null, null)

            val email = currentUser.email
            val phoneNumber = currentUser.phoneNumber

            val firestore = FirebaseFirestore.getInstance()

            if (email != null) {
                firestore.collection("users_doctor_email")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener { documents ->
                        val doctorUid = documents.firstOrNull()?.getString("userId")
                        val doctorName = documents.firstOrNull()?.getString("fullName")
                        onResult(doctorUid, doctorName)
                    }
                    .addOnFailureListener {
                        onResult(null, null)
                    }
            } else if (phoneNumber != null) {
                firestore.collection("users_doctor_phonenumber")
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .get()
                    .addOnSuccessListener { documents ->
                        val doctorUid = documents.firstOrNull()?.getString("userId")
                        val doctorName = documents.firstOrNull()?.getString("fullName")
                        onResult(doctorUid, doctorName)
                    }
                    .addOnFailureListener {
                        onResult(null, null)
                    }
            } else {
                onResult(null, null)
            }
        }

        private fun navigateToHistoryPatientFragment(context: Context?, patientId: String, isFromDoctorFragment: Boolean) {
            val historyPatientFragment = HistoryPatientFragment()

            val bundle = Bundle().apply {
                putString("patientId", patientId)
                putBoolean("isFromDoctorFragment", isFromDoctorFragment)
            }

            historyPatientFragment.arguments = bundle

            val fragmentManager = (context as AppCompatActivity).supportFragmentManager
            val transaction = fragmentManager.beginTransaction()
            transaction.replace(R.id.frame, historyPatientFragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }
}

