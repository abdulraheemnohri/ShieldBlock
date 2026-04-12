package com.example.shieldblock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.BlacklistManager
import com.example.shieldblock.data.WhitelistManager
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.ActivityRuleEditorBinding
import com.example.shieldblock.databinding.ItemRuleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRuleEditorBinding
    private val blacklistManager by lazy { BlacklistManager(this) }
    private val whitelistManager by lazy { WhitelistManager(this) }
    private val filterManager by lazy { FilterManager(this) }
    private var allDomains: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.ruleRecyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            allDomains = withContext(Dispatchers.IO) {
                blacklistManager.loadLocalBlacklist()
            }
            binding.resultCountText.text = getString(R.string.domains_found_label, allDomains.size)
        }

        binding.searchRuleEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 3) {
                    filterDomains(query)
                } else if (query.isEmpty()) {
                    binding.resultCountText.text = getString(R.string.domains_found_label, allDomains.size)
                    binding.ruleRecyclerView.adapter = null
                } else {
                    binding.ruleRecyclerView.adapter = null
                    binding.resultCountText.setText(R.string.type_chars_hint)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.addCustomRuleBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddCustomRuleDialog()
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
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    private fun filterDomains(query: String) {
        val filtered = allDomains.filter { it.contains(query, ignoreCase = true) }.take(100)
        binding.resultCountText.text = getString(R.string.search_results_label, filtered.size)
        binding.ruleRecyclerView.adapter = RuleAdapter(filtered) { domain ->
            whitelistManager.addToWhitelist(domain)
            Toast.makeText(this, getString(R.string.whitelisted_toast, domain), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddCustomRuleDialog() {
        val input = EditText(this)
        input.hint = "*.example.com"
        AlertDialog.Builder(this)
            .setTitle(R.string.add_pattern)
            .setView(input)
            .setPositiveButton(R.string.block) { _, _ ->
                val rule = input.text.toString().trim()
                if (rule.isNotEmpty()) {
                    filterManager.addCustomRule(rule)
                    Toast.makeText(this, "Pattern added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    class RuleAdapter(private val domains: List<String>, val onAllow: (String) -> Unit) :
        RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val domain = domains[position]
            holder.binding.domainText.text = domain
            holder.binding.whitelistActionBtn.setOnClickListener { onAllow(domain) }
        }

        override fun getItemCount() = domains.size
    }
}
