package com.android.library.views

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.library.databinding.FragmentProfileBinding
import com.bumptech.glide.Glide
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment() {
    private var binding: FragmentProfileBinding? = null
    private var permissionResult: ActivityResultLauncher<String>? = null
    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null
    private var user: FirebaseUser? = null
    private var imageUri: Uri? = null
    private var userUpdatedToFirestore = false
    private var imageUpdatedToStorage = false
    private var nameChanged = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentProfileBinding.inflate(inflater, container, false)

        (activity as AppCompatActivity).supportActionBar?.hide()

        user = FirebaseAuth.getInstance().currentUser

        permissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it){
                openGallery()
            }
        }

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == Activity.RESULT_OK){
                val inputStream = activity?.contentResolver?.openInputStream(it.data?.data!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos)

                imageUri = it.data?.data

                Glide.with(requireContext())
                    .load(baos.toByteArray())
                    .into(binding?.profileImageView!!)
            }
        }


        if (user?.photoUrl != null){
            Glide.with(requireContext())
                .load(user?.photoUrl)
                .into(binding?.profileImageView!!)
        }

        if (user?.displayName.isNullOrBlank()){
            binding?.profileNameEditText?.text = Editable.Factory.getInstance().newEditable("Unknown")
            binding?.profileNameEditText?.setSelection("Unknown".length)
        }else{
            binding?.profileNameEditText?.text = Editable.Factory.getInstance().newEditable(user?.displayName)
            binding?.profileNameEditText?.setSelection(user?.displayName?.length!!)
        }

        binding?.addImageBtn?.setOnClickListener {
            checkPermission()
        }

        binding?.profileUpdateBtn?.setOnClickListener {
            it.isEnabled = false
            binding?.updateProgressBar?.visibility = View.VISIBLE
            val name = binding?.profileNameEditText?.text.toString()
            if ((name == "Unknown" || name == user?.displayName) && imageUri == null){
                activity?.onBackPressed()
            }else{
                updateProfile()
            }
        }

        binding?.logoutBtn?.setOnClickListener {
            signOut()
        }

        return binding?.root
    }

    private fun checkPermission(){
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            openGallery()
        }else{
            permissionResult?.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openGallery(){
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        activityResultLauncher?.launch(intent)
    }

    private fun updateProfile(){
        val name = binding?.profileNameEditText?.text.toString()
        val request = UserProfileChangeRequest.Builder()

        imageUri?.let {
            request.photoUri = it
        }

        if (name != "Unknown" && name != user?.displayName){
            request.displayName = name
            nameChanged = true
        }

        user?.updateProfile(request.build())
            ?.addOnSuccessListener {

                if (imageUri != null) {
                    updatePhotoToStorage(imageUri!!)
                }else{
                    imageUpdatedToStorage = true
                }

                if (nameChanged){
                    updateUserToFirestore()
                }else{
                    userUpdatedToFirestore = true
                }
            }
            ?.addOnFailureListener{
                binding?.updateProgressBar?.visibility = View.GONE
                binding?.profileUpdateBtn?.isEnabled = true
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        updateProfile()
                        binding?.updateProgressBar?.visibility = View.VISIBLE
                        binding?.profileUpdateBtn?.isEnabled = false
                    }
                    .show()
            }
    }

    private fun updatePhotoToStorage(imageUri: Uri){
        FirebaseStorage.getInstance().reference
            .child("Users")
            .child(user?.uid!! + ".jpeg")
            .putFile(imageUri)
            .addOnSuccessListener {
                imageUpdatedToStorage = true
                if (userUpdatedToFirestore) {
                    activity?.onBackPressed()
                }
            }
            .addOnFailureListener {
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        updatePhotoToStorage(imageUri)
                    }
                    .show()
            }
    }

    private fun updateUserToFirestore(){
        FirebaseFirestore.getInstance().collection("Users")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener {
                if(it.isEmpty){
                    addUserToFirestore()
                }else{
                    val ref = it.documents[0].reference
                    ref.update("userName", user?.displayName)
                        .addOnSuccessListener {
                            userUpdatedToFirestore = true
                            if (imageUpdatedToStorage){
                                activity?.onBackPressed()
                            }
                        }
                        .addOnFailureListener {
                            Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                                .setAction("Retry"){
                                    updateUserToFirestore()
                                }
                                .show()
                        }
                }
            }
    }

    private fun addUserToFirestore(){
        val userData = HashMap<String, String>()
        userData["userId"] = user?.uid!!
        val name = if (user?.displayName.isNullOrBlank()) "Unknown" else user?.displayName

        userData["userName"] = name!!

        FirebaseFirestore.getInstance().collection("Users")
            .add(userData)
            .addOnSuccessListener {
                userUpdatedToFirestore = true
                if (imageUpdatedToStorage){
                    activity?.onBackPressed()
                }
            }
    }

    private fun signOut(){
        AuthUI.getInstance().signOut(requireContext())
            .addOnSuccessListener {
                activity?.onBackPressed()
            }
            .addOnFailureListener {
                Snackbar.make(binding?.root!!, "oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        signOut()
                    }
                    .show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as AppCompatActivity).supportActionBar?.show()
    }
}