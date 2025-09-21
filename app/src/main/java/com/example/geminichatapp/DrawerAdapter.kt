package com.example.geminichatapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.geminichatapp.databinding.ItemDrawerProjectBinding
import com.example.geminichatapp.databinding.ItemDrawerSessionBinding

class DrawerAdapter(
    private var items: List<DrawerItem>,
    private val listener: (DrawerAction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PROJECT_HEADER = 0
        private const val TYPE_SESSION_ITEM = 1
        private const val TYPE_ACTION = 2
    }

    fun updateItems(newItems: List<DrawerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.ProjectHeader -> TYPE_PROJECT_HEADER
            is DrawerItem.SessionItem -> TYPE_SESSION_ITEM
            is DrawerItem.NewProjectAction, is DrawerItem.NewStandaloneChatAction -> TYPE_ACTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PROJECT_HEADER -> ProjectViewHolder(ItemDrawerProjectBinding.inflate(inflater, parent, false))
            TYPE_SESSION_ITEM -> SessionViewHolder(ItemDrawerSessionBinding.inflate(inflater, parent, false))
            TYPE_ACTION -> ActionViewHolder(ItemDrawerSessionBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DrawerItem.ProjectHeader -> (holder as ProjectViewHolder).bind(item, listener)
            is DrawerItem.SessionItem -> (holder as SessionViewHolder).bind(item, listener)
            is DrawerItem.NewProjectAction -> (holder as ActionViewHolder).bind("Create New Project", R.drawable.ic_add, DrawerAction.NewProjectClick, listener)
            is DrawerItem.NewStandaloneChatAction -> (holder as ActionViewHolder).bind("New Standalone Chat", android.R.drawable.ic_menu_add, DrawerAction.NewStandaloneChatClick, listener)
        }
    }

    override fun getItemCount(): Int = items.size

    class ProjectViewHolder(private val binding: ItemDrawerProjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DrawerItem.ProjectHeader, listener: (DrawerAction) -> Unit) {
            binding.projectNameTextView.text = item.project.name
            binding.addChatInProjectButton.setOnClickListener {
                listener(DrawerAction.AddChatInProjectClick(item.project.id))
            }
            binding.editProjectButton.setOnClickListener {
                listener(DrawerAction.EditProjectClick(item.project))
            }
        }
    }

    class SessionViewHolder(private val binding: ItemDrawerSessionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DrawerItem.SessionItem, listener: (DrawerAction) -> Unit) {
            binding.sessionTitleTextView.text = item.session.title
            binding.sessionTitleTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_chat, 0, 0, 0)
            itemView.setOnClickListener { listener(DrawerAction.SessionClick(item.session.id)) }
        }
    }

    class ActionViewHolder(private val binding: ItemDrawerSessionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String, iconRes: Int, action: DrawerAction, listener: (DrawerAction) -> Unit) {
            binding.sessionTitleTextView.text = title
            binding.sessionTitleTextView.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
            itemView.setOnClickListener { listener(action) }
        }
    }
}