package com.example.shieldblock

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldblock.databinding.ActivityAppExclusionBinding
import com.example.shieldblock.databinding.ItemAppExclusionBinding

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isExcluded: Boolean
)

class AppExclusionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppExclusionBinding
    private val excludedAppsKey = "excluded_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppExclusionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val pm = packageManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val excludedPackages = prefs.getStringSet(excludedAppsKey, emptySet()) ?: emptySet()

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map {
                AppInfo(
                    it.loadLabel(pm).toString(),
                    it.packageName,
                    it.loadIcon(pm),
                    excludedPackages.contains(it.packageName)
                )
            }.sortedBy { it.name }

        binding.appRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appRecyclerView.adapter = AppAdapter(apps) { pkg, isExcluded ->
            val current = prefs.getStringSet(excludedAppsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (isExcluded) current.add(pkg) else current.remove(pkg)
            prefs.edit().putStringSet(excludedAppsKey, current).apply()
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
            holder.binding.exclusionSwitch.isChecked = app.isExcluded
            holder.binding.exclusionSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app.packageName, isChecked)
            }
        }

        override fun getItemCount() = apps.size
    }
}
