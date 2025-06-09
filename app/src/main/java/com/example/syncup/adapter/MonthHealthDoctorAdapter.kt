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
import com.example.syncup.model.MonthHealthItemDoctor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MonthHealthDoctorAdapter(private var monthData: List<MonthHealthItemDoctor>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (monthData[position] is MonthHealthItemDoctor.MonthHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_month_header, parent, false)
                MonthHeaderViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_patient, parent, false)
                MonthViewHolder(view)
            }
        }
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = monthData[position]
        when (holder) {
            is MonthHeaderViewHolder -> holder.bind(data as MonthHealthItemDoctor.MonthHeader)
            is MonthViewHolder -> holder.bind(data as MonthHealthItemDoctor.DataItem)
        }
    }

    override fun getItemCount(): Int {
        return monthData.size
    }

    fun updateData(newData: List<MonthHealthItemDoctor>) {
        monthData = newData
        notifyDataSetChanged()
    }

    class MonthHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val monthHeader: TextView = view.findViewById(R.id.tv_month_header)
        fun bind(item: MonthHealthItemDoctor.MonthHeader) {
            monthHeader.text = item.month
        }
    }

    class MonthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val patientName: TextView = view.findViewById(R.id.patient_name)
        private val ageGender: TextView = view.findViewById(R.id.age_gender)
        private val heartRateTextView: TextView = view.findViewById(R.id.heart_rate)
        private val bloodPressureTextView: TextView = view.findViewById(R.id.blood_pressure)
        private val statusIndicator: TextView = view.findViewById(R.id.status_indicator)
        private val profileImage: ImageView = view.findViewById(R.id.profile_image)
        private val addButton: TextView = view.findViewById(R.id.request_button)
        private val chatIcon: ImageView =
            view.findViewById(R.id.chat_icon)

        private val handler = Handler(Looper.getMainLooper())
        private val updateTimeRunnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 1000)
            }
        }

        fun bind(item: MonthHealthItemDoctor.DataItem) {
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

            getActualDoctorUID { doctorUid, doctorName ->
                if (doctorUid == null || doctorName == null) {
                    return@getActualDoctorUID
                }


                chatIcon.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("doctor_name", doctorName)
                        putString(
                            "doctor_phone_number",
                            patient.phoneNumber
                        )
                        putString("receiverUid", patient.id)
                        putString("doctorUid", doctorUid)
                        putString("profileImage", patient.photoUrl)
                        putString("patientName", patient.name)
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
    }
}
