package com.example.android_bouncybubbleworldview_example

import android.content.res.Resources

inline val Number.dpToPx: Float
    get() = toFloat() * Resources.getSystem().displayMetrics.density
