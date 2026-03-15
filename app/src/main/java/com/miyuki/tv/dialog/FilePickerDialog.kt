package com.miyuki.tv.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * File picker menggunakan Android SAF (Storage Access Framework) built-in.
 * Tidak membutuhkan library pihak ketiga.
 */
class FilePickerDialog(private val fragment: Fragment) {

    private var selectionListener: ((Array<String>) -> Unit)? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null

    init {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris: List<Uri> ->
            val paths = uris.mapNotNull { uri ->
                // Ambil path yang bisa dibaca dari URI
                getRealPathFromUri(fragment.requireContext(), uri)
            }.toTypedArray()
            if (paths.isNotEmpty()) {
                selectionListener?.invoke(paths)
            }
        }
    }

    fun setDialogSelectionListener(listener: (Array<String>) -> Unit) {
        selectionListener = listener
    }

    fun show() {
        // Buka file picker untuk JSON dan M3U
        launcher?.launch(arrayOf(
            "application/json",
            "application/octet-stream",
            "text/plain",
            "*/*"
        ))
    }

    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        return try {
            // Untuk SAF, kita simpan URI string-nya langsung
            // SourcesReader sudah support content:// URI lewat ContentResolver
            uri.toString()
        } catch (e: Exception) {
            null
        }
    }
}
