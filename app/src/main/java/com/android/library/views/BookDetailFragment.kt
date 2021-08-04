
package com.android.library.views

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.android.library.R
import com.android.library.adapters.CommentRecyclerAdapter
import com.android.library.databinding.FragmentBookDetailBinding
import com.android.library.models.Book
import com.android.library.models.CommentData
import com.android.library.models.ReferenceSingleton.docRef
import com.bumptech.glide.Glide
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.*
import kotlin.collections.HashMap

class BookDetailFragment : Fragment(), FirebaseAuth.AuthStateListener {
    private var book = Book()
    private var user: FirebaseUser? = null
    private var binding: FragmentBookDetailBinding? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    private var isBookmarked = false
    private var readBookBottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>? = null
    private var comment = ""
    private val commentData = CommentData()
    private var isCommentButtonClicked = false // this will check if button to add comment is clicked, if clicked add comment directly after login
    private var isCommentPosted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentBookDetailBinding.inflate(layoutInflater, container, false)
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            verifyAndProceed()
        }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        getData()

        readBookBottomSheetBehavior = BottomSheetBehavior.from(binding?.bookBottomSheetDialog!!)
        readBookBottomSheetBehavior?.isDraggable = false

        binding?.closeBookBtn?.setOnClickListener {
            readBookBottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding?.readBookBtn?.setOnClickListener {
            if (user != null){
                if ("password" == user!!.providerData[1].providerId) {
                    if (user!!.isEmailVerified) {
                        // Read Book
                        readBook()
                        return@setOnClickListener
                    }
                    loginAndRegister()
                }
                else{
                    readBook()
                }
            }else{
                loginAndRegister()
            }
        }

        user?.photoUrl?.let{
            Glide.with(requireContext())
                .load(it)
                .into(binding?.profilePic!!)
        }

        val commentBottomSheetBehavior = BottomSheetBehavior.from(binding?.commentBottomSheetDialog!!)
        commentBottomSheetBehavior.isDraggable = false

        binding?.commentBookBtn?.setOnClickListener {
            commentBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            getComments()
        }

        binding?.closeCommentBtn?.setOnClickListener {
            commentBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding?.bookmarkBtn?.setOnClickListener {
            if (isBookmarked){
                deleteFromBookmark()
            }else{
                saveToBookmark()
            }
        }

        binding?.commentEditText?.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                comment = s?.toString()!!
                if (comment.isNotBlank()){
                    binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_enable)
                    binding?.addCommentBtn?.isEnabled = true
                }else{
                    binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_disable)
                    binding?.addCommentBtn?.isEnabled = false
                }
            }

        })

        binding?.addCommentBtn?.setOnClickListener {
            isCommentPosted = true
            commentData.comment = comment

            if (user != null){
                if ("password" == user!!.providerData[1].providerId) {
                    if (user!!.isEmailVerified) {
                        // Add Comment
                        checkForUser()
                        binding?.commentEditText?.text = null
                        binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_disable)
                        hideKeyboard()
                        return@setOnClickListener
                    }
                    loginAndRegister()
                    isCommentButtonClicked = true
                }
                else{
                    checkForUser()
                    binding?.commentEditText?.text = null
                    binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_disable)
                    hideKeyboard()
                }
            }else{
                loginAndRegister()
                isCommentButtonClicked = true
            }
        }

    }

    private fun readBook(){
        readBookBottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

        FirebaseStorage.getInstance().reference
            .child(book.filePath)
            .stream.addOnSuccessListener {
            binding?.readBookView?.fromStream(it.stream)
                ?.swipeHorizontal(true)
                ?.onLoad {
                    binding?.bookProgressBar?.visibility = View.GONE
                }
                ?.onError{
                    Snackbar.make(binding?.root!!, "Failed to load", Snackbar.LENGTH_LONG)
                        .setAction("Retry"){
                            readBook()
                        }
                        .show()
                }
                ?.load()
        }
    }

    private fun checkIfBookmarked() {
        docRef?.collection("Bookmarks")
            ?.whereEqualTo("userId", user?.uid)
            ?.get()
            ?.addOnSuccessListener {
                if (!it.isEmpty){
                    isBookmarked = true
                    binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_solid)
                }
            }
    }

    private fun saveToBookmark(){
        val userData = HashMap<String, Any>()
        userData["userId"] = user?.uid!!
        docRef?.collection("Bookmarks")
            ?.add(userData)
            ?.addOnSuccessListener {
                isBookmarked = true
                binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_solid)
                Snackbar.make(binding?.root!!, "Added To Bookmark", Snackbar.LENGTH_SHORT)
                    .setAction("Undo"){
                        deleteFromBookmark()
                    }
                    .show()
            }
            ?.addOnFailureListener{
                isBookmarked = false
                binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_holo)
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        saveToBookmark()
                    }
                    .show()
            }
    }

    private fun deleteFromBookmark(){
        docRef?.collection("Bookmarks")
            ?.whereEqualTo("userId", user?.uid)
            ?.get()
            ?.addOnSuccessListener {
                it.documents[0].reference.delete()
                    .addOnSuccessListener {
                        binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_holo)
                        isBookmarked = false
                        Snackbar.make(binding?.root!!, "Removed From Bookmark", Snackbar.LENGTH_SHORT)
                            .setAction("Add Again"){
                                saveToBookmark()
                            }
                            .show()
                    }
                    .addOnFailureListener{
                        binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_solid)
                        isBookmarked = true
                        Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                            .setAction("Retry"){
                                deleteFromBookmark()
                            }
                            .show()
                    }
            }
            ?.addOnFailureListener{
                binding?.bookmarkBtn?.setImageResource(R.drawable.bookmark_solid)
                isBookmarked = true
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        deleteFromBookmark()
                    }
                    .show()
            }
    }

     private fun getData(){
        FirebaseFirestore.getInstance().collection("Library")
            .document(docRef?.id!!)
            .get()
            .addOnSuccessListener {
                book.bookName = it.getString("bookName")!!
                book.authorNPublisher = it.getString("authorNPublisher")!!
                book.description = it.getString("description")!!
                book.filePath = it.getString("filePath")!!
                book.imageUri = it.getString("imageUri")!!

                binding?.bookLoadingProgressBar?.visibility = View.GONE
                binding?.scrollView2?.visibility = View.VISIBLE
                setData()
            }
            .addOnFailureListener{
                Snackbar.make(binding?.root!!, "Failed to load", Snackbar.LENGTH_LONG)
                    .setAction("Retry"){
                        getData()
                    }
                    .show()
            }
    }

    private fun setData(){
        if (!(activity as Activity).isFinishing) {
            val imageUri = Uri.parse(book.imageUri)
            Glide.with(requireContext())
                .load(imageUri)
                .into(binding?.bookImageView!!)
        }

        binding?.bookName?.text = book.bookName
        binding?.authorNPublisher?.text = book.authorNPublisher
        binding?.description?.text = book.description
        binding?.currentReadingBook?.text = book.bookName
    }

    private fun saveComments(){
        commentData.added = Timestamp(Date())
        user?.let {
            commentData.userId = it.uid

            docRef?.collection("Comments")
                ?.add(commentData)
        }
    }

    private fun getComments(){
        val query = docRef?.collection("Comments")
            ?.orderBy("added", Query.Direction.DESCENDING)

        val commentOption = FirestoreRecyclerOptions.Builder<CommentData>()
            .setLifecycleOwner(this)
            .setQuery(query!!, CommentData::class.java)
            .build()

        val adapter = CommentRecyclerAdapter(requireContext(), binding, commentOption, binding?.commentRecyclerView!!)
        binding?.commentRecyclerView?.adapter = adapter

    }

    private fun loginAndRegister(){
        val providers = arrayListOf(
            AuthUI.IdpConfig.FacebookBuilder().build(),
            AuthUI.IdpConfig.EmailBuilder().build()
        )

        val intent = AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()

        resultLauncher?.launch(intent)
    }

    private fun verifyAndProceed(){
        user?.let {
            if ("password" == it.providerData[1].providerId){

                if (!it.isEmailVerified){
                    user!!.sendEmailVerification()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "A link has sent for verification!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(),"Issue in sending link", Toast.LENGTH_SHORT).show()
                        }
                }
                else{
                    checkForUser()
                    if (isCommentButtonClicked){
                        binding?.commentEditText?.text = null
                        binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_disable)
                        hideKeyboard()
                        return@verifyAndProceed
                    }
                    //Read book
                    readBook()
                }

                return@verifyAndProceed
            }

            checkForUser()
            if (isCommentButtonClicked){
                binding?.commentEditText?.text = null
                binding?.addCommentBtn?.setImageResource(R.drawable.post_logo_disable)
                hideKeyboard()
                return@verifyAndProceed
            }
            // Read book
            readBook()
        }

    }

    private fun checkForUser(){
        FirebaseFirestore.getInstance().collection("Users")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener {
                if (it.isEmpty) {
                    addUserToFirestore()
                }else{
                    saveComments()
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
                if (isCommentPosted){
                    saveComments()
                }
            }
    }

    override fun onAuthStateChanged(p0: FirebaseAuth) {
        user = p0.currentUser
        if (user != null){
            binding?.bookmarkCardView?.visibility = View.VISIBLE
            checkIfBookmarked()
        }
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(this)
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener (this)
    }

    private fun hideKeyboard(){
        val v = activity?.currentFocus
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v!!.windowToken, 0)
    }
}