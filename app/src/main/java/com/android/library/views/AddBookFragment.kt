package com.android.library.views

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.android.library.models.Book
import com.android.library.R
import com.android.library.databinding.FragmentAddBookBinding
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File

class AddBookFragment : Fragment() {
    private var binding: FragmentAddBookBinding? = null
    private var navController: NavController? = null
    private var permissionRequestCode = 101
    private var callBy = ""
    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionResultLauncher: ActivityResultLauncher<String>? = null
    private var file: Uri? = null
    private var bookImage: ByteArray? = null
    private var imageName: String? = null
    private var fileName: String? = null
    private var book = Book()
    private var user = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentAddBookBinding.inflate(inflater, container, false)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == Activity.RESULT_OK){
                val data = it.data
                when(callBy){
                    "Image Attach" -> {
                        val inputStream = activity?.contentResolver?.openInputStream(data?.data!!)
                        val imageBitmap = BitmapFactory.decodeStream(inputStream)
                        val baos = ByteArrayOutputStream()
                        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos)
                        bookImage = baos.toByteArray()

                        if (data?.data.toString().startsWith("content://")){
                            val cursor = activity?.contentResolver?.query(data?.data!!, null, null, null, null)
                            if (cursor != null && cursor.moveToFirst()){
                                imageName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                            }
                            cursor?.close()
                        }
                        else if (data?.data.toString().startsWith("file://")){
                            imageName = File(data?.data.toString()).name
                        }


                        Glide.with(requireContext())
                            .load(data?.data)
                            .into(binding?.bookImageView!!)
                    }

                    "File Attach" -> {
                        file = data?.data
                        if (data?.data.toString().startsWith("content://")){
                            val cursor = activity?.contentResolver?.query(data?.data!!, null, null, null, null)
                            if (cursor != null && cursor.moveToFirst()){
                                fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                            }
                            cursor?.close()
                        }
                        else if (data?.data.toString().startsWith("file://")){
                            fileName = File(data?.data.toString()).name
                        }

                        binding?.fileName!!.text = fileName

                    }
                }

                if (!isDetailsBlankedTwo()){
                    binding?.finishBtn?.isEnabled = true
                }
            }
        }

        permissionResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it){
                when(callBy){
                    "Image Attach" -> {
                        launchFileExplorerForImage()
                    }

                    "File Attach" -> {
                        launchFileExplorerForFile()
                    }
                }
            }
        }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navController = Navigation.findNavController(view)

        val genre = resources.getStringArray(R.array.Genre)
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, genre)
        binding?.autoCompleteTextView?.setAdapter(arrayAdapter)

        binding?.attachImageBtn?.setOnClickListener {
            callBy = "Image Attach"
            checkPermission()
        }

        binding?.attachFileBtn?.setOnClickListener {
            callBy = "File Attach"
            checkPermission()
        }

        binding?.nextBtn?.setOnClickListener {
            if (!isDetailsBlankedOne()){
                binding?.viewFlipper?.showNext()
            }
        }

        binding?.prevBtn?.setOnClickListener {
            binding?.viewFlipper?.showPrevious()
        }

        binding?.finishBtn?.setOnClickListener {
            if (!isDetailsBlankedTwo()) {
                binding?.finishBtn?.isEnabled = false
                binding?.progressBar?.visibility = View.VISIBLE
                saveFilesInStorage()
            }
        }
    }

    private fun checkPermission(){
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            permissionResultLauncher?.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }else{
            when(callBy){
                "Image Attach" -> {
                    launchFileExplorerForImage()
                }

                "File Attach" -> {
                    launchFileExplorerForFile()
                }
            }
        }
    }

    private fun launchFileExplorerForFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "application/pdf"
        resultLauncher?.launch(intent)
    }

    private fun launchFileExplorerForImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        resultLauncher?.launch(intent)
    }

    private fun isDetailsBlankedOne(): Boolean{
        var isBlank = false

        val name = binding?.bookNameTextField?.editText?.text

        if (name?.toString().isNullOrBlank()){
            isBlank = true
            binding?.bookNameTextField?.error = "This field is empty"
        }

        val authorNPublisher = binding?.authorPublisherTextField?.editText?.text
        if (authorNPublisher.isNullOrBlank()){
            isBlank = true
            binding?.authorPublisherTextField?.error = "This field is empty"
        }

        val description = binding?.bookDescriptionTextField?.editText?.text
        if (description.isNullOrBlank()){
            isBlank = true
            binding?.bookDescriptionTextField?.error = "This field is empty"
        }

        return isBlank
    }

    private fun isDetailsBlankedTwo(): Boolean{
        var isFileBlank = true
        var isImageBlank = true

        file?.let {
            isFileBlank = false
        }
        bookImage?.let {
            isImageBlank = false
        }

        return isFileBlank && isImageBlank
    }

    private fun saveFilesInStorage(){
        val imageStorageReference = FirebaseStorage.getInstance().reference.child("Images")
            .child(user?.uid + "-" + imageName)

        val filePath = "PdfFiles/$user?.uid-$fileName"
        val fileStorageReference = FirebaseStorage.getInstance().reference.child(filePath)


        imageStorageReference.putBytes(bookImage!!)
            .addOnSuccessListener {
                imageStorageReference.downloadUrl
                    .addOnSuccessListener {
                        book.imageUri = it.toString()
                        if (book.filePath.isNotEmpty()){
                            saveFilesInFirestore()
                        }
                    }
            }
            .addOnFailureListener{
                Toast.makeText(requireContext(), "oops! Something went wrong", Toast.LENGTH_SHORT).show()
                if (book.filePath.isNotEmpty()){
                    fileStorageReference.delete()
                        .addOnSuccessListener {
                            binding?.progressBar?.visibility = View.INVISIBLE
                            binding?.finishBtn?.isEnabled = true
                        }
                }
            }

        fileStorageReference.putFile(file!!)
            .addOnSuccessListener {
                book.filePath = filePath
                if (book.imageUri.isNotEmpty()){
                    saveFilesInFirestore()
                }
            }
            .addOnFailureListener{
                Toast.makeText(requireContext(), "oops! Something went wrong", Toast.LENGTH_SHORT).show()
                if (book.imageUri.isNotEmpty()){
                    imageStorageReference.delete()
                        .addOnSuccessListener {
                            binding?.progressBar?.visibility = View.INVISIBLE
                            binding?.finishBtn?.isEnabled = true
                        }
                }
            }
    }

    private fun saveFilesInFirestore(){
        book.bookName = binding?.bookNameTextField?.editText?.text.toString()
        book.authorNPublisher = binding?.authorPublisherTextField?.editText?.text.toString()
        book.description = binding?.bookDescriptionTextField?.editText?.text.toString()
        book.genre = binding?.genreTextField?.editText?.text.toString()
        Firebase.firestore.collection("Library")
            .add(book)
            .addOnSuccessListener {
                activity?.onBackPressed()
            }
            .addOnFailureListener{
                Toast.makeText(requireContext(), "oops! Something went wrong", Toast.LENGTH_SHORT).show()
                binding?.progressBar?.visibility = View.INVISIBLE
                binding?.finishBtn?.isEnabled = true
            }
    }
}