package com.miyuki.tv.dialog

import android.content.Context
import com.developer.filepicker.controller.DialogSelectionListener
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog as BaseFilePicker

class FilePickerDialog(context: Context) : BaseFilePicker(context) {
    fun setDialogSelectionListener(listener: (Array<String>) -> Unit) {
        setDialogSelectionListener(DialogSelectionListener { files -> listener(files) })
    }
}
