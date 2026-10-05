package com.kibu.notes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kibu.notes.data.models.TrendPost

class TrendsAdapter(
    private var posts: List<TrendPost> = emptyList(),
    private val onLikeClicked: ((TrendPost) -> Unit)? = null,
    private val onShareClicked: ((TrendPost) -> Unit)? = null
) : RecyclerView.Adapter<TrendsAdapter.TrendViewHolder>() {

    fun submitList(newPosts: List<TrendPost>) {
        posts = newPosts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trend_post, parent, false)
        return TrendViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrendViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    inner class TrendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val postAvatar: TextView = itemView.findViewById(R.id.postAvatar)
        private val postAuthor: TextView = itemView.findViewById(R.id.postAuthor)
        private val postHandle: TextView = itemView.findViewById(R.id.postHandle)
        private val postTime: TextView = itemView.findViewById(R.id.postTime)
        private val postCategory: TextView = itemView.findViewById(R.id.postCategory)
        private val postContent: TextView = itemView.findViewById(R.id.postContent)
        private val likeButton: TextView = itemView.findViewById(R.id.likeButton)
        private val commentButton: TextView = itemView.findViewById(R.id.commentButton)
        private val shareButton: TextView = itemView.findViewById(R.id.shareButton)

        fun bind(post: TrendPost) {
            postAvatar.text = post.authorName.take(1).uppercase()
            postAuthor.text = post.authorName
            postHandle.text = if (post.authorHandle.startsWith("@")) post.authorHandle else "@${post.authorHandle}"
            postTime.text = post.timeAgo
            postCategory.text = post.category
            postContent.text = post.content

            val heartSymbol = if (post.isLiked) "♥" else "♡"
            likeButton.text = "$heartSymbol ${post.likesCount}"
            commentButton.text = "💬 ${post.commentsCount}"

            likeButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    post.isLiked = !post.isLiked
                    if (post.isLiked) post.likesCount++ else post.likesCount--
                    notifyItemChanged(pos)
                    onLikeClicked?.invoke(post)
                }
            }

            shareButton.setOnClickListener {
                onShareClicked?.invoke(post)
            }
        }
    }
}
