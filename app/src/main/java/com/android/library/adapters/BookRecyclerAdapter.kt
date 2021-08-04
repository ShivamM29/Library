package com.android.library.adapters

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.android.library.R
import com.android.library.databinding.FragmentBookStoreBinding
import com.android.library.databinding.SingleBookRowBinding
import com.android.library.models.Book
import com.bumptech.glide.Glide
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.shockwave.pdfium.PdfDocument

class BookRecyclerAdapter(private val context: Context, val mBinding: FragmentBookStoreBinding?, options: FirestoreRecyclerOptions<Book>, private val onClickEvent: OnClickEvent) : FirestoreRecyclerAdapter<Book, BookRecyclerAdapter.BookRecyclerView>(options) {
    private val user = FirebaseAuth.getInstance().currentUser

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookRecyclerView {
        val layoutInflater = LayoutInflater.from(context)
        val binding = SingleBookRowBinding.inflate(layoutInflater, parent, false)
        return BookRecyclerView(binding)
    }

    override fun onBindViewHolder(holder: BookRecyclerView, position: Int, model: Book) {
        val imageUri = Uri.parse(model.imageUri)
        if (!(context as Activity).isFinishing){
            Glide.with(context)
                .load(imageUri)
                .into(holder.binding.bookImageView)
        }
        holder.binding.bookName.text = model.bookName
        holder.binding.authorNPublisher.text = model.authorNPublisher

        user?.let {
            snapshots.getSnapshot(position).reference
                .collection("Bookmarks")
                .whereEqualTo("userId", it.uid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty){
                        holder.binding.bookmarkBtn.setImageResource(R.drawable.bookmark_holo)
                    }else{
                        holder.binding.bookmarkBtn.setImageResource(R.drawable.bookmark_solid)
                    }
                }
        }

        if (user == null){
            holder.binding.bookmarkBtn.visibility = View.GONE
        }else{
            holder.binding.bookmarkBtn.visibility = View.VISIBLE
        }
    }

    override fun onDataChanged() {
        mBinding?.storeLoadingProgressBar?.visibility = View.GONE
        if (itemCount == 0){
            mBinding?.noBookTextView?.visibility = View.VISIBLE
        }else{
            mBinding?.noBookTextView?.visibility = View.GONE
        }
    }

    inner class BookRecyclerView(val binding: SingleBookRowBinding):RecyclerView.ViewHolder(binding.root){
        init {
            binding.root.setOnClickListener {
                val ref = snapshots.getSnapshot(adapterPosition).reference
                onClickEvent.onBookClicked(ref)
            }

            binding.bookmarkBtn.setOnClickListener {
                val ref = snapshots.getSnapshot(adapterPosition).reference
                val bookmarkBtn = binding.bookmarkBtn
                onClickEvent.onBookMarkClicked(ref, bookmarkBtn)
            }
        }
    }

    interface OnClickEvent{
        fun onBookClicked(ref: DocumentReference)

        fun onBookMarkClicked(ref: DocumentReference, bookmarkBtn:ImageButton)
    }
}