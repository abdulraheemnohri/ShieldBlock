package com.example.shieldblock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.data.FilterSource
import com.example.shieldblock.databinding.ActivitySourceManagementBinding
import com.example.shieldblock.databinding.DialogAddSourceBinding
import com.example.shieldblock.databinding.ItemFilterSourceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        binding.sourceRecyclerView.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.addSourceBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddSourceDialog()
        }
        binding.uploadFileBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            filePicker.launch("text/*")
        }

        binding.selectAllBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bulkToggle(true)
        }
        binding.selectNoneBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bulkToggle(false)
        }
        binding.resetSourcesBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_msg)
                .setPositiveButton(R.string.reset) { _, _ ->
                    filterManager.resetToDefaults()
                    refreshList()
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_apps -> { startActivity(Intent(this, AppExclusionActivity::class.java)); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); finish(); true }
                R.id.nav_settings -> true
                else -> false
            }
        }
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
            },
            onEdit = { source -> showAddSourceDialog(source) },
            onSync = { source -> syncSource() },
            onPreview = { source -> showPreviewDialog(source) }
        )
    }

    private fun syncSource() {
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "blacklist_update_manual",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<BlacklistWorker>().build()
        )
        Toast.makeText(this, R.string.syncing_sources, Toast.LENGTH_SHORT).show()
    }

    private fun showPreviewDialog(source: FilterSource) {
        val msg = "Downloading preview..."
        val dialog = AlertDialog.Builder(this)
            .setTitle(source.name)
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    if (source.type == "LOCAL") {
                        val file = File(source.url)
                        if (file.exists()) file.readLines().take(50).joinToString("\n") else "File not found"
                    } else {
                        val client = OkHttpClient()
                        val request = Request.Builder().url(source.url).build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            response.body?.string()?.lines()?.take(50)?.joinToString("\n") ?: "Empty response"
                        } else "Failed to fetch content"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            dialog.setMessage(content)
        }
    }

    private fun showAddSourceDialog(existing: FilterSource? = null) {
        val dialogBinding = DialogAddSourceBinding.inflate(layoutInflater)
        existing?.let {
            dialogBinding.sourceNameEditText.setText(it.name)
            dialogBinding.sourceUrlEditText.setText(it.url)
            dialogBinding.isWhitelistSwitch.isChecked = it.isWhitelist
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_url else R.string.add_source_title)
            .setView(dialogBinding.root)
            .setPositiveButton(if (existing == null) R.string.add else R.string.update) { _, _ ->
                val name = dialogBinding.sourceNameEditText.text.toString()
                val url = dialogBinding.sourceUrlEditText.text.toString()
                val isWhitelist = dialogBinding.isWhitelistSwitch.isChecked
                if (name.isNotBlank() && url.isNotBlank()) {
                    if (existing != null) {
                        filterManager.removeCustomSource(existing.id)
                    }
                    filterManager.addCustomSource(name, url, isWhitelist = isWhitelist)
                    refreshList()
                }
            }.setNegativeButton(R.string.cancel, null).show()
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
            Toast.makeText(this, R.string.upload_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.upload_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    class SourceAdapter(
        private val items: List<FilterSource>,
        val onToggle: (String, Boolean) -> Unit,
        val onDelete: (String) -> Unit,
        val onEdit: (FilterSource) -> Unit,
        val onSync: (FilterSource) -> Unit,
        val onPreview: (FilterSource) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemFilterSourceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFilterSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.sourceName.text = item.name
            val typeStr = if (item.type == "LOCAL") holder.itemView.context.getString(R.string.local_file) else item.url
            holder.binding.sourceDetails.text = "${item.domainCount} domains • $typeStr"
            holder.binding.categoryChip.text = if (item.isWhitelist) "Whitelist" else item.category

            holder.binding.sourceSwitch.setOnCheckedChangeListener(null)
            holder.binding.sourceSwitch.isChecked = item.enabled
            holder.binding.sourceSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }

            holder.binding.syncSourceBtn.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onSync(item)
            }

            holder.itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPreview(item)
            }

            holder.itemView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (item.id.startsWith("custom_")) {
                    val options = arrayOf("Edit Source", "Delete Source")
                    AlertDialog.Builder(holder.itemView.context)
                        .setItems(options) { _, which ->
                            if (which == 0) onEdit(item) else onDelete(item.id)
                        }.show()
                }
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
