package com.kuaforumcepte.app.data.media

import android.net.Uri
import com.kuaforumcepte.app.data.registration.DraftImage

/** Seçilen görselin kırp/sığdır önizlemesi; "Kullan" ile ilgili listeye eklenir. */
data class PendingImage(
    val source: Uri,
    val kind: ImageKind,
    val mode: FitMode,
    val preview: DraftImage?,
    val processing: Boolean,
)
