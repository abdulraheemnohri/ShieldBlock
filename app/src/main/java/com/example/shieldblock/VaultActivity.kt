package com.example.shieldblock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.data.VaultManager
import com.example.shieldblock.databinding.ActivityVaultBinding

class VaultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVaultBinding
    private val vaultManager by lazy { VaultManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.exportBtn.setOnClickListener {
            val config = vaultManager.exportConfig()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Aegis Config", config)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Aegis Core Configuration Encoded to Clipboard", Toast.LENGTH_LONG).show()
        }

        binding.importBtn.setOnClickListener {
            showImportDialog()
        }
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Paste Aegis JSON Payload..."
        AlertDialog.Builder(this)
            .setTitle("Synchronize Vault")
            .setView(input)
            .setPositiveButton("INJECT") { _, _ ->
                val payload = input.text.toString()
                if (vaultManager.importConfig(payload)) {
                    Toast.makeText(this, "Vault Decrypted & Synchronized", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "INVALID PAYLOAD SIGNATURE", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ABORT", null)
            .show()
    }
}
