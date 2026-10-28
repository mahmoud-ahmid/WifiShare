package com.example.wifishare.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wifishare.databinding.ActivityPdfViewerBinding
import com.example.wifishare.databinding.ItemPdfPageBinding

class PdfViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPdfViewerBinding
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val uri = intent.getParcelableExtra<Uri>(EXTRA_URI) ?: return finish()
        supportActionBar?.title = intent.getStringExtra(EXTRA_NAME) ?: ""

        try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return finish()
            renderer = PdfRenderer(pfd!!)
            binding.rvPages.layoutManager = LinearLayoutManager(this)
            binding.rvPages.adapter = PdfPageAdapter(renderer!!)
        } catch (e: Exception) {
            binding.tvError.visibility = View.VISIBLE
            binding.rvPages.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
        pfd = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }

    class PdfPageAdapter(private val renderer: PdfRenderer) :
        RecyclerView.Adapter<PdfPageAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = renderer.pageCount

        inner class VH(private val b: ItemPdfPageBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(index: Int) {
                val page = renderer.openPage(index)
                val scale = 2
                val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                b.ivPage.setImageBitmap(bmp)
                page.close()
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_NAME = "extra_name"
        fun newIntent(ctx: Context, uri: Uri, name: String): Intent =
            Intent(ctx, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_URI, uri); putExtra(EXTRA_NAME, name)
            }
    }
}
