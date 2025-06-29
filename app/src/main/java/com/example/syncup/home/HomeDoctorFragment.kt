package com.example.syncup.home

import HealthData
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.adapter.PatientPagerAdapter
import com.example.syncup.databinding.FragmentHomeBinding
import com.example.syncup.databinding.FragmentHomeDoctorBinding
import com.example.syncup.profile.ProfileDoctorFragment
import com.example.syncup.profile.ProfilePatientFragment
import com.example.syncup.search.PatientData
import com.example.syncup.search.SearchDoctorFragment
import com.example.syncup.search.SearchPatientFragment
import com.example.syncup.viewmodel.SharedViewModel
import com.example.syncup.viewmodel.observeOnce
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class HomeDoctorFragment : Fragment() {

    private var _binding: FragmentHomeDoctorBinding? = null
    private val binding get() = _binding!!
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var monthChartView: com.example.syncup.chart.MonthChartViewHome? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private val firestore = FirebaseFirestore.getInstance()
//    private lateinit var searchDoctor: EditText
private val realtimeDB = FirebaseDatabase.getInstance().reference
    private lateinit var parentLayout: ConstraintLayout
    private val auth = FirebaseAuth.getInstance()
    private var documentId: String? = null

    private var currentTab: Int = 0
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val patientList = mutableListOf<PatientData>()
    private lateinit var patientSpinner: Spinner
    private val heartRateListeners = mutableMapOf<String, ValueEventListener>()

    private var patientAdapter: ArrayAdapter<String>? = null



//    private fun setupUI(view: View) {
//        // Set listener pada parent layout untuk menangkap klik di luar input
//        view.setOnTouchListener { _, event ->
//            if (event.action == MotionEvent.ACTION_DOWN) {
//                // Clear focus dari EditText
//                searchDoctor.clearFocus()
//                hideKeyboard()
//            }
//            false
//        }
//
//
//        // Tambahkan listener agar keyboard turun saat EditText kehilangan fokus
//        searchDoctor.setOnEditorActionListener { _, actionId, event ->
//            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
//                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
//
//                val searchText = searchDoctor.text.toString().trim()
//                hideKeyboard()
//
//                // Jika input kosong, tampilkan semua dokter
//                navigateToSearchFragment(searchText)
//                true
//            } else {
//                false
//            }
//        }
//
//
//    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?  {
        _binding = FragmentHomeDoctorBinding.inflate(inflater, container, false)

        val view = binding.root

//        searchDoctor = binding.searchDoctor

//        setupUI(view)

        parentLayout = view.findViewById(R.id.main)
        parentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }

        val bottomNavView = activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNavView?.visibility = View.VISIBLE


        patientSpinner = binding.patientSpinner
        val scan = activity?.findViewById<FrameLayout>(R.id.scanButtonContainer)
        scan?.visibility = View.VISIBLE

        fetchUserData()
        observeOnlineStatus()

        fetchPatientsForSpinner()
        return view



    }


    private val onlinePatientIds = mutableSetOf<String>()

    private fun observeOnlineStatus() {
        val heartRateRef = realtimeDB.child("heart_rate")

        heartRateRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onlinePatientIds.clear()

                for (patientSnapshot in snapshot.children) {
                    val patientId = patientSnapshot.key ?: continue
                    val latest = patientSnapshot.child("latest").getValue(Int::class.java) ?: 0

                    if (latest > 0) {
                        onlinePatientIds.add(patientId)
                    }
                }

                Log.d("SpinnerRefresh", "Online status updated: $onlinePatientIds")

                // Refresh Spinner agar warna update
                patientAdapter?.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineStatus", "Failed to read online status", error.toException())
            }
        })
    }


    private fun fetchPatientsForSpinner() {
        getActualDoctorUID { doctorId ->
            if (doctorId == null) {
                Toast.makeText(requireContext(), "Dokter tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            firestore.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorId)
                .get()
                .addOnSuccessListener { assignedSnapshot ->
                    Log.d("AssignedPatientsQuery", "Assigned Patients: ${assignedSnapshot.documents.size}")

                    if (assignedSnapshot.isEmpty) {
                        Toast.makeText(requireContext(), "Tidak ada pasien yang ditugaskan", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val patientIds = assignedSnapshot.documents.mapNotNull { it.getString("patientId") }
                    Log.d("AssignedPatientIds", "Patient IDs: $patientIds")

                    patientList.clear()

                    // Add "All" option to the patient list first
                    patientList.add(PatientData(name = "All", id = "All", age = "", gender = "", heartRate = "", systolicBP = "", diastolicBP = "", email = "", phoneNumber = "", photoUrl = ""))

                    // Fetch patient details for the spinner
                    for (patientId in patientIds) {
                        firestore.collection("users_patient_email").document(patientId).get()
                            .addOnSuccessListener { userDoc ->
                                Log.d("UserDoc", "Fetched user data for patient: $patientId")

                                val name = userDoc.getString("fullName") ?: "N/A"
                                Log.d("PatientData", "Patient name: $name")

                                if (name != "N/A") {
                                    val age = userDoc.getString("age") ?: "N/A"
                                    val gender = userDoc.getString("gender") ?: "N/A"
                                    val heartRate = userDoc.getString("heartRate") ?: "N/A"
                                    val systolicBP = userDoc.getString("systolicBP") ?: "N/A"
                                    val diastolicBP = userDoc.getString("diastolicBP") ?: "N/A"
                                    val email = userDoc.getString("email") ?: "N/A"
                                    val phoneNumber = userDoc.getString("phoneNumber") ?: "N/A"
                                    val photoUrl = userDoc.getString("photoUrl") ?: "N/A"

                                    val patient = PatientData(
                                        id = patientId,
                                        name = name,
                                        age = age,
                                        gender = gender,
                                        heartRate = heartRate,
                                        systolicBP = systolicBP,
                                        diastolicBP = diastolicBP,
                                        email = email,
                                        phoneNumber = phoneNumber,
                                        photoUrl = photoUrl
                                    )
                                    patientList.add(patient)

                                    Log.d("PatientFetch", "Added patient: $name")
                                } else {
                                    Log.d("PatientFetch", "Skipped patient due to invalid name: $patientId")
                                }

                                // After fetching all patients, set the spinner listener
                                if (patientList.isNotEmpty()) {
                                    val patientNames = patientList.map { it.name }
                                    patientAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, patientNames) {
                                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                            val view = super.getView(position, convertView, parent)
                                            val textView = view.findViewById<TextView>(android.R.id.text1)

                                            val patientId = patientList.getOrNull(position)?.id.orEmpty()
                                            val isOnline = onlinePatientIds.contains(patientId)

                                            if (isOnline) {
                                                // Online: abu-abu background, teks hitam
                                                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                                                textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                                            } else {
                                                // Offline: ungu background, teks putih
                                                view.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_dark))
                                                textView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                            }

                                            return view
                                        }

                                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                                            val view = super.getDropDownView(position, convertView, parent)
                                            val textView = view.findViewById<TextView>(android.R.id.text1)

                                            val patientId = patientList.getOrNull(position)?.id.orEmpty()
                                            val isOnline = onlinePatientIds.contains(patientId)

                                            if (isOnline) {
                                                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                                                textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                                            } else {
                                                view.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_dark))
                                                textView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                            }

                                            return view
                                        }
                                    }


                                    patientSpinner.adapter = patientAdapter

                                    Log.d("SpinnerAdapter", "Adapter set with names: $patientNames")

                                    // Apply the custom background to the spinner
                                    patientSpinner.setBackgroundResource(R.drawable.spinner_background)  // Set the background drawable

                                    // Set the "All" option as the default selected item
                                    patientSpinner.setSelection(0)

                                    // Set the spinner listener here after populating the data
                                    setSpinnerListener()
                                } else {
                                    patientSpinner.visibility = View.GONE
                                    Toast.makeText(requireContext(), "No patients available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("PatientFetchError", "Failed to fetch data for patient: $patientId", exception)
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal mengambil data pasien", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun setSpinnerListener() {
        patientSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedPatient = patientList[position]

                Log.d("SelectedPatient", "Selected Patient: ${selectedPatient.name}")

                // Observe the heart rate of the selected patient
                if (selectedPatient.id != "All") {
                    if (onlinePatientIds.contains(selectedPatient.id)) {
                        observePatientFromRealtime(selectedPatient.id)
                    } else {
                        observeSelectedPatientHeartRate(selectedPatient.id)
                    }
                } else {
                    updatePatientChartForAll()
                }

            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Toast.makeText(requireContext(), "No patient selected", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Function to observe the heart rate data for a single selected patient
    private fun observeSelectedPatientHeartRate(patientId: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e("HeartRate", "Doctor UID is null")
                return@getActualDoctorUID
            }

            fetchAveragePatientAge(doctorUID) { avgAge ->
                if (avgAge == null) {
                    Log.w("HeartRate", "Average age not available")
                    return@fetchAveragePatientAge
                }

                val heartRateRef = firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", patientId)
                    .whereGreaterThanOrEqualTo("timestamp", "$today 00:00:00")
                    .whereLessThanOrEqualTo("timestamp", "$today 23:59:59")
                    .orderBy("timestamp", Query.Direction.DESCENDING)

                heartRateRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("HomeDoctorFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        var totalHeartRate = 0.0
                        var totalSystolic = 0.0
                        var totalDiastolic = 0.0
                        var totalBattery = 0.0
                        var count = 0

                        val heartRates = mutableListOf<Float>()

                        for (doc in snapshot.documents) {
                            val heartRate = doc.getDouble("heartRate") ?: continue
                            val systolic = doc.getDouble("systolicBP") ?: continue
                            val diastolic = doc.getDouble("diastolicBP") ?: continue
                            val battery = doc.getDouble("batteryLevel") ?: 0.0

                            heartRates.add(heartRate.toFloat())

                            totalHeartRate += heartRate
                            totalSystolic += systolic
                            totalDiastolic += diastolic
                            totalBattery += battery
                            count++
                        }

                        updateHeartRateChart(heartRates)

                        if (count > 0) {
                            val avgHeartRate = (totalHeartRate / count).roundToInt()
                            val avgSystolic = (totalSystolic / count).roundToInt()
                            val avgDiastolic = (totalDiastolic / count).roundToInt()
                            val avgBattery = (totalBattery / count).roundToInt()

                            binding.heartRateValue.text = "$avgHeartRate"
                            binding.bpValue.text = "$avgSystolic/$avgDiastolic"
                            binding.batteryValue.text = "$avgBattery%"

                            updateIndicator(avgHeartRate, avgAge)
                        }
                    } else {
                        Log.d("HeartRate", "No heart rate data found for patient $patientId.")
                        showEmptyImage()
                        binding.heartRateValue.text = "null"
                        binding.bpValue.text = "null"
                        binding.batteryValue.text = "null"
                        updateIndicator(0, null)
                    }
                }
            }
        }
    }

    private fun observePatientFromRealtime(patientId: String) {
        val heartRateRef = realtimeDB.child("heart_rate").child(patientId).child("latest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(Int::class.java) ?: 0

                if (!isAdded || view == null) return

                binding?.let { binding ->
                    binding.heartRateValue.text = "$heartRate"
                    updateHeartRateChart(listOf(heartRate.toFloat()))
                }

                // Ambil data BP & Battery terakhir dari Firestore
                firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", patientId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (!isAdded || view == null) return@addOnSuccessListener

                        val doc = result.documents.firstOrNull()
                        val systolic = doc?.getDouble("systolicBP")?.roundToInt() ?: 0
                        val diastolic = doc?.getDouble("diastolicBP")?.roundToInt() ?: 0
                        val battery = doc?.getDouble("batteryLevel")?.roundToInt() ?: 0

                        binding?.let { binding ->
                            binding.bpValue.text = "$systolic/$diastolic"
                            binding.batteryValue.text = "$battery%"
                        }

                        fetchPatientAgeById(patientId) { avgAge ->
                        if (!isAdded || view == null) return@fetchPatientAgeById
                            if (avgAge == null) {
                                Log.w("UpdateIndicator", "avgAge is null, cannot update indicator")
                                return@fetchPatientAgeById
                            }

                            Log.d("UpdateIndicator", "Updating indicator with HR=$heartRate, Age=$avgAge")
                            updateIndicator(heartRate, avgAge)
                        }

                    }
                    .addOnFailureListener { error ->
                        Log.e("FirestoreFetch", "Failed to fetch BP/Battery", error)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RealtimeHeartRate", "Error fetching realtime heart rate: ${error.message}")
            }
        }

        heartRateRef.addValueEventListener(listener)
        heartRateListeners[patientId] = listener
    }

    private fun fetchPatientAgeById(patientId: String, callback: (Int?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()

        val emailQuery = firestore.collection("users_patient_email")
            .whereEqualTo("userId", patientId)
            .get()

        val phoneQuery = firestore.collection("users_patient_phonenumber")
            .whereEqualTo("userId", patientId)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(emailQuery, phoneQuery)
            .addOnSuccessListener { results ->
                val emailSnapshot = results[0] as QuerySnapshot
                val phoneSnapshot = results[1] as QuerySnapshot

                val emailAges = emailSnapshot.documents.mapNotNull {
                    it.getString("age")?.toIntOrNull()
                }
                val phoneAges = phoneSnapshot.documents.mapNotNull {
                    it.getString("age")?.toIntOrNull()
                }

                val allAges = emailAges + phoneAges
                val avgAge = if (allAges.isNotEmpty()) allAges.sum() / allAges.size else null

                Log.d("AvgAgeByPatientId", "Average age for $patientId = $avgAge")
                callback(avgAge)
            }
            .addOnFailureListener {
                Log.e("AvgAgeByPatientId", "Failed to get age data", it)
                callback(null)
            }
    }

    private fun fetchAveragePatientAge(doctorUID: String, onResult: (Int?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("assigned_patient")
            .whereEqualTo("doctorUid", doctorUID)
            .get()
            .addOnSuccessListener { assignedSnapshot ->
                val patientIds = assignedSnapshot.documents.mapNotNull { it.getString("patientId") }

                if (patientIds.isEmpty()) {
                    Log.d("DoctorData", "No assigned patients found")
                    onResult(null)
                    return@addOnSuccessListener
                }

                val emailQuery = firestore.collection("users_patient_email")
                    .whereIn("userId", patientIds)
                    .get()

                val phoneQuery = firestore.collection("users_patient_phonenumber")
                    .whereIn("userId", patientIds)
                    .get()

                Tasks.whenAllSuccess<QuerySnapshot>(emailQuery, phoneQuery)
                    .addOnSuccessListener { results ->
                        val emailSnapshot = results[0] as QuerySnapshot
                        val phoneSnapshot = results[1] as QuerySnapshot

                        val emailAges = emailSnapshot.documents.mapNotNull {
                            it.getString("age")?.toIntOrNull()
                        }
                        val phoneAges = phoneSnapshot.documents.mapNotNull {
                            it.getString("age")?.toIntOrNull()
                        }

                        val allAges = emailAges + phoneAges
                        val avgAge = if (allAges.isNotEmpty()) allAges.sum() / allAges.size else null

                        Log.d("DoctorData", "Average patient age: $avgAge")
                        onResult(avgAge)
                    }
                    .addOnFailureListener {
                        Log.e("DoctorData", "Failed to get age data", it)
                        onResult(null)
                    }
            }
            .addOnFailureListener {
                Log.e("DoctorData", "Failed to get assigned patients", it)
                onResult(null)
            }
    }

    private var patientChartListener: ListenerRegistration? = null

    // Function to update the chart for "All" patients (average heart rate for the day per patient)
    private fun updatePatientChartForAll() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e("HomeDoctorFragment", "Doctor UID is null")
                return@getActualDoctorUID
            }

            fetchAveragePatientAge(doctorUID) { avgAge ->
                if (avgAge == null) {
                    Log.w("HomeDoctorFragment", "Average age not available")
                    return@fetchAveragePatientAge
                }

                val heartRateRef = firestore.collection("patient_heart_rate")
                    .whereIn("userId", patientList.map { it.id })
                    .orderBy("timestamp", Query.Direction.DESCENDING)

                // ✅ Simpan listener agar bisa di-remove saat onDestroyView
                patientChartListener = heartRateRef.addSnapshotListener { snapshot, e ->
                    // ✅ Hindari update UI jika binding sudah null
                    if (!isAdded || view == null || binding == null) return@addSnapshotListener

                    if (e != null) {
                        Log.w("HomeDoctorFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        var totalHeartRate = 0.0
                        var totalSystolic = 0.0
                        var totalDiastolic = 0.0
                        var totalBattery = 0.0
                        var count = 0

                        val heartRates = mutableListOf<Float>()

                        for (doc in snapshot.documents) {
                            val timestamp = doc.getString("timestamp") ?: continue
                            if (!timestamp.startsWith(today)) continue

                            val heartRate = doc.getDouble("heartRate") ?: continue
                            val systolic = doc.getDouble("systolicBP") ?: continue
                            val diastolic = doc.getDouble("diastolicBP") ?: continue
                            val battery = doc.getDouble("batteryLevel") ?: 0.0

                            heartRates.add(heartRate.toFloat())

                            totalHeartRate += heartRate
                            totalSystolic += systolic
                            totalDiastolic += diastolic
                            totalBattery += battery
                            count++
                        }

                        updateHeartRateChart(heartRates)

                        if (count > 0) {
                            val avgHeartRate = (totalHeartRate / count).roundToInt()
                            val avgSystolic = (totalSystolic / count).roundToInt()
                            val avgDiastolic = (totalDiastolic / count).roundToInt()
                            val avgBattery = (totalBattery / count).roundToInt()

                            binding?.heartRateValue?.text = "$avgHeartRate"
                            binding?.bpValue?.text = "$avgSystolic/$avgDiastolic"
                            binding?.batteryValue?.text = "$avgBattery%"

                            updateIndicator(avgHeartRate, avgAge)
                        }
                    } else {
                        Log.d("HeartRate", "No heart rate data found for any patient.")
                        showEmptyImage()
                        binding?.heartRateValue?.text = "null"
                        binding?.bpValue?.text = "null"
                        binding?.batteryValue?.text = "null"
                        updateIndicator(0, null)
                    }
                }
            }
        }
    }





    // Function to update the chart with the data (heart rates) from the patients
    private fun updateHeartRateChart(heartRates: List<Float>) {
        if (!isAdded || view == null || binding == null) return  // <-- Tambahan aman

        binding?.let { binding ->
            val chart = binding.heartRateChart
            chart.setData(heartRates)
            chart.invalidate()
        }
    }



    // Function to show the empty image if no data is available
    // Function to show the empty image if no data is available
    private fun showEmptyImage() {
        val chartView = binding.heartRateChart
        chartView.setData(emptyList()) // ← tambahkan ini agar barChartView masuk ke kondisi empty
    }









//    private fun startLiveLocationUpdates() {
//        val mapsTextView = view?.findViewById<TextView>(R.id.maps)
//
//        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
//            interval = 5000
//            fastestInterval = 3000
//            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
//        }
//
//        locationCallback = object : com.google.android.gms.location.LocationCallback() {
//            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
//                if (!isAdded) return
//                val location = locationResult.lastLocation
//                if (location != null) {
//                    currentLat = location.latitude
//                    currentLon = location.longitude
//
//                    // Reverse geocode untuk mendapatkan alamat lengkap
//                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
//                    try {
//                        val addresses = geocoder.getFromLocation(currentLat!!, currentLon!!, 1)
//                        if (!addresses.isNullOrEmpty()) {
//                            val address = addresses[0]
//                            val fullAddress = buildString {
//                                append(address.thoroughfare ?: "")              // Nama jalan
//                                if (!address.subThoroughfare.isNullOrEmpty()) {
//                                    append(" ${address.subThoroughfare}")       // Nomor rumah
//                                }
//                                if (!address.locality.isNullOrEmpty()) {
//                                    append(", ${address.locality}")             // Kota
//                                }
//                                // Jangan tambahkan postalCode dan countryName
//                            }
//
//                            mapsTextView?.text = fullAddress
//                        } else {
//                            mapsTextView?.text = "Address not found"
//                        }
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        mapsTextView?.text = "Failed to get address"
//                    }
//                } else {
//                    mapsTextView?.text = "Location not available"
//                }
//            }
//        }
//
//        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
//            == PackageManager.PERMISSION_GRANTED
//        ) {
//            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, requireActivity().mainLooper)
//        } else {
//            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
//        }
//    }

//    private fun fetchAveragePatientData() {
//        getActualDoctorUID { doctorUID ->
//            if (doctorUID == null) {
//                Log.e("DoctorData", "Doctor UID is null")
//                return@getActualDoctorUID
//            }
//
//            firestore.collection("assigned_patient")
//                .whereEqualTo("doctorUid", doctorUID)
//                .addSnapshotListener { assignedSnapshot, assignedError ->
//                    if (assignedError != null) {
//                        Log.e("DoctorData", "Error listening to assigned_patient", assignedError)
//                        return@addSnapshotListener
//                    }
//
//                    val patientIds = assignedSnapshot?.documents?.mapNotNull { it.getString("patientId") } ?: emptyList()
//                    if (patientIds.isEmpty()) {
//                        Log.d("DoctorData", "No assigned patients found")
//                        return@addSnapshotListener
//                    }
//
//                    // Ambil data usia dari koleksi users
//                    val emailQuery = firestore.collection("users_patient_email")
//                        .whereIn("userId", patientIds)
//                        .get()
//
//                    val phoneQuery = firestore.collection("users_patient_phonenumber")
//                        .whereIn("userId", patientIds)
//                        .get()
//
//                    Tasks.whenAllSuccess<QuerySnapshot>(emailQuery, phoneQuery)
//                        .addOnSuccessListener { results ->
//                            val emailSnapshot = results[0] as QuerySnapshot
//                            val phoneSnapshot = results[1] as QuerySnapshot
//
//                            val emailAges = emailSnapshot.documents.mapNotNull {
//                                it.getString("age")?.toIntOrNull()
//                            }
//                            val phoneAges = phoneSnapshot.documents.mapNotNull {
//                                it.getString("age")?.toIntOrNull()
//                            }
//
//
//                            val allAges = emailAges + phoneAges
//                            val avgAge = if (allAges.isNotEmpty()) allAges.sum() / allAges.size else null
//
//                            firestore.collection("patient_heart_rate")
//                                .whereIn("userId", patientIds)
//                                .addSnapshotListener { heartSnapshot, heartError ->
//                                    if (heartError != null) {
//                                        Log.e("DoctorData", "Error listening to patient_heart_rate", heartError)
//                                        return@addSnapshotListener
//                                    }
//
//                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
//                                    var totalHeartRate = 0.0
//                                    var totalSystolic = 0.0
//                                    var totalDiastolic = 0.0
//                                    var totalBattery = 0.0
//                                    var count = 0
//
//                                    for (doc in heartSnapshot?.documents ?: emptyList()) {
//                                        val timestampStr = doc.getString("timestamp") ?: continue
//                                        if (!timestampStr.startsWith(today)) continue
//
//                                        val heartRate = doc.getDouble("heartRate") ?: continue
//                                        if (heartRate == 0.0) continue
//                                        val systolic = doc.getDouble("systolicBP") ?: continue
//                                        val diastolic = doc.getDouble("diastolicBP") ?: continue
//                                        val battery = doc.getDouble("batteryLevel") ?: 0.0
//
//                                        totalHeartRate += heartRate
//                                        totalSystolic += systolic
//                                        totalDiastolic += diastolic
//                                        totalBattery += battery
//                                        count++
//                                    }
//
//                                    _binding?.let { binding ->
//                                        if (count > 0) {
//                                            val avgHeartRate = (totalHeartRate / count).roundToInt()
//                                            val avgSystolic = (totalSystolic / count).roundToInt()
//                                            val avgDiastolic = (totalDiastolic / count).roundToInt()
//                                            val avgBattery = (totalBattery / count).roundToInt()
//
//                                            binding.heartRateValue.text = "$avgHeartRate"
//                                            binding.bpValue.text = "$avgSystolic/$avgDiastolic"
//                                            binding.batteryValue.text = "$avgBattery%"
//
//                                            // Update indikator dan warna latar belakang
//                                            updateIndicator(avgHeartRate, avgAge)
//                                            val chartView = binding.heartRateChart
//                                            val heartRates = heartSnapshot?.documents
//                                                ?.filter {
//                                                    val t = it.getString("timestamp") ?: return@filter false
//                                                    t.startsWith(today)
//                                                }
//                                                ?.mapNotNull { it.getDouble("heartRate")?.toFloat() }
//                                                ?: emptyList()
//
//                                            chartView.setData(heartRates)
//
//
//
//                                        } else {
//                                            binding.heartRateValue.text = "null"
//                                            binding.bpValue.text = "null"
//                                            binding.batteryValue.text = "null"
//                                            binding.indicatorValue.text = "null"
//                                            Log.d("DoctorData", "No valid patient data for today")
//                                        }
//                                    }
//                                }
//                        }
//                }
//        }
//    }
    private fun stopLiveLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }


    private fun updateIndicator(avgHeartRate: Int, avgAge: Int?) {
        avgAge?.let { age ->
            val maxWarning = 220 - age
            val minWarning = (maxWarning * 0.8).toInt()

            val (statusText, colorRes) = when {
                avgHeartRate >= maxWarning -> "Danger" to R.color.red
                avgHeartRate < minWarning -> "Health" to R.color.green
                else -> "Warning" to R.color.yellow
            }

            _binding?.let { binding ->
                binding.indicatorValue.text = statusText
                binding.indicatorValue.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.black))

                val background = ContextCompat.getDrawable(binding.root.context, R.drawable.rounded_status_bg)?.mutate()
                val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
                wrappedDrawable?.let {
                    DrawableCompat.setTint(it, ContextCompat.getColor(binding.root.context, colorRes))
                    binding.indicatorBox.background = it
                }
            }
        } ?: run {
            _binding?.indicatorValue?.text = "null"
        }
    }
