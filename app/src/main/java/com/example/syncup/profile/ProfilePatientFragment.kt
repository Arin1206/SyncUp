package com.example.syncup.profile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.databinding.FragmentProfilePatientBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfilePatientFragment : Fragment() {

    private var _binding: FragmentProfilePatientBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var documentId: String? = null // Simpan ID dokumen untuk update Firestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilePatientBinding.inflate(inflater, container, false)
        val view = binding.root

        fetchUserData()

        // Tambahkan fungsi edit ketika tombol edit ditekan
        binding.edit.setOnClickListener {
            showEditDialog()
        }

        return view
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email
        val userPhone = currentUser.phoneNumber

        val query = if (!userEmail.isNullOrEmpty()) {
            firestore.collection("users_patient_email").whereEqualTo("email", userEmail)
        } else if (!userPhone.isNullOrEmpty()) {
            firestore.collection("users_patient_phonenumber").whereEqualTo("phonenumber", userPhone)
        } else {
            Toast.makeText(requireContext(), "No email or phone found", Toast.LENGTH_SHORT).show()
            return
        }

        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val document = documents.documents[0]
                documentId = document.id // Simpan ID dokumen untuk update nanti
                val fullName = document.getString("fullName") ?: "Unknown Name"
                val age = document.getString("age") ?: "Unknown Age"
                val gender = document.getString("gender") ?: "Unknown Gender"

                binding.fullname.text = fullName
                binding.ageGender.text = "$age years - $gender"

                Log.d("ProfilePatient", "User Data Loaded: $fullName, $age, $gender")
            } else {
                Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Log.e("ProfilePatient", "Error fetching user data", e)
            Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showEditDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val editFullName = dialogView.findViewById<EditText>(R.id.edit_fullname)
        val editAge = dialogView.findViewById<EditText>(R.id.edit_age)
        val editGender = dialogView.findViewById<EditText>(R.id.edit_gender)

        // Ambil teks usia dari UI dan pastikan hanya angka
        val ageGenderText = binding.ageGender.text.toString()
        val agePart = if (ageGenderText.contains("-")) {
            ageGenderText.split("-")[0].trim().replace(Regex("[^0-9]"), "")
        } else ""

        val genderPart = if (ageGenderText.contains("-")) {
            ageGenderText.split("-").getOrNull(1)?.trim() ?: ""
        } else ""

        // Set nilai awal pada dialog edit
        editFullName.setText(binding.fullname.text.toString())
        editAge.setText(agePart)
        editGender.setText(genderPart)

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newFullName = editFullName.text.toString().trim()
                val newAge = editAge.text.toString().trim()
                val newGender = editGender.text.toString().trim()

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

            // Atur warna teks tombol menjadi putih
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
            "users_patient_email"
        } else if (!userPhone.isNullOrEmpty()) {
            "users_patient_phonenumber"
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
