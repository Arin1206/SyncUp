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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is DataViewHolder) {
            holder.stopUpdatingTime()
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
        private val chatIcon: ImageView =
            view.findViewById(R.id.chat_icon)

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
                        putString("patientName", patient.name)
                        putString(
                            "doctor_phone_number",
                            patient.phoneNumber
                        )
                        putString("receiverUid", patient.id)
                        putString("doctorUid", doctorUid)
                        putString("profileImage", patient.photoUrl)
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

        fun stopUpdatingTime() {
            handler.removeCallbacks(updateTimeRunnable)
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
                            ?.getString("fullName")
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
                            ?.getString("fullName")
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
