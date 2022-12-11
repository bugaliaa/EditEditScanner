package com.example.editeditscanner.activity

import android.content.Intent
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.GONE
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.editeditscanner.App
import com.example.editeditscanner.R
import com.example.editeditscanner.data.Frame
import com.example.editeditscanner.databinding.ActivityEditBinding
import com.example.editeditscanner.utils.BrightnessAndContrastController
import com.example.editeditscanner.utils.Filter
import com.example.editeditscanner.utils.Utils
import com.example.editeditscanner.viewmodels.EditViewModel
import com.example.editeditscanner.viewmodels.EditViewModelFactory
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.*

class EditActivity : BaseActivity(), View.OnClickListener, OnSeekBarChangeListener,
    NavigationBarView.OnItemSelectedListener {

    private lateinit var croppedMat: Mat
    private lateinit var editedMat: Mat
    private lateinit var frame: Frame
    private lateinit var brightnessAndContrastController: BrightnessAndContrastController
    private lateinit var binding: ActivityEditBinding
    private lateinit var viewModel: EditViewModel

    private var currentActiveId = R.id.iv_original_image
    private var modifyToolIsVisible = false

    private fun setupPreview() {
        croppedMat = Utils.readMat(frame.croppedUri)
        if (frame.editedUri == null) {
            editedMat = Mat()
            croppedMat.copyTo(editedMat)
        } else {
            editedMat = Utils.readMat(frame.editedUri)
        }
        previewMat(editedMat)
        binding.pbEdit.visibility = View.GONE
    }

    private fun filterImageButton(resourceId: Int, processImage: ProcessImage) {
        lifecycleScope.launch(Dispatchers.Default) {
            val height = croppedMat.height().toDouble()
            val width = croppedMat.width().toDouble()
            Mat().let { result ->
                Imgproc.resize(croppedMat, result, Size(width, height))
                processImage.process(result).let {
                    val bmp = Bitmap.createBitmap(
                        it.width(),
                        it.height(),
                        Bitmap.Config.ARGB_8888
                    )
                    matToBitmap(it, bmp)
                    lifecycleScope.launch(Dispatchers.Main) {
                        (findViewById<View?>(resourceId) as ImageView).setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun filterImage(processImage: ProcessImage) {
        binding.pbEdit.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            editedMat = processImage.process(croppedMat)
            previewMat(editedMat)
            lifecycleScope.launch(Dispatchers.Main) {
                binding.pbEdit.visibility = View.GONE
            }
        }
    }

    private fun setupFilterButtons() {
        filterImageButton(R.id.iv_original_image, object : ProcessImage {
            override fun process(mat: Mat): Mat {
                return croppedMat
            }
        })
        filterImageButton(R.id.iv_black_and_white, object : ProcessImage {
            override fun process(mat: Mat): Mat {
                return Filter.thresholdOTSU(mat)
            }
        })
        filterImageButton(R.id.iv_auto, object : ProcessImage {
            override fun process(mat: Mat): Mat {
                return Filter.auto(mat)
            }
        })
        filterImageButton(R.id.iv_grayscale, object : ProcessImage {
            override fun process(mat: Mat): Mat {
                return Filter.grayscale(mat)
            }
        })
        filterImageButton(R.id.iv_magic, object : ProcessImage {
            override fun process(mat: Mat): Mat {
                return Filter.magicColor(mat)
            }
        })
    }

    private fun setActive(activeId: Int) {
        findViewById<View>(currentActiveId).apply {
            alpha = 0.6f
            setPadding(12, 12, 12, 12)
        }
        findViewById<View>(activeId).apply {
            alpha = 1f
            setPadding(0, 0, 0, 0)
        }
        currentActiveId = activeId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setActive(R.id.iv_auto)
        val frameId = intent.getLongExtra("frameId", -1)
        if (frameId == -1L) {
            Toast.makeText(this, "Unexpected error occures", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initialiseViewModel(frameId)

        viewModel.frame?.observe(this) { frame ->
            this.frame = frame
            setupPreview()
            setupFilterButtons()
            setupBrightnessAndContrast()
        }

        binding.let {
            it.bottomNavigationView.setOnItemSelectedListener(this)
            it.ivBlackAndWhite.setOnClickListener(this)
            it.ivAuto.setOnClickListener(this)
            it.ivGrayscale.setOnClickListener(this)
            it.ivMagic.setOnClickListener(this)
            it.ivOriginalImage.setOnClickListener(this)
        }
    }

    private fun initialiseViewModel(frameId: Long) {
        (application as App).database?.let { db ->
            viewModel = ViewModelProvider(
                this,
                EditViewModelFactory(application as App, db.frameDao())
            ).get(
                EditViewModel::class.java
            )
            viewModel.getFrame(frameId)
        }
    }

    private fun setupBrightnessAndContrast() {
        brightnessAndContrastController = BrightnessAndContrastController(0.0, 1.0)
        binding.sbBrightness.let {
            it.max = 200
            it.progress = 100
            it.setOnSeekBarChangeListener(this)
        }

        binding.sbContrast.let {
            it.max = 200
            it.progress = 100
            it.setOnSeekBarChangeListener(this)
        }

        binding.llModifyTools.visibility = GONE
    }

    private fun resetBrightnessAndContrast() {
        brightnessAndContrastController.brightness = 0.0
        brightnessAndContrastController.contrast = 0.0
        binding.sbBrightness.progress = 100
        binding.sbContrast.progress = 150
    }

    private fun previewMat(mat: Mat) {
        val bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
        matToBitmap(mat, bitmap)
        lifecycleScope.launch(Dispatchers.Main) {
            binding.ivEdit.setImageBitmap(bitmap)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                saveImage()
            }
            R.id.menu_rotate_left -> {
                rotateLeft()
            }
            R.id.menu_retake -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            R.id.menu_modify -> {
                modifyToolIsVisible = !modifyToolIsVisible
                binding.llModifyTools.visibility =
                    if (modifyToolIsVisible) View.VISIBLE else View.GONE
            }
        }
        return false
    }

    private fun saveImage() {
        binding.pbEdit.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            brightnessAndContrastController.mat?.let {
                editedMat = it
            }
            Utils.let {
                it.saveMat(editedMat, frame.editedUri)
                it.saveMat(croppedMat, frame.croppedUri)
            }
            Intent().let {
                it.putExtra(
                    "frame_position",
                    intent.getIntExtra("frame_position", 0)
                )
                setResult(RESULT_OK, it)
            }
            editedMat.release()
            croppedMat.release()
            lifecycleScope.launch(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun rotateLeft() {
        Core.rotate(croppedMat, croppedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
        Core.rotate(editedMat, editedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
        previewMat(editedMat)
    }

    override fun onClick(view: View) {
        setActive(view.id)
        resetBrightnessAndContrast()
        when (view.id) {
            R.id.iv_black_and_white -> {
                filterImage(object : ProcessImage {
                    override fun process(mat: Mat): Mat {
                        return Filter.thresholdOTSU(mat)
                    }
                })
            }
            R.id.iv_auto -> {
                filterImage(object : ProcessImage {
                    override fun process(mat: Mat): Mat {
                        return Filter.auto(mat)
                    }
                })
            }
            R.id.iv_grayscale -> {
                filterImage(object : ProcessImage {
                    override fun process(mat: Mat): Mat {
                        return Filter.grayscale(mat)
                    }
                })
            }
            R.id.iv_magic -> {
                filterImage(object : ProcessImage {
                    override fun process(mat: Mat): Mat {
                        return Filter.magicColor(mat)
                    }
                })
            }
            R.id.iv_original_image -> {
                editedMat = croppedMat.clone()
                previewMat(editedMat)
            }
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
        when (seekBar.id) {
            R.id.sb_contrast -> {
                binding.tvContrast.text = String.format(
                    Locale.getDefault(),
                    "Contrast • %d%%",
                    i - 100
                )
                brightnessAndContrastController.setContrast(editedMat, i / 100.0)
            }
            R.id.sb_brightness -> {
                binding.tvBrightness.text = String.format(
                    Locale.getDefault(),
                    "Brightness • %d%%",
                    i - 100
                )
                brightnessAndContrastController.setBrightness(editedMat, (i - 100).toDouble())
            }
            else -> {
                editedMat
            }
        }.let {
            previewMat(it)
        }
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(p0: SeekBar?) {}

    internal interface ProcessImage {
        fun process(mat: Mat): Mat
    }
}