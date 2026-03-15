package com.miyuki.tv.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.miyuki.tv.R
import com.miyuki.tv.databinding.TrackSelectionDialogBinding

class TrackSelectionDialog : DialogFragment() {
    private lateinit var trackSelector: DefaultTrackSelector
    private var onDismiss: (() -> Unit)? = null

    companion object {
        fun createForTrackSelector(ts: DefaultTrackSelector, onDismiss: () -> Unit) =
            TrackSelectionDialog().apply { this.trackSelector = ts; this.onDismiss = onDismiss }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AppCompatDialog(activity, R.style.SettingsDialogThemeOverlay).apply { setTitle(R.string.track_selection_title) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val b = TrackSelectionDialogBinding.inflate(i, c, false)
        b.btnClose.setOnClickListener { dismiss() }
        return b.root
    }

    override fun onDismiss(dialog: android.content.DialogInterface) { super.onDismiss(dialog); onDismiss?.invoke() }
}
