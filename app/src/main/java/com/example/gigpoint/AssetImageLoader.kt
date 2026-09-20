package com.example.gigpoint

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView

object AssetImageLoader {

    fun loadProduct(
        context: Context,
        imageView: ImageView,
        assetName: String
    ) {
        try {
            context.assets.open("products/$assetName.png").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.mipmap.ic_launcher)
                }
            }
        } catch (_: Exception) {
            imageView.setImageResource(R.mipmap.ic_launcher)
        }
    }
}
