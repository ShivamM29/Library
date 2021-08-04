package com.android.library.views

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.android.library.R
import com.android.library.adapters.BookRecyclerAdapter
import com.android.library.databinding.FragmentBookStoreBinding
import com.android.library.models.Book
import com.android.library.models.ReferenceSingleton.docRef
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

class BookStoreFragment : Fragment() , FirebaseAuth.AuthStateListener, BookRecyclerAdapter.OnClickEvent{
    private var navController: NavController? = null
    private var binding: FragmentBookStoreBinding? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    private var user: FirebaseUser? = null
    private var recyclerAdapter: BookRecyclerAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentBookStoreBinding.inflate(inflater, container, false)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            verifyAndProceed()
        }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        navController = Navigation.findNavController(view)

        binding?.addBookBtn?.setOnClickListener{
            if (user != null){
                if ("password" == user!!.providerData[1].providerId){
                    if (user!!.isEmailVerified) {
                        navController?.navigate(R.id.action_bookStoreFragment_to_addBookFragment)
                    }else{
                        loginAndRegister()
                    }
                }else{
                    navController?.navigate(R.id.action_bookStoreFragment_to_addBookFragment)
                }
            }else{
                loginAndRegister()
            }
        }

        val query = FirebaseFirestore.getInstance().collection("Library")

        val option = FirestoreRecyclerOptions.Builder<Book>()
            .setLifecycleOwner(this)
            .setQuery(query, Book::class.java)
            .build()

        recyclerAdapter = BookRecyclerAdapter(requireContext(),binding, option, this)
        binding?.bookRecyclerView?.adapter = recyclerAdapter
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

    private fun verifyAndProceed() {
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
                    navController?.navigate(R.id.action_bookStoreFragment_to_addBookFragment)
                }

                return@verifyAndProceed
            }

            checkForUser()
            navController?.navigate(R.id.action_bookStoreFragment_to_addBookFragment)
        }

    }

    private fun checkForUser(){
        FirebaseFirestore.getInstance().collection("Users")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener {
                if (it.isEmpty) {
                    addUserToFirestore()
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
    }

    override fun onAuthStateChanged(p0: FirebaseAuth) {
        user = p0.currentUser
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(this)
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener (this)
    }

    override fun onBookClicked(ref: DocumentReference) {
        docRef = ref
        navController?.navigate(R.id.action_bookStoreFragment_to_bookDetailFragment)
    }

    override fun onBookMarkClicked(ref: DocumentReference, bookmarkBtn: ImageButton) {
        checkIfBookmarked(ref, bookmarkBtn)
    }

    private fun checkIfBookmarked(ref: DocumentReference, bookmarkBtn: ImageButton) {
        ref.collection("Bookmarks")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener {
                if (it.isEmpty){
                    saveToBookmark(ref, bookmarkBtn)
                    bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                }else{
                    deleteFromBookmark(ref, bookmarkBtn)
                    bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                }
            }
    }

    private fun saveToBookmark(ref: DocumentReference, bookmarkBtn: ImageButton){
        val userData = HashMap<String, Any>()
        userData["userId"] = user?.uid!!
        ref.collection("Bookmarks")
            .add(userData)
            .addOnSuccessListener {
                Snackbar.make(binding?.root!!, "Added To Bookmark", Snackbar.LENGTH_SHORT)
                    .setAction("Undo"){
                        deleteFromBookmark(ref, bookmarkBtn)
                        bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                    }
                    .show()
            }
            .addOnFailureListener{
                bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        saveToBookmark(ref, bookmarkBtn)
                        bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                    }
                    .show()
            }
    }

    private fun deleteFromBookmark(ref: DocumentReference, bookmarkBtn: ImageButton){
        ref.collection("Bookmarks")
            .whereEqualTo("userId", user?.uid)
            .get()
            .addOnSuccessListener {
                it.documents[0].reference.delete()
                    .addOnSuccessListener {
                        Snackbar.make(binding?.root!!, "Removed From Bookmark", Snackbar.LENGTH_SHORT)
                            .setAction("Add Again"){
                                saveToBookmark(ref, bookmarkBtn)
                                bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                            }
                            .show()
                    }
                    .addOnFailureListener{
                        bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                        Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                            .setAction("Retry"){
                                deleteFromBookmark(ref, bookmarkBtn)
                                bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                            }
                            .show()
                    }
            }
            .addOnFailureListener{
                bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                Snackbar.make(binding?.root!!, "Oops! Something went wrong", Snackbar.LENGTH_SHORT)
                    .setAction("Retry"){
                        deleteFromBookmark(ref, bookmarkBtn)
                        bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                    }
                    .show()
            }
    }

    override fun onResume() {
        super.onResume()
        activity?.invalidateOptionsMenu()
    }
}