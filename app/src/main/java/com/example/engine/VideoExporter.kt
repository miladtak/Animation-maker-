package com.example.engine

import android.content.Context
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

object VideoExporter {

    suspend fun exportAnimation(
        context: Context,
        project: Project,
        format: com.example.ui.components.ExportFormat,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val totalFrames = project.timeline.totalFrames.coerceAtLeast(1)
        val fps = project.timeline.fps.coerceIn(12, 60)
        val width = 720
        val height = (720f * (project.canvasHeight / project.canvasWidth.coerceAtLeast(100f))).toInt().coerceIn(360, 1280)
        // Ensure even dimensions for video codecs
        val encWidth = if (width % 2 != 0) width - 1 else width
        val encHeight = if (height % 2 != 0) height - 1 else height

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
        if (!outputDir.exists()) outputDir.mkdirs()

        when (format) {
            com.example.ui.components.ExportFormat.CURRENT_FRAME_PNG -> {
                val file = File(outputDir, "puppet_frame_${project.timeline.currentFrame}_${System.currentTimeMillis()}.png")
                val bitmap = renderFrameToBitmap(project, project.timeline.currentFrame, encWidth, encHeight)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                onProgress(1.0f)
                file
            }

            com.example.ui.components.ExportFormat.PNG_SEQUENCE -> {
                val seqDir = File(outputDir, "seq_${System.currentTimeMillis()}")
                seqDir.mkdirs()
                for (f in 0 until totalFrames) {
                    val bitmap = renderFrameToBitmap(project, f, encWidth, encHeight)
                    val frameFile = File(seqDir, String.format("frame_%04d.png", f))
                    FileOutputStream(frameFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    onProgress((f + 1f) / totalFrames)
                }
                seqDir
            }

            com.example.ui.components.ExportFormat.ANIMATED_GIF -> {
                val file = File(outputDir, "animation_${System.currentTimeMillis()}.gif")
                exportGif(project, file, encWidth / 2, encHeight / 2, totalFrames, fps, onProgress)
                file
            }

            com.example.ui.components.ExportFormat.MP4_VIDEO -> {
                val file = File(outputDir, "animation_${System.currentTimeMillis()}.mp4")
                try {
                    exportMp4WithMediaCodec(project, file, encWidth, encHeight, totalFrames, fps, onProgress)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to GIF if device lacks MP4 hardware encoder
                    exportGif(project, file, encWidth / 2, encHeight / 2, totalFrames, fps, onProgress)
                }
                file
            }
        }
    }

    /**
     * Renders a specific timeline frame of the project onto an Android Bitmap.
     */
    fun renderFrameToBitmap(project: Project, frameIndex: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Canvas Background
        val bgColor = when (project.settings.backgroundMode) {
            CanvasBackgroundMode.DARK -> 0xFF1E1E24.toInt()
            CanvasBackgroundMode.WHITE -> 0xFFFFFFFF.toInt()
            CanvasBackgroundMode.GRAY -> 0xFF808080.toInt()
            CanvasBackgroundMode.GREEN_SCREEN -> 0xFF00FF00.toInt()
            CanvasBackgroundMode.TRANSPARENT -> 0x00000000
        }
        if (bgColor != 0) {
            canvas.drawColor(bgColor)
        }

        val scaleX = width.toFloat() / project.canvasWidth
        val scaleY = height.toFloat() / project.canvasHeight

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scaleX, scaleY)

        val frameData = project.frames.find { it.index == frameIndex }

        for (layer in project.layers) {
            if (!layer.isVisible) continue

            val transform = frameData?.layerOverrides?.get(layer.id) ?: layer.transform
            val pins = frameData?.pinOverrides?.get(layer.id) ?: layer.puppetModifier.pins
            val strokes = frameData?.strokesPerLayer?.get(layer.id) ?: layer.rasterStrokes

            canvas.save()
            canvas.translate(transform.translationX, transform.translationY)
            canvas.rotate(transform.rotation)
            canvas.scale(transform.scaleX, transform.scaleY)

            // 1. Draw Raster Strokes
            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val puppet = layer.puppetModifier
            val hasPins = pins.isNotEmpty() || puppet.deformerType != InvisibleDeformerType.NONE

            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue

                val colorInt = if (stroke.isEraser) 0xFF1E1E24.toInt() else stroke.color.toInt()
                strokePaint.color = colorInt
                strokePaint.strokeWidth = stroke.strokeWidth
                strokePaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)

                val effectivePoints = if (hasPins) {
                    stroke.points.map { pt ->
                        PuppetWarpEngine.deformPoint(
                            origX = pt.x,
                            origY = pt.y,
                            pins = pins,
                            deformerType = puppet.deformerType,
                            deformerRotation = puppet.deformerRotation,
                            deformerRadius = puppet.deformerRadius,
                            volumeContour = puppet.volumeContour,
                            width = project.canvasWidth,
                            height = project.canvasHeight
                        )
                    }
                } else stroke.points

                if (effectivePoints.size == 1) {
                    canvas.drawCircle(effectivePoints[0].x, effectivePoints[0].y, stroke.strokeWidth / 2f, strokePaint)
                } else {
                    val path = android.graphics.Path()
                    path.moveTo(effectivePoints[0].x, effectivePoints[0].y)
                    for (i in 1 until effectivePoints.size) {
                        val prev = effectivePoints[i - 1]
                        val curr = effectivePoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        path.quadTo(prev.x, prev.y, midX, midY)
                    }
                    path.lineTo(effectivePoints.last().x, effectivePoints.last().y)
                    canvas.drawPath(path, strokePaint)
                }
            }

