package com.example.syncup.history.fragment

import HealthData
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.HealthAdapter
import com.example.syncup.model.HealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class DateFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: HealthAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_date, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = HealthAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        fetchHealthData()

        return view
    }

    private fun fetchHealthData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        firestore.collection("patient_heart_rate")
            .whereEqualTo("userId", userId) // ❌ Hapus orderBy untuk menghindari indeks Firestore
            .get()
            .addOnSuccessListener { documents ->
                val groupedItems = mutableListOf<HealthItem>()
                val groupedMap = LinkedHashMap<String, MutableList<HealthItem.DataItem>>()

                val dataList = documents.mapNotNull { doc ->
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                    val timestamp = doc.getString("timestamp") ?: return@mapNotNull null

                    val formattedDate = extractDate(timestamp)  // 🔹 Ambil Tanggal
                    val formattedTime = extractTime(timestamp)  // 🔹 Ambil Jam:Menit

                    HealthData(
                        heartRate = heartRate,
                        bloodPressure = "$systolicBP/$diastolicBP",
                        batteryLevel = batteryLevel,
                        timestamp = formattedTime,
                        fullTimestamp = timestamp // Simpan timestamp asli untuk pengurutan
                    )
                }

                // 🔹 Urutkan data secara lokal berdasarkan timestamp terbaru
                val sortedData = dataList.sortedByDescending { it.fullTimestamp }

                // 🔹 Kelompokkan data berdasarkan tanggal
                for (data in sortedData) {
                    val formattedDate = extractDate(data.fullTimestamp)
                    val dataItem = HealthItem.DataItem(data)

                    if (!groupedMap.containsKey(formattedDate)) {
                        groupedMap[formattedDate] = mutableListOf()
                    }
                    groupedMap[formattedDate]?.add(dataItem)
                }

                // 🔹 Masukkan ke dalam list RecyclerView dengan Header Tanggal
                for ((date, items) in groupedMap) {
                    groupedItems.add(HealthItem.DateHeader(date)) // Tambahkan header tanggal
                    groupedItems.addAll(items) // Tambahkan data di bawahnya
                }

                // 🔹 Update adapter dengan data yang telah dikelompokkan
                healthDataAdapter.updateData(groupedItems)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Failed to fetch data: ${exception.message}")
            }
    }



    // **Fungsi untuk mengambil hanya tanggal (misalnya "24 Jan 2025")**
    private fun extractDate(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())  // 🔹 Format Tanggal
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing timestamp: ${e.message}")
            timestamp  // Jika gagal, gunakan timestamp asli
        }
    }

    // **Fungsi untuk mengambil jam & menit saja dari timestamp**
    private fun extractTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())  // 🔹 Format Jam:Menit
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("TimeFormatError", "Error parsing timestamp: ${e.message}")
            timestamp
        }
    }
}