//    private fun checkLocationPermissionAndUpdateMaps() {
//        val mapsTextView = view?.findViewById<TextView>(R.id.maps)
//
//        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
//        } else {
//            // Cek apakah layanan lokasi aktif (GPS atau Network)
//            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
//            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
//            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
//
//            if (!isGpsEnabled && !isNetworkEnabled) {
//                // Jika lokasi tidak aktif, tampilkan pesan dan arahkan ke setting
//                mapsTextView?.text = "Location not available, enable location services"
//                openLocationSettings()
//            } else {
//                // Jika layanan lokasi aktif, mulai live updates
//                startLiveLocationUpdates()
//            }
//        }
//    }
    private fun openLocationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PatientPagerAdapter(this)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTab = position // Track the current selected tab
                if (currentTab == 1) { // Offline tab
                    updateViewPagerHeightForChild()
                } else {
                    updateViewPagerHeightForChild()
                }
            }
        })

        // Initially adjust the height for the first page (if needed)
        updateViewPagerHeightForChild()


        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (!userId.isNullOrEmpty()) {
            loadActualDoctorProfilePicture()
        }
//        fetchAveragePatientData()
//        fetchWeeklyAverages()
        monthChartView = view.findViewById(R.id.monthHeartRateChart)
//        fetchMonthlyAverages()

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Online" else "Offline"
        }.attach()

        val profileImage = view.findViewById<ImageView>(R.id.profile)
        profileImage.setOnClickListener {
            navigateToPatientFragment()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val mapsTextView = view.findViewById<TextView>(R.id.maps)
        mapsTextView.setOnClickListener {
            if (currentLat != null && currentLon != null) {
                try {
                    // Coba buka dengan Google Maps
                    val gmmIntentUri = Uri.parse("geo:${currentLat},${currentLon}?q=${currentLat},${currentLon}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    Log.e(HomeFragment.TAG, "Google Maps failed, trying web URL.")

                    // Jika gagal, buka dengan URL Maps
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$currentLat,$currentLon")
                    )
                    startActivity(webIntent)
                }
            } else {
                Log.e(HomeFragment.TAG, "Coordinates are null. Cannot open maps.")
            }
        }


        val dayTextView = binding.day
        val currentDate = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(currentDate)
        val dayFormat = SimpleDateFormat("EEEE", Locale.ENGLISH)  // Format to get the day name
        val dayName = dayFormat.format(currentDate)  // Get the name of the day (e.g., "Monday")
        dayTextView.text = "$dayName, $formattedDate"



//
//        checkLocationPermissionAndUpdateMaps()

        // Trigger resize untuk halaman pertama
        binding.tabLayout.post {
            val tabStrip = binding.tabLayout.getChildAt(0) as ViewGroup
            for (i in 0 until tabStrip.childCount) {
                val tabView = tabStrip.getChildAt(i)

                // Set margin kanan antar tab
                val params = tabView.layoutParams as ViewGroup.MarginLayoutParams
                params.marginEnd = 12
                params.width = (80 * resources.displayMetrics.density).toInt()  // Lebar 80dp
                params.height = (30 * resources.displayMetrics.density).toInt() // Tinggi 22dp
                tabView.layoutParams = params

            }
        }



    }

    private fun updateViewPagerHeightForChild() {
        val currentView = getChildAtCurrentPosition() ?: return
        val newHeight = currentView.height

        // Set new height for ViewPager2
        val params = binding.viewPager.layoutParams
        if (params.height != newHeight) {
            params.height = newHeight
            binding.viewPager.layoutParams = params
        }
    }

    // Helper function to get the currently visible child view in ViewPager2
    private fun getChildAtCurrentPosition(): View? {
        val recyclerView = binding.viewPager.getChildAt(0) as? ViewGroup ?: return null
        val position = binding.viewPager.currentItem
        if (position < recyclerView.childCount) {
            return recyclerView.getChildAt(position)
        }
        return null
    }


    private fun navigateToSearchFragment(query: String) {
        val bundle = Bundle().apply {
            putString("searchQuery", query) // Kirim keyword ke fragment tujuan
        }
        val fragment = SearchDoctorFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment)
            .addToBackStack(null)
            .commit()
    }
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        Log.d("ProfileDoctor", "Current User Email: $email")
        Log.d("ProfileDoctor", "Formatted Phone: $phoneNumber")

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_doctor_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfileDoctor", "Email query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfileDoctor", "No user document found for email")
                        onResult(null)  // No user document found for email
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfileDoctor", "Found userId for email: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileDoctor", "Error querying email", e)
                    onResult(null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_doctor_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfileDoctor", "Phone number query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfileDoctor", "No user document found for phone number")
                        onResult(null)  // No user document found for phone number
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfileDoctor", "Found userId for phone number: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileDoctor", "Error querying phone number", e)
                    onResult(null)
                }
        } else {
            Log.e("ProfileDoctor", "No email or phone number found for the current user")
            onResult(null)  // If neither email nor phone is available
        }
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email
        var userPhone = currentUser.phoneNumber?.let { formatPhoneNumber(it) } // Format nomor sebelum query

        Log.d("ProfilePatient", "User Email: $userEmail | User Phone (Formatted): $userPhone")

        val query: Pair<String, String>? = when {
            !userEmail.isNullOrEmpty() -> Pair("users_doctor_email", userEmail)
            !userPhone.isNullOrEmpty() -> Pair("users_doctor_phonenumber", userPhone)
            else -> null
        }

        if (query == null) {
            Toast.makeText(requireContext(), "No email or phone found", Toast.LENGTH_SHORT).show()
            return
        }

        val (collection, identifier) = query
        firestore.collection(collection).whereEqualTo(if (collection == "users_doctor_email") "email" else "phoneNumber", identifier)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    documentId = document.id
                    val fullName = document.getString("fullName") ?: "Unknown Name"
                    val age = document.getString("age") ?: "Unknown Age"
                    val gender = document.getString("gender") ?: "Unknown Gender"

                    binding.maps.text = "Dr. $fullName"

                    Log.d("ProfilePatient", "User Data Loaded: $fullName, $age, $gender, Identifier: $identifier")

                    // **Ambil foto profil dari Firestore jika ada**
                    loadActualDoctorProfilePicture()
                } else {
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Log.e("ProfilePatient", "Error fetching user data", e)
                Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
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

    private fun loadActualDoctorProfilePicture() {
        getActualDoctorUID { patientUserId ->
            if (patientUserId != null) {
                loadProfilePicture(patientUserId)
            } else {
                Log.e("ProfilePatient", "Failed to get actual patient UID")
                // Bisa juga kasih placeholder/default image kalau perlu
            }
        }
    }

    private fun loadProfilePicture(userId: String) {
        firestore.collection("doctor_photoprofile").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val photoUrl = document.getString("photoUrl")
                    Log.d("HomeFragment", "Photo URL from Firestore: $photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.account_circle) // gambar default sementara
                            .error(R.drawable.account_circle)        // gambar error jika gagal load
                            .into(binding.profile)
                    } else {
                        Log.w("HomeFragment", "photoUrl field is empty or null")
                    }
                } else {
                    Log.w("HomeFragment", "No document found for userId: $userId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Failed to get photo profile", e)
            }

    }

    private fun navigateToPatientFragment() {
        val fragment = ProfileDoctorFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment) // Sesuaikan dengan container di layout
            .addToBackStack(null) // Tambahkan ke backstack agar bisa kembali ke Home
            .commit()
    }


    private fun getPatientAgeByUID(patientUID: String, onResult: (Int?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users_patient_email")
            .whereEqualTo("userId", patientUID)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val age = docs.firstOrNull()?.getString("age")?.toInt()
                    return@addOnSuccessListener onResult(age)
                }

                db.collection("users_patient_phonenumber")
                    .whereEqualTo("userId", patientUID)
                    .get()
                    .addOnSuccessListener { phoneDocs ->
                        val age = phoneDocs.firstOrNull()?.getString("age")?.toInt()
                        onResult(age)
                    }
                    .addOnFailureListener { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }




    private fun fetchWeeklyAverages() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e(HomeFragment.TAG, "❌ UID dokter tidak ditemukan.")
                updateUI(0, 0.0, 0.0, 0)
                return@getActualDoctorUID
            }

            val db = FirebaseFirestore.getInstance()

            // Ambil pasien yang di-assign ke dokter ini
            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .get()
                .addOnSuccessListener { assignedDocs ->
                    val assignedPatientUIDs = assignedDocs.mapNotNull { it.getString("patientId") }

                    if (assignedPatientUIDs.isEmpty()) {
                        Log.d(HomeFragment.TAG, "❌ Tidak ada pasien yang di-assign ke dokter ini.")
                        updateUI(0, 0.0, 0.0, 0)
                        return@addOnSuccessListener
                    }

                    db.collection("patient_heart_rate")
                        .whereIn("userId", assignedPatientUIDs)
                        .addSnapshotListener { documents, error ->
                            if (error != null) {
                                Log.e(HomeFragment.TAG, "❌ Error fetching health data", error)
                                return@addSnapshotListener
                            }

                            if (documents == null || documents.isEmpty) {
                                Log.d(HomeFragment.TAG, "📭 Tidak ada data heart rate minggu ini.")
                                updateUI(0, 0.0, 0.0, 0)
                                return@addSnapshotListener
                            }

                            val weekMap = LinkedHashMap<String, MutableList<HealthData>>()
                            val firstWeekRange = getFirstWeekOfCurrentMonth()
                            val currentMonthYear = getCurrentMonthYear()

                            val docsList = documents.toList()
                            val patientAgeMap = mutableMapOf<String, Int?>()

                            // Langkah 1: Ambil umur semua pasien terkait
                            var ageFetchCount = 0
                            for (doc in docsList) {
                                val userId = doc.getString("userId") ?: continue
                                if (userId !in patientAgeMap) {
                                    getPatientAgeByUID(userId) { age ->
                                        patientAgeMap[userId] = age
                                        ageFetchCount++

                                        // Lanjut hanya kalau semua umur sudah didapat
                                        if (ageFetchCount == docsList.size) {
                                            processWeeklyData(docsList, assignedPatientUIDs, patientAgeMap, weekMap, firstWeekRange, currentMonthYear)
                                        }
                                    }
                                } else {
                                    ageFetchCount++
                                    if (ageFetchCount == docsList.size) {
                                        processWeeklyData(docsList, assignedPatientUIDs, patientAgeMap, weekMap, firstWeekRange, currentMonthYear)
                                    }
                                }
                            }
                        }
                }
                .addOnFailureListener {
                    Log.e(HomeFragment.TAG, "❌ Gagal mengambil assigned_patient", it)
                    updateUI(0, 0.0, 0.0, 0)
                }
        }
    }

    // Fungsi untuk memproses dan mengelompokkan data mingguan
    private fun processWeeklyData(
        docsList: List<DocumentSnapshot>,
        assignedPatientUIDs: List<String>,
        patientAgeMap: Map<String, Int?>,
        weekMap: LinkedHashMap<String, MutableList<HealthData>>,
        firstWeekRange: Pair<String, String>,
        currentMonthYear: String
    ) {
        for (doc in docsList) {
            val userId = doc.getString("userId") ?: continue
            if (userId !in assignedPatientUIDs) continue

            val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
            if (heartRate == 0) continue

            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
            val timestamp = doc.getString("timestamp") ?: continue

            val dataMonthYear = extractMonthYearFromTimestamp(timestamp)
            if (dataMonthYear != currentMonthYear) {
                Log.d(HomeFragment.TAG, "📌 Data diabaikan (bukan bulan berjalan): $timestamp")
                continue
            }

            val weekNumber = getWeekOfMonth(timestamp, firstWeekRange)
            val userAge = patientAgeMap[userId]

            val healthData = HealthData(
                heartRate = heartRate,
                bloodPressure = "$systolicBP/$diastolicBP",
                batteryLevel = batteryLevel,
                timestamp = timestamp,
                fullTimestamp = timestamp,
                userAge = userAge
            )

            weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
        }

        val latestWeek = weekMap.keys.maxByOrNull { week ->
            extractStartEndDateFromWeek(week).first
        }

        val currentWeekData = latestWeek?.let { weekMap[it] }

        if (!currentWeekData.isNullOrEmpty()) {
            val avgHeartRate = currentWeekData.map { it.heartRate }.average().toInt()
            val avgSystolicBP = currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
            val avgDiastolicBP = currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
            val avgBatteryLevel = currentWeekData.map { it.batteryLevel }.average().toInt()

            Log.d(HomeFragment.TAG, "✅ Averages -> HR: $avgHeartRate, BP: $avgSystolicBP/$avgDiastolicBP, Battery: $avgBatteryLevel")
            updateUI(avgHeartRate, avgSystolicBP.toDouble(), avgDiastolicBP.toDouble(), avgBatteryLevel)
        } else {
            Log.d(HomeFragment.TAG, "⚠ Tidak ada data valid untuk minggu terbaru.")
            updateUI(0, 0.0, 0.0, 0)
        }
    }

    private fun getFirstWeekOfCurrentMonth(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        val startOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        Log.d("WeekCalculation", "Corrected First Week of Current Month: $startOfWeek - $endOfWeek")

        return Pair(startOfWeek, endOfWeek)
    }

    private fun getCurrentMonthYear(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }

    private fun getWeekOfMonth(timestamp: String, firstWeekRange: Pair<String, String>): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val compareFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"

        val firstWeekStart = compareFormat.parse("${firstWeekRange.first} 00:00:00")
        val firstWeekEnd = compareFormat.parse("${firstWeekRange.second} 23:59:59")

        val calendar = Calendar.getInstance().apply { time = date }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val startDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)

        val weekNumber = ((calendar.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1

        Log.d(HomeFragment.TAG, "📅 Tanggal: $timestamp -> Week: $weekNumber ($startDate - $endDate) di $currentMonth")

        return when {
            date.after(firstWeekStart) && date.before(firstWeekEnd) ->
                "Week 1 (${firstWeekRange.first} - ${firstWeekRange.second})"
            else ->
                "Week $weekNumber ($startDate - $endDate)"
        }
    }

    private fun extractStartEndDateFromWeek(weekText: String): Pair<String, String> {
        return try {
            val regex = """\((\d{2} \w{3} \d{4}) - (\d{2} \w{3} \d{4})\)""".toRegex()
            val matchResult = regex.find(weekText)

            if (matchResult != null) {
                val startDate = matchResult.groupValues[1]
                val endDate = matchResult.groupValues[2]
                return Pair(startDate, endDate)
            }

            Log.e("WeekParsing", "Failed to extract start/end date from: $weekText")
            Pair("01 Jan 1900", "01 Jan 1900")
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting start/end date from week: ${e.message}")
            Pair("01 Jan 1900", "01 Jan 1900")
        }
    }

    private fun updateUI(heartRate: Int, systolic: Double, diastolic: Double, battery: Int) {
        Log.d(HomeFragment.TAG, "Updating UI with -> HeartRate: $heartRate, BP: ${systolic.roundToInt()} / ${diastolic.roundToInt()}, Battery: $battery%")

//        view?.findViewById<TextView>(R.id.avg_week_heartrate)?.text =
//            if (heartRate > 0) "$heartRate" else "null"

        view?.findViewById<TextView>(R.id.avg_week_bloodpressure)?.text =
            if (systolic > 0 && diastolic > 0) "${systolic.roundToInt()} / ${diastolic.roundToInt()}" else "null"

        view?.findViewById<TextView>(R.id.avg_week_battery)?.text =
            if (battery > 0) "$battery %" else "null"
    }
    private fun extractMonthYearFromTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp) ?: return "Unknown Month"

            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            Log.e(HomeFragment.TAG, "❌ Error extracting month-year from timestamp: ${e.message}")
            "Unknown Month"
        }
    }

    private fun fetchMonthlyAverages() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e(HomeFragment.TAG, "❌ UID dokter tidak ditemukan.")
                return@getActualDoctorUID
            }

            val db = FirebaseFirestore.getInstance()

            // Step 1: Ambil pasien yang di-assign ke dokter ini
            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .get()
                .addOnSuccessListener { assignedDocs ->
                    val assignedPatientUIDs = assignedDocs.mapNotNull { it.getString("patientId") }

                    if (assignedPatientUIDs.isEmpty()) {
                        Log.d(HomeFragment.TAG, "❌ Tidak ada pasien yang di-assign ke dokter ini.")
                        return@addOnSuccessListener
                    }

                    // Step 2: Ambil data heart rate pasien-pasien tersebut
                    db.collection("patient_heart_rate")
                        .whereIn("userId", assignedPatientUIDs)
                        .addSnapshotListener { documents, error ->
                            if (error != null) {
                                Log.e(HomeFragment.TAG, "❌ Error fetching monthly data: ", error)
                                return@addSnapshotListener
                            }

                            val monthMap = mutableMapOf<String, MutableList<Int>>()

                            val now = Calendar.getInstance()
                            val fourMonthsAgo = Calendar.getInstance().apply {
                                add(Calendar.MONTH, -3)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            if (documents != null) {
                                for (doc in documents) {
                                    val userId = doc.getString("userId") ?: continue
                                    if (userId !in assignedPatientUIDs) continue

                                    val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                                    if (heartRate == 0) continue

                                    val timestamp = doc.getString("timestamp") ?: continue
                                    val timestampMillis = timestampToMillis(timestamp) ?: continue

                                    val dataCalendar = Calendar.getInstance().apply {
                                        timeInMillis = timestampMillis
                                    }

                                    if (dataCalendar.timeInMillis in fourMonthsAgo.timeInMillis..now.timeInMillis) {
                                        val monthName = getMonthNameByIndex(dataCalendar.get(Calendar.MONTH))
                                        monthMap.getOrPut(monthName) { mutableListOf() }.add(heartRate)
                                    }
                                }
                            }

                            // Hitung rata-rata heart rate tiap bulan
                            val monthAverages = monthMap.mapValues { (_, values) -> values.average().toInt() }

                            // Dapatkan urutan 4 bulan terakhir
                            val currentMonthIndex = now.get(Calendar.MONTH)
                            val last4Months = (0..3).map {
                                val monthIndex = (currentMonthIndex - it + 12) % 12
                                getMonthNameByIndex(monthIndex)
                            }.reversed()

                            // Pastikan urutan lengkap
                            val completeData = last4Months.associateWith { monthAverages[it] }

                            Log.d(HomeFragment.TAG, "✅ Filtered Monthly Averages (Last 4 months): $completeData")
                            monthChartView?.setData(completeData)
                        }
                }
                .addOnFailureListener {
                    Log.e(HomeFragment.TAG, "❌ Gagal mengambil assigned_patient", it)
                }
        }
    }


    private fun getMonthNameByIndex(index: Int): String {
        return listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[index]
    }

    private fun timestampToMillis(timestamp: String): Long? {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            formatter.parse(timestamp)?.time
        } catch (e: Exception) {
            null
        }
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.let { binding ->
            val recyclerView = binding.viewPager.getChildAt(0) as? ViewGroup
            val currentView = recyclerView?.getChildAt(binding.viewPager.currentItem)
            currentView?.viewTreeObserver?.removeOnGlobalLayoutListener(globalLayoutListener)
            pageChangeCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        }

        heartRateListeners.forEach { (patientId, listener) ->
            realtimeDB.child("heart_rate").child(patientId).child("latest").removeEventListener(listener)
        }
        heartRateListeners.clear()
        globalLayoutListener = null
        pageChangeCallback = null
        _binding = null
    }

}
