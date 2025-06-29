package com.example.syncup.search

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.chat.RoomChatDoctorFragment
import com.example.syncup.chat.RoomChatFragment
import com.example.syncup.history.HistoryPatientFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PatientAdapter(patientList: List<PatientData>,  private val context: Context) :
    RecyclerView.Adapter<PatientAdapter.PatientViewHolder>() {

    private var patientList: MutableList<PatientData> = patientList.toMutableList()
    private val assignedPatients = mutableSetOf<String>()
    private var hideAddButton: Boolean = false
    private var doctorName: String? = null
    private var doctorUid: String? = null

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

        // Debugging: Log patient name to check if it's being fetched properly
        Log.d("PatientAdapter", "Patient Name: ${patient.name}")

        // Fallback for patient name if it's null or empty
        val patientName = patient.name.takeIf { !it.isNullOrEmpty() } ?: "Unknown"

        holder.addbutton.visibility = if (hideAddButton) View.GONE else View.VISIBLE
        holder.patientName.text = patientName  // Use the patient name, with fallback
        holder.ageGender.text = "Age: ${patient.age}  ${patient.gender}"
        holder.heartRate.text = "Heart rate ${patient.heartRate} Bpm"

        // Loading profile image using Glide
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

        // Fetch the actual doctor UID and doctor name
        getActualDoctorUID { doctorUid, doctorName ->  // Now you have both `doctorUid` and `doctorName`
            if (doctorUid == null || doctorName == null) return@getActualDoctorUID

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
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, R.color.purple_dark))
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(context, R.color.purple_dark))
                            }

                            dialog.show()
                        }
                    }
                }
        }







        // Chat Icon click listener to navigate to RoomChat
        holder.itemView.findViewById<ImageView>(R.id.chat_icon).setOnClickListener {
            getActualDoctorUID { doctorUid, doctorName ->
                if (doctorUid != null) {
                    val bundle = Bundle().apply {
                        putString("patientName", patient.name)
                        putString("doctor_phone_number", patient.phoneNumber)
                        putString("receiverUid", patient.id)
                        putString("doctorUid", doctorUid)
                        putString("profileImage", patient.photoUrl)
                        putString("doctorName", doctorName ?: "")
                    }

                    val roomChatFragment = RoomChatDoctorFragment().apply {
                        arguments = bundle
                    }

                    (context as? AppCompatActivity)?.supportFragmentManager?.beginTransaction()
                        ?.replace(R.id.frame, roomChatFragment)
                        ?.addToBackStack(null)
                        ?.commit()
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

        // If you want to color the background drawable of status
        val background = ContextCompat.getDrawable(context, R.drawable.rounded_status_bg)?.mutate()
        val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
        wrappedDrawable?.let {
            DrawableCompat.setTint(it, ContextCompat.getColor(context, colorRes))
            holder.statusindicator.background = it
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context

            // 1️⃣ Cek umur dulu
            getAgeFromPatientId(patient.id) { age ->
                if (age == null) {
                    AlertDialog.Builder(context)
                        .setTitle("Umur Tidak Valid")
                        .setMessage("Pasien ${patient.name} memiliki data umur yang tidak valid atau kosong.\nMohon periksa kembali data pasien.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // 2️⃣ Umur valid → cek apakah pasien ini sudah di-assign oleh dokter yang login
                    getActualDoctorUID { currentDoctorUid, _ ->
                        if (currentDoctorUid == null) {
                            Toast.makeText(context, "Gagal mendapatkan UID dokter", Toast.LENGTH_SHORT).show()
                            return@getActualDoctorUID
                        }

                        val firestore = FirebaseFirestore.getInstance()
                        firestore.collection("assigned_patient")
                            .whereEqualTo("patientId", patient.id)
                            .whereEqualTo("doctorUid", currentDoctorUid)  // ❗Cek hanya untuk dokter ini
                            .get()
                            .addOnSuccessListener { documents ->
                                if (!documents.isEmpty) {
                                    // ✅ Sudah di-assign ke dokter ini → langsung navigasi
                                    navigateToHistoryPatientFragment(context, patient.id, isFromDoctorFragment = true)
                                } else {
                                    // ❌ Belum di-assign ke dokter ini → tampilkan dialog konfirmasi
                                    showAssignConfirmationDialog(context, holder, patient, position)
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Gagal memeriksa status penugasan pasien: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        }

    }

    private fun showAssignConfirmationDialog(
        context: Context?,
        holder: PatientAdapter.PatientViewHolder,
        patient: PatientData,
        position: Int
    ) {
        if (context == null) return  // ⛔ Hindari crash karena context null

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
                                // Update tombol
                                val bgDrawable = ContextCompat.getDrawable(context, R.drawable.button_background)?.mutate()
                                val wrappedDrawable = bgDrawable?.let { DrawableCompat.wrap(it) }
                                wrappedDrawable?.let {
                                    DrawableCompat.setTint(it, ContextCompat.getColor(context, R.color.light_gray))
                                    holder.addbutton.background = it
                                }

                                holder.addbutton.text = "Assigned"
                                holder.addbutton.isEnabled = false
                                holder.addbutton.setTextColor(ContextCompat.getColor(context, android.R.color.white))

                                Toast.makeText(context, "${patient.name} telah ditambahkan sebagai pasien Anda.", Toast.LENGTH_SHORT).show()
                                notifyItemChanged(position)

                                // Navigasi ke History
                                navigateToHistoryPatientFragment(context, patient.id, isFromDoctorFragment = true)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Gagal menambahkan pasien: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "Doctor UID tidak tersedia.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(context, R.color.purple_dark))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(context, R.color.purple_dark))
        }

        dialog.show()
    }

    private fun getAgeFromPatientId(patientId: String, onResult: (Int?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()

        // Cek di koleksi users_patient_email
        firestore.collection("users_patient_email")
            .whereEqualTo("userId", patientId)
            .get()
            .addOnSuccessListener { emailDocs ->
                if (!emailDocs.isEmpty) {
                    val ageStr = emailDocs.firstOrNull()?.getString("age")
                    val age = ageStr?.toIntOrNull()
                    onResult(age)
                } else {
                    // Jika tidak ditemukan di email, coba cek di phone number
                    firestore.collection("users_patient_phonenumber")
                        .whereEqualTo("userId", patientId)
                        .get()
                        .addOnSuccessListener { phoneDocs ->
                            val ageStr = phoneDocs.firstOrNull()?.getString("age")
                            val age = ageStr?.toIntOrNull()
                            onResult(age)
                        }
                        .addOnFailureListener {
                            onResult(null)
                        }
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun navigateToHistoryPatientFragment(context: Context?, patientId: String, isFromDoctorFragment: Boolean) {
        val historyPatientFragment = HistoryPatientFragment()

        // Membuat Bundle dan mengirim data patientId dan isFromDoctorFragment
        val bundle = Bundle().apply {
            putString("patientId", patientId)  // Mengirimkan patientId
            putBoolean("isFromDoctorFragment", isFromDoctorFragment)  // Mengirimkan status apakah dari DoctorFragment
        }

        historyPatientFragment.arguments = bundle

        val fragmentManager = (context as AppCompatActivity).supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.frame, historyPatientFragment)  // Assuming 'frame' is your container
        transaction.addToBackStack(null)  // Optional: Add to backstack for navigation
        transaction.commit()
    }


    private fun getActualDoctorUID(onResult: (String?, String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null, null)

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_doctor_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val doctorUid = documents.firstOrNull()?.getString("userId")
                    val doctorName = documents.firstOrNull()?.getString("fullName") // Assuming doctor name is stored as 'fullName'
                    onResult(doctorUid, doctorName)
                }
                .addOnFailureListener {
                    onResult(null, null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_doctor_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val doctorUid = documents.firstOrNull()?.getString("userId")
                    val doctorName = documents.firstOrNull()?.getString("fullName") // Assuming doctor name is stored as 'fullName'
                    onResult(doctorUid, doctorName)
                }
                .addOnFailureListener {
                    onResult(null, null)
                }
        } else {
            onResult(null, null) // If neither email nor phone number is available
        }
    }

    // Helper function to format phone number
    private fun formatPhoneNumber(phoneNumber: String?): String? {
        return phoneNumber?.let {
            if (it.startsWith("+62")) {
                "0" + it.substring(3)  // Replace +62 with 0
            } else {
                it  // If it doesn't start with +62, return the number as is
            }
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