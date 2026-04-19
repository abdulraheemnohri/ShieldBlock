package com.example.shieldblock

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.data.FilterManager
import com.example.shieldblock.databinding.ActivityAppExclusionBinding
import com.example.shieldblock.databinding.ItemAppExclusionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppExclusionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppExclusionBinding
    private val filterManager by lazy { FilterManager(this) }
    private var allApps: List<AppInfo> = emptyList()
    private var showSystemApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppExclusionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = "Aegis App Guard"

        binding.appRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.systemAppToggle.setOnClickListener {
            showSystemApps = !showSystemApps
            binding.systemAppToggle.text = if (showSystemApps) "Hide System Apps" else "Show System Apps"
            filterApps(binding.searchAppEditText.text.toString())
        }

        binding.searchAppEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterApps(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadApps()
        setupBottomNavigation()
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val excluded = filterManager.getExcludedPackages()

                apps.map { app ->
                    AppInfo(
                        name = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isExcluded = excluded.contains(app.packageName)
                    )
                }.sortedBy { it.name }
            }
            filterApps("")
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun filterApps(query: String) {
        val filtered = allApps.filter {
            (showSystemApps || !it.isSystem) &&
            (it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
        }
        binding.appRecyclerView.adapter = AppAdapter(filtered) { app, isChecked ->
            if (isChecked) {
                filterManager.addExcludedPackage(app.packageName)
            } else {
                filterManager.removeExcludedPackage(app.packageName)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_apps
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_analytics -> { startActivity(Intent(this, AnalyticsActivity::class.java)); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val isSystem: Boolean,
        var isExcluded: Boolean
    )

    class AppAdapter(private val apps: List<AppInfo>, val onToggle: (AppInfo, Boolean) -> Unit) :
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
            holder.binding.excludeSwitch.isChecked = app.isExcluded
            holder.binding.systemBadge.visibility = if (app.isSystem) View.VISIBLE else View.GONE

            holder.binding.excludeSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app, isChecked)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(it.context, AppDossierActivity::class.java)
                intent.putExtra("packageName", app.packageName)
                it.context.startActivity(intent)
            }
        }

        override fun getItemCount() = apps.size
    }
}
