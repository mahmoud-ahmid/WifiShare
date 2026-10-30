package com.example.wifishare.data.send

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThumbnailLoader {
    private const val TAG = "ThumbnailLoader"
    private val cache = LruCache<String, Bitmap>(200)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun load(ctx: Context, item: SendItem, target: ImageView, fallback: Int) {
        loadRaw(ctx, item.uri, item.mimeType, item.appIcon, target, fallback)
    }

    fun loadRaw(ctx: Context, uri: Uri, mimeType: String, appIcon: Drawable?, target: ImageView, fallback: Int) {
        val key = uri.toString()
        target.tag = key

        if (appIcon != null) {
            target.setImageDrawable(appIcon)
            target.scaleType = ImageView.ScaleType.FIT_CENTER
            return
        }

        cache.get(key)?.let {
            target.setImageBitmap(it)
            target.scaleType = ImageView.ScaleType.CENTER_CROP
            return
        }

        target.setImageResource(fallback)
        target.scaleType = ImageView.ScaleType.CENTER_INSIDE

        scope.launch {
            val bmp = when {
                mimeType.startsWith("image/") -> loadImage(ctx, uri)
                mimeType.startsWith("video/") -> loadVideo(ctx, uri)
                mimeType.startsWith("audio/") -> loadAudio(ctx, uri)
                else -> null
            }
            if (bmp != null) {
                cache.put(key, bmp)
                withContext(Dispatchers.Main) {
                    if (target.tag == key) {
                        target.setImageBitmap(bmp)
                        target.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            }
        }
    }

    private fun loadImage(ctx: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.loadThumbnail(uri, Size(180, 180), null)
        } else {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        }
    } catch (e: Exception) { Log.w(TAG, "img: ${e.message}"); null }

    private fun loadVideo(ctx: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.contentResolver.loadThumbnail(uri, Size(180, 180), null)
        } else {
            val m = MediaMetadataRetriever()
            m.setDataSource(ctx, uri)
            val b = m.getFrameAtTime(0)
            m.release(); b
        }
    } catch (e: Exception) { Log.w(TAG, "vid: ${e.message}"); null }

    private fun loadAudio(ctx: Context, uri: Uri): Bitmap? = try {
        val m = MediaMetadataRetriever()
        m.setDataSource(ctx, uri)
        val a = m.embeddedPicture
        m.release()
        a?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: Exception) { Log.w(TAG, "aud: ${e.message}"); null }
}
