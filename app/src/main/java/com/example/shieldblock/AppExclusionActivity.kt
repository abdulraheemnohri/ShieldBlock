package com.example.shieldblock

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.StatsManager
import com.example.shieldblock.data.AppGroupManager
import com.example.shieldblock.databinding.ActivityAppExclusionBinding
import com.example.shieldblock.databinding.ItemAppExclusionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isExcluded: Boolean,
    val blockedCount: Int,
    val isSystem: Boolean,
    val isHighRisk: Boolean
)

class AppExclusionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppExclusionBinding
    private val statsManager by lazy { StatsManager(this) }
    private val groupManager by lazy { AppGroupManager(this) }
    private val excludedAppsKey = "excluded_apps"
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var sortMode = "blocked"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppExclusionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBottomNavigation()
        binding.appRecyclerView.layoutManager = LinearLayoutManager(this)

        refreshAppsList()

        binding.searchAppEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilters() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.showSystemAppsChip.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.sortBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showSortDialog()
        }
        binding.createGroupBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showGroupMenu()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_apps
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            binding.bottomNavigation.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_apps -> true
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    private fun showGroupMenu() {
        val groups = groupManager.getGroups()
        val names = groups.map { it.name }.toMutableList()
        names.add(0, "+ Create New Group")

        AlertDialog.Builder(this)
            .setTitle("Custom App Groups")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    showCreateGroupDialog()
                } else {
                    applyGroupExclusion(groups[which - 1])
                }
            }.show()
    }

    private fun applyGroupExclusion(group: com.example.shieldblock.data.AppGroup) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getStringSet(excludedAppsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(group.packages)
        prefs.edit().putStringSet(excludedAppsKey, current).apply()
        refreshAppsList()
        Toast.makeText(this, "Group '${group.name}' excluded", Toast.LENGTH_SHORT).show()
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this)
        input.hint = "Group Name (e.g. Social)"
        AlertDialog.Builder(this)
            .setTitle("Create App Group")
            .setMessage("Group will include currently filtered apps.")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val pkgs = filteredApps.map { it.packageName }.toSet()
                    groupManager.saveGroup(name, pkgs)
                    Toast.makeText(this, "Group '$name' created with ${pkgs.size} apps", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun refreshAppsList() {
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { loadApps() }
            applyFilters()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Most Blocks", "App Name")
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setItems(options) { _, which ->
                sortMode = if (which == 0) "blocked" else "name"
                applyFilters()
            }
            .show()
    }

    private fun applyFilters() {
        val query = binding.searchAppEditText.text.toString()
        val showSystem = binding.showSystemAppsChip.isChecked

        filteredApps = allApps.filter {
            (showSystem || !it.isSystem) &&
            (it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
        }

        filteredApps = if (sortMode == "blocked") {
            filteredApps.sortedByDescending { it.blockedCount }
        } else {
            filteredApps.sortedBy { it.name }
        }

        binding.appRecyclerView.adapter = AppAdapter(filteredApps) { pkg, isExcluded ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val current = prefs.getStringSet(excludedAppsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (isExcluded) current.add(pkg) else current.remove(pkg)
            prefs.edit().putStringSet(excludedAppsKey, current).apply()
        }
    }

    private fun loadApps(): List<AppInfo> {
        val pm = packageManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val excludedPackages = prefs.getStringSet(excludedAppsKey, emptySet()) ?: emptySet()

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map {
                val blockedCount = statsManager.getAppBlockedCount(it.packageName)
                AppInfo(
                    it.loadLabel(pm).toString(),
                    it.packageName,
                    it.loadIcon(pm),
                    excludedPackages.contains(it.packageName),
                    blockedCount,
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    blockedCount > 50
                )
            }
    }

    class AppAdapter(private val apps: List<AppInfo>, val onToggle: (String, Boolean) -> Unit) :
        RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemAppExclusionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAppExclusionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.binding.appName.text = app.name
            holder.binding.packageName.text = app.packageName
            holder.binding.appIcon.setImageDrawable(app.icon)
            holder.binding.exclusionSwitch.setOnCheckedChangeListener(null)
            holder.binding.exclusionSwitch.isChecked = app.isExcluded

            holder.binding.appStatsText.text = if (app.isHighRisk) {
                "⚠️ ${app.blockedCount} tracker requests blocked"
            } else {
                "${app.blockedCount} ads blocked"
            }
            holder.binding.appStatsText.setTextColor(
                holder.itemView.context.getColor(if (app.isHighRisk) R.color.tertiary else R.color.primary)
            )

            holder.binding.exclusionSwitch.setOnCheckedChangeListener { _, isChecked ->
                app.isExcluded = isChecked
                onToggle(app.packageName, isChecked)
            }
        }

        override fun getItemCount() = apps.size
    }
}
