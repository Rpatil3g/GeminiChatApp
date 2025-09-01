package com.example.geminichatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon // <-- Import Markwon

class ChatAdapter(
    private var chatMessages: MutableList<ChatMessage>,
    private val markwon: Markwon // <-- 1. Accept Markwon instance in constructor
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_MODEL = 2
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.messageTextView)
    }

    override fun getItemViewType(position: Int): Int {
        return if (chatMessages[position].role == Role.USER) {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_MODEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_user_message
        } else {
            R.layout.item_model_message
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = chatMessages[position]

        // 2. Use Markwon for MODEL messages, plain text for USER messages
        if (message.role == Role.MODEL) {
            markwon.setMarkdown(holder.messageTextView, message.text)
        } else {
            holder.messageTextView.text = message.text
        }
    }

    override fun getItemCount() = chatMessages.size

    fun submitList(newMessages: List<ChatMessage>) {
        chatMessages.clear()
        chatMessages.addAll(newMessages)
        notifyDataSetChanged()
    }
}