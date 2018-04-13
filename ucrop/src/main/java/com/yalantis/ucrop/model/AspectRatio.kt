package com.yalantis.ucrop.model

import android.os.Parcelable
import android.support.annotation.Nullable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AspectRatio(@Nullable val aspectRatioTitle: String?,
                       val aspectRatioX: Float,
                       val aspectRatioY: Float) : Parcelable