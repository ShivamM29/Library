package com.android.library.adapters

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.library.databinding.FragmentBookDetailBinding
import com.android.library.databinding.SingleCommentRowBinding
import com.android.library.models.CommentData
import com.bumptech.glide.Glide
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class CommentRecyclerAdapter(val context: Context, val mBinding: FragmentBookDetailBinding?,  options: FirestoreRecyclerOptions<CommentData>, private var recyclerView: RecyclerView) : FirestoreRecyclerAdapter<CommentData, CommentRecyclerAdapter.CommentViewHolder>(options){
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SingleCommentRowBinding.inflate(layoutInflater, parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int, model: CommentData) {
        holder.binding.commentTextView.text = model.comment

        FirebaseFirestore.getInstance().collection("Users")
            .whereEqualTo("userId", model.userId)
            .get()
            .addOnSuccessListener {
                val userName = it.documents[0].data!!["userName"]
                holder.binding.commentUserName.text = userName.toString()
            }

        if (model.userId.isNotEmpty()){
            FirebaseStorage.getInstance().reference
                .child("Users")
                .child(model.userId + ".jpeg")
                .downloadUrl
                .addOnSuccessListener {
                    if (!(context as Activity).isFinishing){
                        Glide.with(context)
                            .load(it)
                            .into(holder.binding.profilePic)
                    }
                }
        }
    }

    override fun onDataChanged() {
        mBinding?.commentProgressBar?.visibility = View.GONE

        if (itemCount == 0){
            mBinding?.noCommentTextView?.visibility = View.VISIBLE
        }else{
            mBinding?.noCommentTextView?.visibility = View.GONE
        }

        recyclerView.smoothScrollToPosition(0)
    }

    inner class CommentViewHolder(val binding: SingleCommentRowBinding): RecyclerView.ViewHolder(binding.root)
}