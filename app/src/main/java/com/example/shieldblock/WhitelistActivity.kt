package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
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
    private var allWhitelist: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupBottomNavigation()
        binding.whitelistRecyclerView.layoutManager = LinearLayoutManager(this)
        refreshList()

        binding.searchWhitelistEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterList(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.addDomainBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddDomainDialog()
        }

        binding.clearWhitelistBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            AlertDialog.Builder(this)
                .setTitle("Clear All?")
                .setMessage("Remove all entries from safe domains?")
                .setPositiveButton("Purge") { _, _ ->
                    whitelistManager.clearWhitelist()
                    refreshList()
                }.setNegativeButton("Cancel", null).show()
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
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left); finish(); true }
                else -> false
            }
        }
    }

    private fun showAddDomainDialog() {
        val input = EditText(this)
        input.hint = "e.g. example.com"
        AlertDialog.Builder(this)
            .setTitle("Add Safe Domain")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val domain = input.text.toString().trim()
                if (domain.isNotEmpty()) {
                    whitelistManager.addToWhitelist(domain)
                    refreshList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshList() {
        allWhitelist = whitelistManager.getWhitelist().toList().sorted()
        filterList(binding.searchWhitelistEditText.text.toString())
    }

    private fun filterList(query: String) {
        val filtered = allWhitelist.filter { it.contains(query, ignoreCase = true) }
        binding.whitelistCountText.text = "${filtered.size} Safe Domains"
        binding.whitelistRecyclerView.adapter = WhitelistAdapter(filtered) { domain ->
            whitelistManager.removeFromWhitelist(domain)
            refreshList()
        }
    }

    class WhitelistAdapter(private val items: List<String>, val onRemove: (String) -> Unit) :
        RecyclerView.Adapter<WhitelistAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemWhitelistBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWhitelistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val domain = items[position]
            holder.binding.whitelistDomain.text = domain
            holder.binding.deleteWhitelistBtn.setOnClickListener { onRemove(domain) }
        }

        override fun getItemCount() = items.size
    }
}
