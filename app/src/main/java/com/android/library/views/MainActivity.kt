package com.android.library.views

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.android.library.R
import com.bumptech.glide.Glide
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity(), FirebaseAuth.AuthStateListener{
    private var navController: NavController? = null
    private var user: FirebaseUser? = null
    private var profilePic : ImageView? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null
    private var mItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            verifyAndProceed()
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container) as NavHostFragment
        navController = navHostFragment.navController
        NavigationUI.setupActionBarWithNavController(this, navController!!)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val menuItem = menu?.findItem(R.id.profileFragment)
        mItem = menuItem
        val actionView = menuItem?.actionView

        profilePic = actionView?.findViewById(R.id.profilePic)
        user?.let {
            if (user?.photoUrl != null){
                Glide.with(this)
                    .load(user?.photoUrl)
                    .into(profilePic!!)
            }
        }

        actionView?.setOnClickListener {
            if (user != null) {
                onOptionsItemSelected(menuItem)
            }else{
                loginAndRegister()
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (user != null) {
            navController?.let {
                return NavigationUI.onNavDestinationSelected(item, it)
            }
        }
        return super.onOptionsItemSelected(item)
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
                            Toast.makeText(this, "A link has sent for verification!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this,"Issue in sending link", Toast.LENGTH_SHORT).show()
                        }
                }
                else{
                    checkForUser()
                    if (mItem != null) {
                        onOptionsItemSelected(mItem!!)
                    }
                }

                return@verifyAndProceed
            }
            checkForUser()
            if (mItem != null) {
                onOptionsItemSelected(mItem!!)
            }
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

    override fun onSupportNavigateUp(): Boolean {
        navController?.navigateUp()
        return super.onSupportNavigateUp()
    }

    override fun onAuthStateChanged(p0: FirebaseAuth) {
        user = p0.currentUser
        invalidateOptionsMenu()
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(this)
        user?.let {
            checkForUser()
        }
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener (this)
    }
}