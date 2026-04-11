package com.example.shieldblock

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.shieldblock.databinding.ActivityTextEditorBinding
import java.io.File

class TextEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTextEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filePath = intent.getStringExtra("file_path") ?: ""
        val fileName = intent.getStringExtra("file_name") ?: "Editor"
        binding.fileNameText.text = fileName

        val file = File(filePath)
        if (file.exists()) {
            binding.fileEditText.setText(file.readText())
        }

        binding.saveButton.setOnClickListener {
            try {
                file.writeText(binding.fileEditText.text.toString())
                Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
