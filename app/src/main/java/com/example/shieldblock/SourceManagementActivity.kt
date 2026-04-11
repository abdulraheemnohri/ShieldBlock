package com.example.shieldblock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.FilterSource
import com.example.shieldblock.databinding.ActivitySourceManagementBinding
import com.example.shieldblock.databinding.DialogAddSourceBinding
import com.example.shieldblock.databinding.ItemFilterSourceBinding
import java.io.File
import java.io.FileOutputStream

class SourceManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySourceManagementBinding
    private val filterManager by lazy { FilterManager(this) }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleUploadedFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySourceManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.sourceRecyclerView.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.addSourceBtn.setOnClickListener { showAddSourceDialog() }
        binding.uploadFileBtn.setOnClickListener { filePicker.launch("text/*") }

        binding.selectAllBtn.setOnClickListener { bulkToggle(true) }
        binding.selectNoneBtn.setOnClickListener { bulkToggle(false) }
    }

    private fun bulkToggle(enabled: Boolean) {
        val all = filterManager.getAllSources()
        all.forEach { filterManager.setFilterEnabled(it.id, enabled) }
        refreshList()
    }

    private fun refreshList() {
        val all = filterManager.getAllSources()
        binding.sourceRecyclerView.adapter = SourceAdapter(all,
            onToggle = { id, enabled -> filterManager.setFilterEnabled(id, enabled) },
            onDelete = { id ->
                filterManager.removeCustomSource(id)
                refreshList()
            }
        )
    }

    private fun showAddSourceDialog() {
        val dialogBinding = DialogAddSourceBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Add List URL")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.sourceNameEditText.text.toString()
                val url = dialogBinding.sourceUrlEditText.text.toString()
                if (name.isNotBlank() && url.isNotBlank()) {
                    filterManager.addCustomSource(name, url)
                    refreshList()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun handleUploadedFile(uri: Uri) {
        try {
            val fileName = "local_${System.currentTimeMillis()}.txt"
            val file = File(filesDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            filterManager.addCustomSource("Uploaded: ${uri.lastPathSegment}", file.absolutePath, "LOCAL")
            refreshList()
            Toast.makeText(this, "File uploaded successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    class SourceAdapter(
        private val items: List<FilterSource>,
        val onToggle: (String, Boolean) -> Unit,
        val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemFilterSourceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFilterSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.sourceName.text = item.name
            val typeStr = if (item.type == "LOCAL") "Local File" else item.url
            holder.binding.sourceDetails.text = "${item.domainCount} domains • $typeStr"
            holder.binding.categoryChip.text = item.category

            holder.binding.sourceSwitch.setOnCheckedChangeListener(null)
            holder.binding.sourceSwitch.isChecked = item.enabled
            holder.binding.sourceSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }

            holder.itemView.setOnLongClickListener {
                if (item.id.startsWith("custom_")) {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Remove Source?")
                        .setMessage("Delete ${item.name}?")
                        .setPositiveButton("Remove") { _, _ -> onDelete(item.id) }
                        .setNegativeButton("Cancel", null).show()
                }
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
