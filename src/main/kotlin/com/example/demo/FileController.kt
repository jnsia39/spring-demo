package com.example.demo

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Random
import javax.imageio.ImageIO

@RestController
@RequestMapping("/api/v1/files")
class FileController {
    val imageDirPath = "./uploaded-files/"

    @GetMapping("/image/make")
    fun makeSampleImages(
        @RequestParam(defaultValue = "100_000") count: Int
    ): ResponseEntity<String> {
        generateFrames(imageDirPath, count)
        return ResponseEntity.ok("Images created.")
    }

    @GetMapping("/image/list")
    fun getImageList(
        pageable: Pageable
    ): Page<String> {
        val dir = File(imageDirPath)
        if (!dir.exists()) dir.mkdirs()

        val allFiles = dir.listFiles { _, name -> name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()

        val fromIndex = (pageable.pageNumber - 1) * pageable.pageSize
        val toIndex = (fromIndex + pageable.pageSize).coerceAtMost(allFiles.size)

//        if (fromIndex >= allFiles.size) return Page.empty()

        val urls = allFiles.subList(fromIndex, toIndex).map {
            it.name
        }

        return PageImpl(urls, pageable, allFiles.size.toLong())
    }

    fun generateFrames(outputDir: String, frameCount: Int) {
        val width = 720
        val height = 720
        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()

        for (i in 1..frameCount) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()

            val gradient = GradientPaint(0f, 0f, Color.RED, width.toFloat(), height.toFloat(), Color.BLUE)
            (g as Graphics2D).paint = gradient
            g.fillRect(0, 0, width, height)

            val rand = Random()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (rand.nextDouble() < 0.02) { // 2% 노이즈
                        val noiseColor = Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 255)
                        image.setRGB(x, y, noiseColor.rgb)
                    }
                }
            }

            for (j in 0 until 50) {
                g.color = Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 255)
                val x = rand.nextInt(width)
                val y = rand.nextInt(height)
                val w = rand.nextInt(300)
                val h = rand.nextInt(300)
                if (rand.nextBoolean()) {
                    g.fillRect(x, y, w, h)
                } else {
                    g.fillOval(x, y, w, h)
                }
            }

            g.color = Color.WHITE
            g.font = Font("Arial", Font.BOLD, 64)
            g.drawString("Frame $i", 200, 360)

            g.dispose()

            val fileName = String.format("frame_%06d.png", i)
            val file = File(dir, fileName)
            ImageIO.write(image, "png", file)

            if (i % 100 == 0) println("Generated $i frames...")
        }

        println("All $frameCount frames generated in: ${dir.absolutePath}")
    }
}