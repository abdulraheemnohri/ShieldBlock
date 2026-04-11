package com.example.shieldblock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.databinding.ActivityWhitelistBinding
import com.example.shieldblock.databinding.ItemWhitelistBinding

class WhitelistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWhitelistBinding
    private val whitelistManager by lazy { WhitelistManager(this) }
    private var allDomains: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.whitelistRecyclerView.layoutManager = LinearLayoutManager(this)

        refreshList()

        binding.searchWhitelistEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.clearWhitelistBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Whitelist?")
                .setMessage("This will remove all domains from your whitelist.")
                .setPositiveButton("Clear All") { _, _ ->
                    allDomains.forEach { whitelistManager.removeFromWhitelist(it) }
                    refreshList()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshList() {
        allDomains = whitelistManager.getWhitelist().toList().sorted()
        applyFilter(binding.searchWhitelistEditText.text.toString())
    }

    private fun applyFilter(query: String) {
        val filtered = allDomains.filter { it.contains(query, ignoreCase = true) }
        binding.whitelistCountText.text = "${filtered.size} domains"
        binding.whitelistRecyclerView.adapter = WhitelistAdapter(filtered) { domain ->
            whitelistManager.removeFromWhitelist(domain)
            refreshList()
        }
    }

    class WhitelistAdapter(private val domains: List<String>, val onRemove: (String) -> Unit) :
        RecyclerView.Adapter<WhitelistAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemWhitelistBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWhitelistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val domain = domains[position]
            holder.binding.domainText.text = domain
            holder.binding.removeBtn.setOnClickListener { onRemove(domain) }
        }

        override fun getItemCount() = domains.size
    }
}
