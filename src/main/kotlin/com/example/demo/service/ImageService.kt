package com.example.demo.service

import com.example.demo.repository.ImageRecordRepository
import jakarta.transaction.Transactional
import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Random
import javax.imageio.ImageIO

@Transactional
@Service
class ImageService(
    private val imageRecordRepository: ImageRecordRepository,

    @Value("\${file.video-path}")
    private val videoDir: String,
    @Value("\${file.image-path}")
    private val imageDir: String,
) {
    fun getImage(name: String): Long {
        val dir = File(imageDir)
        val file = dir.listFiles { _, filename -> filename.endsWith(name) }?.firstOrNull()
        return file?.let { Files.size(it.toPath()) } ?: 0L
    }

    fun getImageList(pageable: Pageable): Page<String> {
        val page = imageRecordRepository.findAll(pageable)

        val urlList = page.content.mapNotNull {
            generateThumbnail(
                File(imageDir, it.url), File(imageDir, it.url)
            )

            it.url
        }

        return PageImpl(urlList, pageable, page.totalElements)
    }

    fun makeSampleImages(count: Int): String {
        generateFrames(imageDir, count)
        return "Images created."
    }

    private fun generateThumbnail(originalFile: File, thumbFile: File) {
        if (!originalFile.exists()) throw IllegalArgumentException("원본 이미지가 존재하지 않음")

        thumbFile.parentFile.mkdirs()

        Thumbnails.of(originalFile)
            .size(300, 300)
            .outputFormat("jpg")
            .toFile(thumbFile)
    }

    private fun generateFrames(outputDir: String, frameCount: Int) {
        val width = 720
        val height = 720
        val dir = File(outputDir).also { it.mkdirs() }
        val rand = Random()

        for (i in 1..frameCount) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()

            try {
                val gradient = GradientPaint(0f, 0f, Color.RED, width.toFloat(), height.toFloat(), Color.BLUE)
                g.paint = gradient
                g.fillRect(0, 0, width, height)

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        if (rand.nextDouble() < 0.02) {
                            val noiseColor = Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256))
                            image.setRGB(x, y, noiseColor.rgb)
                        }
                    }
                }

                for (j in 0 until 50) {
                    g.color = Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256))
                    val x = rand.nextInt(width)
                    val y = rand.nextInt(height)
                    val w = rand.nextInt(300)
                    val h = rand.nextInt(300)
                    if (rand.nextBoolean()) g.fillRect(x, y, w, h) else g.fillOval(x, y, w, h)
                }

                g.color = Color.WHITE
                g.font = Font("Arial", Font.BOLD, 64)
                g.drawString("Frame $i", 200, 360)
            } finally {
                g.dispose()
            }

            val fileName = "frame_%06d.png".format(i)
            ImageIO.write(image, "png", File(dir, fileName))

            if (i % 100 == 0) println("Generated $i frames...")
        }

        println("All $frameCount frames generated in: ${dir.absolutePath}")
    }
}