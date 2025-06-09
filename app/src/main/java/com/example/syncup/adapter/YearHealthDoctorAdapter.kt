package com.example.syncup.adapter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.chat.RoomChatDoctorFragment
import com.example.syncup.model.YearHealthItemDoctor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class YearHealthDoctorAdapter(private var yearData: List<YearHealthItemDoctor>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (yearData[position] is YearHealthItemDoctor.YearHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_year_header, parent, false)
                YearHeaderViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_patient, parent, false)
                YearViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = yearData[position]
        when (holder) {
            is YearHeaderViewHolder -> holder.bind(data as YearHealthItemDoctor.YearHeader)
            is YearViewHolder -> holder.bind(data as YearHealthItemDoctor.YearData)
        }
    }

    override fun getItemCount(): Int {
        return yearData.size
    }

    fun updateData(newData: List<YearHealthItemDoctor>) {
        yearData = newData
        notifyDataSetChanged()
    }

    class YearHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val yearHeader: TextView = view.findViewById(R.id.tv_year_header)
        fun bind(item: YearHealthItemDoctor.YearHeader) {
            yearHeader.text = item.year
        }
    }

    class YearViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val patientName: TextView = view.findViewById(R.id.patient_name)
        private val ageGender: TextView = view.findViewById(R.id.age_gender)
        private val heartRateTextView: TextView = view.findViewById(R.id.heart_rate)
        private val bloodPressureTextView: TextView = view.findViewById(R.id.blood_pressure)
        private val statusIndicator: TextView = view.findViewById(R.id.status_indicator)
        private val profileImage: ImageView = view.findViewById(R.id.profile_image)
        private val addButton: TextView = view.findViewById(R.id.request_button)
        private val chatIcon: ImageView =
            view.findViewById(R.id.chat_icon)  // Add chat icon reference

        private val handler = Handler(Looper.getMainLooper())
        private val updateTimeRunnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 1000)
            }
        }

        fun bind(item: YearHealthItemDoctor.YearData) {
            val patient = item.patientData
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

            val background =
                ContextCompat.getDrawable(context, R.drawable.rounded_status_bg)?.mutate()
            val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
            wrappedDrawable?.let {
                DrawableCompat.setTint(it, ContextCompat.getColor(context, colorRes))
                statusIndicator.background = it
            }

            addButton.visibility = View.GONE
            handler.removeCallbacks(updateTimeRunnable)
            handler.post(updateTimeRunnable)

            // Fetch doctor data (doctorUid and doctorName)
            getActualDoctorUID { doctorUid, doctorName ->
                if (doctorUid == null || doctorName == null) {
                    return@getActualDoctorUID
                }

                // Set up a click listener to navigate to RoomChatDoctorFragment when chatIcon is clicked
                chatIcon.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("patientName", patient.name)  // Pass doctorName
                        putString(
                            "doctor_phone_number",
                            patient.phoneNumber
                        )  // Assuming phoneNumber is available
                        putString("receiverUid", patient.id)  // Pass patientId as receiverUid
                        putString("doctorUid", doctorUid)  // Pass doctorUid
                        putString("profileImage", patient.photoUrl)  // Pass profile image URL
                    }

                    // Navigate to RoomChatDoctorFragment
                    val roomChatFragment = RoomChatDoctorFragment().apply {
                        arguments = bundle
                    }

                    // Use the context (activity) to navigate to the RoomChatDoctorFragment
                    (context as? AppCompatActivity)?.supportFragmentManager?.beginTransaction()
                        ?.replace(R.id.frame, roomChatFragment)
                        ?.addToBackStack(null)
                        ?.commit()
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
                        val doctorName = documents.firstOrNull()
                            ?.getString("fullName") // Assuming doctor name is stored as 'fullName'
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
                        val doctorName = documents.firstOrNull()
                            ?.getString("fullName") // Assuming doctor name is stored as 'fullName'
                        onResult(doctorUid, doctorName)
                    }
                    .addOnFailureListener {
                        onResult(null, null)
                    }
            } else {
                onResult(null, null)
            }
        }
    }


}
