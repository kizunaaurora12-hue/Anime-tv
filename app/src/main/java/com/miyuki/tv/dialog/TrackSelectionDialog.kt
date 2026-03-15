package com.miyuki.tv.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.miyuki.tv.R
import com.miyuki.tv.databinding.TrackSelectionDialogBinding

class TrackSelectionDialog : DialogFragment() {

    private lateinit var trackSelector: DefaultTrackSelector
    private var onDismissListener: (() -> Unit)? = null

    companion object {
        fun createForTrackSelector(
            trackSelector: DefaultTrackSelector,
            onDismiss: () -> Unit
        ): TrackSelectionDialog = TrackSelectionDialog().apply {
            this.trackSelector     = trackSelector
            this.onDismissListener = onDismiss
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AppCompatDialog(activity, R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.track_selection_title)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = TrackSelectionDialogBinding.inflate(inflater, container, false)
        binding.btnClose.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }
}