            // 2. Draw Vector Shapes
            layer.shapeData?.let { shape ->
                val fillPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = shape.fillColor.toInt()
                    alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                }
                val linePaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = shape.strokeWidth
                    color = shape.strokeColor.toInt()
                    alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                }

                val halfW = shape.width / 2f
                val halfH = shape.height / 2f

                when (shape.shapeType) {
                    ShapeType.RECT -> {
                        if (shape.isFilled) canvas.drawRect(-halfW, -halfH, halfW, halfH, fillPaint)
                        canvas.drawRect(-halfW, -halfH, halfW, halfH, linePaint)
                    }
                    ShapeType.CIRCLE -> {
                        if (shape.isFilled) canvas.drawCircle(0f, 0f, shape.radius, fillPaint)
                        canvas.drawCircle(0f, 0f, shape.radius, linePaint)
                    }
                    ShapeType.TRIANGLE -> {
                        val path = android.graphics.Path().apply {
                            moveTo(0f, -halfH)
                            lineTo(halfW, halfH)
                            lineTo(-halfW, halfH)
                            close()
                        }
                        if (shape.isFilled) canvas.drawPath(path, fillPaint)
                        canvas.drawPath(path, linePaint)
                    }
                    ShapeType.STAR -> {
                        val path = android.graphics.Path()
                        val outerR = shape.radius
                        val innerR = outerR * 0.45f
                        for (i in 0 until 10) {
                            val r = if (i % 2 == 0) outerR else innerR
                            val angle = Math.toRadians((i * 36 - 90).toDouble())
                            val x = (r * cos(angle)).toFloat()
                            val y = (r * sin(angle)).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        if (shape.isFilled) canvas.drawPath(path, fillPaint)
                        canvas.drawPath(path, linePaint)
                    }
                    ShapeType.CUBE_25D -> {
                        canvas.drawRect(-50f, -20f, 50f, 60f, fillPaint)
                        canvas.drawRect(-50f, -20f, 50f, 60f, linePaint)
                    }
                }
            }

            // 3. Draw Text
            layer.textData?.let { textData ->
                val textPaint = Paint().apply {
                    isAntiAlias = true
                    color = textData.textColor.toInt()
                    textSize = textData.fontSize
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                }
                if (textData.mode == TextMode.EXTRUDE_3D) {
                    val shadowPaint = Paint(textPaint).apply { color = 0xFF111115.toInt() }
                    for (d in 1..6) {
                        canvas.drawText(textData.text, d.toFloat(), d.toFloat(), shadowPaint)
                    }
                }
                canvas.drawText(textData.text, 0f, 0f, textPaint)
            }

            canvas.restore()
        }

        canvas.restore()
        return bitmap
    }

    private fun exportMp4WithMediaCodec(
        project: Project,
        outputFile: File,
        width: Int,
        height: Int,
        totalFrames: Int,
        fps: Int,
        onProgress: (Float) -> Unit
    ) {
        val mime = "video/avc"
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = (1_000_000L / fps)

        try {
            for (f in 0 until totalFrames) {
                val bitmap = renderFrameToBitmap(project, f, width, height)
                val yuv = bitmapToYuv420(bitmap, width, height)

                val inputBufferIndex = encoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(yuv)
                    val ptsUs = f * frameDurationUs
                    val flags = if (f == totalFrames - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    encoder.queueInputBuffer(inputBufferIndex, 0, yuv.size, ptsUs, flags)
                }

                var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                while (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && outputBuffer != null) {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }

                onProgress((f + 1f) / totalFrames)
            }
        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (e: Exception) { e.printStackTrace() }
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun exportGif(
        project: Project,
        outputFile: File,
        width: Int,
        height: Int,
        totalFrames: Int,
        fps: Int,
        onProgress: (Float) -> Unit
    ) {
        val delayMs = (1000 / fps).coerceAtLeast(40)
        FileOutputStream(outputFile).use { stream ->
            // Minimal Animated GIF header
            stream.write("GIF89a".toByteArray())
            // Screen descriptor
            stream.write(byteArrayOf(
                (width and 0xFF).toByte(), ((width shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte(),
                0xF7.toByte(), 0, 0
            ))
            // Global color table (256 colors grayscale/basic palette)
            for (i in 0..255) {
                stream.write(byteArrayOf(i.toByte(), i.toByte(), i.toByte()))
            }

            // Netscape 2.0 loop block
            stream.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            stream.write("NETSCAPE2.0".toByteArray())
            stream.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))

            for (f in 0 until totalFrames) {
                val bitmap = renderFrameToBitmap(project, f, width, height)
                // Graphic control extension
                stream.write(byteArrayOf(
                    0x21, 0xF9.toByte(), 0x04, 0x04,
                    (delayMs and 0xFF).toByte(), ((delayMs shr 8) and 0xFF).toByte(),
                    0x00, 0x00
                ))
                // Image descriptor
                stream.write(byteArrayOf(
                    0x2C, 0, 0, 0, 0,
                    (width and 0xFF).toByte(), ((width shr 8) and 0xFF).toByte(),
                    (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte(),
                    0x00
                ))

                // Frame pixel bytes uncompressed
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                stream.write(0x08) // LZW minimum code size
                var chunkCount = 0
                val chunk = ByteArray(254)
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
                    chunk[chunkCount++] = gray
                    if (chunkCount == 254) {
                        stream.write(254)
                        stream.write(chunk)
                        chunkCount = 0
                    }
                }
                if (chunkCount > 0) {
                    stream.write(chunkCount)
                    stream.write(chunk, 0, chunkCount)
                }
                stream.write(0x00) // Block terminator

                onProgress((f + 1f) / totalFrames)
            }
            stream.write(0x3B) // GIF Trailer
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        var yIndex = 0
        var uIndex = width * height
        var vIndex = width * height + (width * height / 4)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (uIndex < yuv.size) yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    if (vIndex < yuv.size) yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
