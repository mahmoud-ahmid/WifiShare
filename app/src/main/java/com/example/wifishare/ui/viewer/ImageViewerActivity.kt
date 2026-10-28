package com.example.wifishare.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.wifishare.databinding.ActivityImageViewerBinding

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageViewerBinding
    private val matrix = Matrix()
    private var scaleFactor = 1f
    private lateinit var scaleDetector: ScaleGestureDetector
    private var lastX = 0f
    private var lastY = 0f
    private var posX = 0f
    private var posY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI) ?: return finish()
        supportActionBar?.title = intent.getStringExtra(EXTRA_NAME) ?: ""

        binding.ivImage.setImageURI(uri)

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 6f)
                if (scaleFactor <= 1.01f) { posX = 0f; posY = 0f }
                binding.ivImage.scaleType = ImageView.ScaleType.MATRIX
                applyMatrix()
                return true
            }
        })

        binding.ivImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
                MotionEvent.ACTION_MOVE -> if (!scaleDetector.isInProgress && scaleFactor > 1.01f) {
                    posX += event.x - lastX
                    posY += event.y - lastY
                    lastX = event.x; lastY = event.y
                    applyMatrix()
                }
            }
            true
        }
    }

    private fun applyMatrix() {
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(posX, posY)
        binding.ivImage.imageMatrix = matrix
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_NAME = "extra_name"
        fun newIntent(ctx: Context, uri: Uri, name: String): Intent =
            Intent(ctx, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_URI, uri); putExtra(EXTRA_NAME, name)
            }
    }
}
