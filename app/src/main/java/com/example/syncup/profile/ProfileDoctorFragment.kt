package com.example.syncup.profile

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.databinding.FragmentProfileDoctorBinding
import com.example.syncup.databinding.FragmentProfilePatientBinding
import com.example.syncup.home.HomeDoctorFragment
import com.example.syncup.inbox.InboxPatientFragment
import com.example.syncup.welcome.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class ProfileDoctorFragment : Fragment() {

    private var _binding: FragmentProfileDoctorBinding? = null
    private val binding get() = _binding!!

    private val storage = FirebaseStorage.getInstance()
    private var imageUri: Uri? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var activeDialog: AlertDialog? = null
    private var documentId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileDoctorBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.logoutbutton.setOnClickListener {
            showLogoutDialog()
        }

        fetchUserData()
        binding.photoprofile.setOnClickListener {
            showImagePickerDialog()
        }

        binding.imageView6.setOnClickListener {
            // Navigate to the Inbox Patient Fragment using FragmentTransaction
            val fragmentTransaction = requireActivity().supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.frame, InboxPatientFragment())
            fragmentTransaction.addToBackStack(null)  // Add the transaction to the back stack if needed
            fragmentTransaction.commit()
        }


        binding.arrow.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Navigate to HomeFragment when back is pressed
            val homeFragment = HomeDoctorFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, homeFragment)  // Ensure 'frame' is the container ID for fragments
                .commit()
        }


        // Tambahkan fungsi edit ketika tombol edit ditekan
        binding.edit.setOnClickListener {
            showEditDialog()
        }

        return view
    }

    private fun loadProfilePicture(userId: String) {
        firestore.collection("doctor_photoprofile").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val photoUrl = document.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this).load(photoUrl).circleCrop().into(binding.photoprofile)
                        Log.d("ProfilePatient", "Profile picture loaded: $photoUrl")
                    }
                } else {
                    Log.d("ProfilePatient", "No profile picture found for user.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfilePatient", "Error loading profile picture", e)
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


    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email
        var userPhone = currentUser.phoneNumber?.let { formatNomorTelepon(it) } // Format nomor sebelum query

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

                    binding.fullname.text = fullName
                    binding.ageGender.text = "$age years - $gender"

                    // **Tambahkan email atau nomor telepon ke UI**
                    binding.phoneoremail.text = identifier
                    binding.emailOrPhonenumber.text = if (collection == "users_doctor_email") "Email" else "Phone Number"

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




    private fun formatNomorTelepon(phoneNumber: String): String {
        return if (phoneNumber.startsWith("+62")) {
            "0" + phoneNumber.substring(3) // Ganti +62 dengan 0
        } else {
            phoneNumber // Jika tidak diawali +62, biarkan apa adanya
        }
    }


    private fun showLogoutDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Ubah warna tombol setelah dialog muncul
        val purpleDark = resources.getColor(R.color.purple_dark, null) // sesuaikan nama warna

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(purpleDark)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(purpleDark)
    }


    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()

        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

        // Arahkan pengguna ke halaman login
        val intent = Intent(requireContext(), WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }


    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Upload Profile Picture")
            .setItems(options) { dialog, which ->
                when (options[which]) {
                    "Take Photo" -> openCamera()
                    "Choose from Gallery" -> openGallery()
                    "Cancel" -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(takePictureIntent, 101)
        }
    }

    private fun openGallery() {
        val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(pickPhoto, 102)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && data != null) { // Kamera
            val bitmap = data.extras?.get("data") as Bitmap
            imageUri = getImageUri(bitmap)
            showConfirmDialog(bitmap)
        } else if (requestCode == 102 && data != null) { // Galeri
            imageUri = data.data
            showConfirmDialog(null)
        }
    }

    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(requireActivity().contentResolver, bitmap, "ProfilePicture", null)
        return Uri.parse(path)
    }

    @SuppressLint("MissingInflatedId")
    private fun showConfirmDialog(bitmap: Bitmap?) {
        activeDialog?.dismiss()
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_photo, null)
        val imagePreview = dialogView.findViewById<ImageView>(R.id.image_preview)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<View>(R.id.btn_save)
        val progressBar = binding.progressBar

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true) // Jangan biarkan dialog tertutup otomatis
            .create()

        if (bitmap != null) {
            imagePreview.setImageBitmap(bitmap)
        } else {
            imageUri?.let { uri ->
                imagePreview.setImageURI(uri)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            fetchUserData() // Reload gambar sebelumnya
        }

        btnSave.setOnClickListener {
            dialog.dismiss()
            btnSave.isEnabled = false
            progressBar.visibility = View.VISIBLE

            // Jangan dismiss dialog dulu
            uploadImageToFirebase {
                // Upload selesai baru tutup dialog dan sembunyikan progress
                btnSave.isEnabled = true

            }

        }

        dialog.show()
    }


    private fun uploadImageToFirebase(function: () -> Unit) {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid
        val storageRef = storage.reference.child("profile_images/$userId.jpg")
        val progressBar = binding.progressBar
        imageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        savePhotoUrlToFirestore(downloadUri.toString())
                    }
                    progressBar.visibility = View.GONE
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                    Log.e("ProfilePatient", "Error uploading image", e)
                }
        }

    }

    private fun savePhotoUrlToFirestore(photoUrl: String) {
        getActualDoctorUID { patientUserId ->
            if (patientUserId == null) {
                Toast.makeText(requireContext(), "Failed to get patient ID", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            val photoData = hashMapOf(
                "userId" to patientUserId,
                "photoUrl" to photoUrl
            )

            firestore.collection("doctor_photoprofile").document(patientUserId)
                .set(photoData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
                    Glide.with(this).load(photoUrl).circleCrop().into(binding.photoprofile) // Tampilkan langsung
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to update profile picture", Toast.LENGTH_SHORT).show()
                    Log.e("ProfilePatient", "Error saving photo URL", e)
                }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showEditDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val editFullName = dialogView.findViewById<EditText>(R.id.edit_fullname)
        val editAge = dialogView.findViewById<EditText>(R.id.edit_age)
        val spinnerGender = dialogView.findViewById<Spinner>(R.id.spinner_gender)

        // 1. Gunakan layout custom untuk Spinner item
        val genderOptions = listOf("Male", "Female")
        val adapter = ArrayAdapter(context, R.layout.spinner_item, genderOptions)
        adapter.setDropDownViewResource(R.layout.spinner_item) // Bisa diganti dengan dropdown layout sendiri
        spinnerGender.adapter = adapter

        // 2. Ambil data usia dan gender dari TextView yang ada
        val ageGenderText = binding.ageGender.text.toString()
        val agePart = if (ageGenderText.contains("-")) {
            ageGenderText.split("-")[0].trim().replace(Regex("[^0-9]"), "")
        } else ""

        val genderPart = if (ageGenderText.contains("-")) {
            ageGenderText.split("-").getOrNull(1)?.trim() ?: ""
        } else ""

        // 3. Set nilai awal input ke dialog
        editFullName.setText(binding.fullname.text.toString())
        editAge.setText(agePart)

        // 4. Atur posisi Spinner sesuai gender
        val selectedGenderIndex = genderOptions.indexOfFirst {
            it.equals(genderPart, ignoreCase = true)
        }.takeIf { it >= 0 } ?: 0
        spinnerGender.setSelection(selectedGenderIndex)

        // 5. Buat dan tampilkan AlertDialog
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newFullName = editFullName.text.toString().trim()
                val newAge = editAge.text.toString().trim()
                val newGender = spinnerGender.selectedItem.toString()

                if (newFullName.isEmpty() || newAge.isEmpty() || newGender.isEmpty()) {
                    Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateUserData(newFullName, newAge, newGender)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(resources.getColor(android.R.color.white, null))
            negativeButton.setTextColor(resources.getColor(android.R.color.white, null))
        }

        dialog.show()
    }






    private fun updateUserData(fullName: String, age: String, gender: String) {
        val currentUser = auth.currentUser ?: return

        val userEmail = currentUser.email
        val userPhone = currentUser.phoneNumber

        val collectionName = if (!userEmail.isNullOrEmpty()) {
            "users_doctor_email"
        } else if (!userPhone.isNullOrEmpty()) {
            "users_doctor_phonenumber"
        } else {
            return
        }

        documentId?.let { docId ->
            firestore.collection(collectionName).document(docId)
                .update(
                    mapOf(
                        "fullName" to fullName,
                        "age" to age,
                        "gender" to gender
                    )
                )
                .addOnSuccessListener {
                    binding.fullname.text = fullName
                    binding.ageGender.text = "$age years - $gender"
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    Log.d("ProfilePatient", "User Data Updated: $fullName, $age, $gender")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
                    Log.e("ProfilePatient", "Error updating user data", e)
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}