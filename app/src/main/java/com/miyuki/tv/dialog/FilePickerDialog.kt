package com.miyuki.tv.dialog

import android.content.Context
import com.developer.filepicker.controller.DialogSelectionListener
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog as BaseFilePicker

/**
 * Thin wrapper around the FilePicker library dialog so the rest
 * of the codebase imports from our own package.
 */
class FilePickerDialog(context: Context) : BaseFilePicker(context) {
    fun setDialogSelectionListener(listener: (Array<String>) -> Unit) {
        setDialogSelectionListener(DialogSelectionListener { files -> listener(files) })
    }
}
