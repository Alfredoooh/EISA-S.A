package com.appao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val list: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object { const val T_USER = 0; const val T_AI = 1; const val T_TYPING = 2 }

    override fun getItemViewType(position: Int): Int = when (list[position]) {
        is ChatMessage.User -> T_USER
        is ChatMessage.Ai -> T_AI
        ChatMessage.Typing -> T_TYPING
    }

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: TextView = v.findViewById(R.id.bubble)
        val time: TextView = v.findViewById(R.id.time)
    }
    class AiVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: TextView = v.findViewById(R.id.bubble)
        val time: TextView = v.findViewById(R.id.time)
    }
    class TypingVH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_USER -> UserVH(inf.inflate(R.layout.item_chat_user, parent, false))
            T_AI -> AiVH(inf.inflate(R.layout.item_chat_ai, parent, false))
            else -> TypingVH(inf.inflate(R.layout.item_chat_typing, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val m = list[position]) {
            is ChatMessage.User -> (h as UserVH).apply {
                bubble.text = m.text
                time.text = m.time
            }
            is ChatMessage.Ai -> (h as AiVH).apply {
                bubble.text = m.text
                time.text = m.time
            }
            else -> {}
        }
    }

    override fun getItemCount() = list.size
}

sealed class ChatMessage {
    data class User(val text: String, val time: String) : ChatMessage()
    data class Ai(val text: String, val time: String) : ChatMessage()
    data object Typing : ChatMessage()
}
