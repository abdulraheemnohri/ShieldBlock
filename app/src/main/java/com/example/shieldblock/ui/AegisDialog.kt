package com.example.shieldblock.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.example.shieldblock.R
import com.example.shieldblock.databinding.DialogAegisBinding

class AegisDialog(private val context: Context) {
    private val binding = DialogAegisBinding.inflate(LayoutInflater.from(context))
    private val builder = AlertDialog.Builder(context, R.style.Aegis_Dialog_Theme)
    private var dialog: AlertDialog? = null

    init {
        builder.setView(binding.root)
    }

    fun setTitle(title: String): AegisDialog {
        binding.dialogTitle.text = title
        return this
    }

    fun setMessage(message: String): AegisDialog {
        binding.dialogMessage.text = message
        return this
    }

    fun setPositiveButton(text: String, onClick: () -> Unit): AegisDialog {
        binding.btnPositive.text = text
        binding.btnPositive.setOnClickListener {
            onClick()
            dialog?.dismiss()
        }
        return this
    }

    fun setNegativeButton(text: String, onClick: (() -> Unit)? = null): AegisDialog {
        binding.btnNegative.visibility = View.VISIBLE
        binding.btnNegative.text = text
        binding.btnNegative.setOnClickListener {
            onClick?.invoke()
            dialog?.dismiss()
        }
        return this
    }

    fun show(): AlertDialog {
        dialog = builder.create()
        dialog?.show()
        // Ensure background is transparent for the card corners
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog!!
    }
}
