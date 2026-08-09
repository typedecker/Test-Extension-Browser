package com.browserextensions.browserwithextensions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.browserextensions.browserwithextensions.databinding.ItemExtensionBinding

class ExtensionsAdapter(
    private val extensions: MutableList<ExtensionsActivity.ExtensionInfo>,
    private val onRemoveClick: (ExtensionsActivity.ExtensionInfo) -> Unit,
    private val onToggleEnabled: (ExtensionsActivity.ExtensionInfo) -> Unit
) : RecyclerView.Adapter<ExtensionsAdapter.ExtensionViewHolder>() {

    class ExtensionViewHolder(private val binding: ItemExtensionBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(
            extension: ExtensionsActivity.ExtensionInfo,
            onRemoveClick: (ExtensionsActivity.ExtensionInfo) -> Unit,
            onToggleEnabled: (ExtensionsActivity.ExtensionInfo) -> Unit
        ) {
            binding.extensionName.text = extension.name
            binding.extensionVersion.text = "v${extension.version}"
            binding.extensionDescription.text = extension.description.ifEmpty { "No description" }
            
            binding.enableSwitch.apply {
                isChecked = extension.enabled
                text = if (extension.enabled) "Enabled" else "Disabled"
                setOnCheckedChangeListener { _, isChecked ->
                    onToggleEnabled(extension.copy(enabled = isChecked))
                }
            }
            
            binding.removeButton.setOnClickListener {
                onRemoveClick(extension)
            }
            
            binding.root.alpha = if (extension.enabled) 1.0f else 0.6f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExtensionViewHolder {
        val binding = ItemExtensionBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ExtensionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExtensionViewHolder, position: Int) {
        holder.bind(extensions[position], onRemoveClick, onToggleEnabled)
    }

    override fun getItemCount(): Int = extensions.size

    fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    fun notifyItemChanged(position: Int) {
        super.notifyItemChanged(position)
    }
}
