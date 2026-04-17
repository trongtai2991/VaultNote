package com.simplifynowsoftware.voicenote.manager

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ModelSelectionAdapter(private val onModelSelected: (OpenRouterModel) -> Unit) :
    ListAdapter<OpenRouterModel, ModelSelectionAdapter.ViewHolder>(ModelDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(android.R.id.text1)
        val idText: TextView = view.findViewById(android.R.id.text2)
        // Trong thực tế, bạn nên dùng một layout tùy chỉnh, nhưng ở đây dùng tạm simple_list_item_2 
        // và thêm một TextView cho giá tiền nếu dùng layout custom.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Sử dụng layout có sẵn của Android cho nhanh, hoặc bạn có thể tạo layout riêng
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = getItem(position)
        holder.nameText.text = model.name
        holder.nameText.setTextColor(Color.WHITE)
        
        val pricingInfo = model.pricing?.let {
            "\nInput: ${it.prompt.toPricePerMillion()} | Output: ${it.completion.toPricePerMillion()}"
        } ?: ""
        
        holder.idText.text = "${model.id}$pricingInfo"
        holder.idText.setTextColor(Color.LTGRAY)
        
        holder.itemView.setOnClickListener { onModelSelected(model) }
    }

    class ModelDiffCallback : DiffUtil.ItemCallback<OpenRouterModel>() {
        override fun areItemsTheSame(oldItem: OpenRouterModel, newItem: OpenRouterModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OpenRouterModel, newItem: OpenRouterModel) = oldItem == newItem
    }
}
