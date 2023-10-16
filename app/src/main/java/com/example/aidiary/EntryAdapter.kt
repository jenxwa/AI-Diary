package com.example.aidiary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aidiary.databinding.ViewEntryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiaryEntry(val date: String, val content: String)

class EntryAdapter(private val entries: MutableList<DiaryEntry>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onEntryButtonClickListener: ((Int, String) -> Unit)? = null
    private val ENTRY_VIEW_TYPE = 1
    private val INPUT_VIEW_TYPE = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ViewEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return when (viewType) {
            INPUT_VIEW_TYPE -> EntryInputViewHolder(binding)
            else -> EntryDisplayViewHolder(binding)
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemViewType(position: Int): Int {
        val entry = entries[position]
        return if (entry.content.isEmpty() && entry.date == getCurrentDate()) INPUT_VIEW_TYPE else ENTRY_VIEW_TYPE
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = entries[position]
        if (holder is EntryInputViewHolder) {
            holder.bind(entry)
        } else if (holder is EntryDisplayViewHolder) {
            holder.bind(entry)
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        return dateFormat.format(Date())
    }

    fun setOnEntryButtonClickListener(listener: (Int, String) -> Unit) {
        onEntryButtonClickListener = listener
    }

    inner class EntryInputViewHolder(private val binding: ViewEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: DiaryEntry) {
            binding.txvTodaysDate.text = entry.date
            binding.materialButton.setOnClickListener {
                val text = binding.inputTextField.text.toString()
                if (text.isNotEmpty()) {
                    entries[position] = DiaryEntry(entry.date, text)
                    notifyItemChanged(position)
                    onEntryButtonClickListener?.invoke(adapterPosition, text)
                }
            }
        }
    }

    inner class EntryDisplayViewHolder(private val binding: ViewEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: DiaryEntry) {
            binding.txvTodaysDate.text = entry.date
            binding.textViewContent.text = entry.content
        }
    }
}
