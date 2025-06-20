package com.example.demo

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Random
import javax.imageio.ImageIO
import kotlin.concurrent.thread

@RestController
@RequestMapping("/api/v1/files")
class FileController {
    val basePath = "./uploaded-files"
    val ffmpegPath = "C:\\dev\\ffmpeg\\bin\\ffmpeg.exe" // FFmpeg 실행 파일 경로

    @PostMapping("/video")
    fun getSampleVideo(): String {
        val inputPath = "$basePath/video/sample-video.mp4" // 입력 파일 경로
        val outputM3u8 = "$basePath/video/stream.m3u8" // 출력 M3U8 파일 경로
        val segment0 = "$basePath/video/stream0.ts"

        if (File(outputM3u8).exists()) {
            return "이미 스트리밍 준비 완료됨: /hls/stream.m3u8"
        }

        File(basePath).mkdirs()

        // ffmpeg 실행
        val command = listOf(
            ffmpegPath,
            "-i", inputPath,
            "-c:v", "libx264",            // 비디오 코덱을 H.264로 설정
            "-c:a", "aac",                // 오디오 코덱을 AAC로 설정
            "-f", "hls",                  // 출력 포맷을 HLS로 설정
            "-hls_time", "2",             // 각 세그먼트의 길이를 2초로 설정
            "-hls_list_size", "0",        // 재생목록에 포함될 세그먼트 수 무제한 (전체 목록 유지)
            "-hls_flags", "independent_segments", // 각 세그먼트를 독립적으로 디코딩 가능하게 설정
            outputM3u8
        )

        // FFmpeg 백그라운드 실행
        thread {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().forEachLine {
                println("[FFmpeg] $it")
            }

            process.waitFor()
            println("FFmpeg 종료됨 (코드: ${process.exitValue()})")
        }

        // 세그먼트 생성 대기 (최대 5초, 100ms 단위로 확인)
        val maxWaitMs = 5000L
        val intervalMs = 100L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (File(segment0).exists()) {
                return "/stream.m3u8"
            }

            Thread.sleep(intervalMs)
        }

        return "⚠️ FFmpeg 실행되었지만 아직 세그먼트 생성 안 됨"
    }

    @PostMapping("/video/stop")
    fun stopSampleVideo(): String {
        val outputPath = "$basePath/video"
        val dir = File(outputPath)

        val streamFile = dir.listFiles { _, name -> name.endsWith(".m3u8") }
        streamFile?.forEach { it.delete() }

        val segments = dir.listFiles { _, name -> name.endsWith(".ts") }
        segments?.forEach { it.delete() }

        return "Sample video stopped."
    }

    @GetMapping("/video/frame")
    fun getSampleFrame(
        @RequestParam time: String?,
        @RequestParam frame: Int?
    ): String {
        if (time.isNullOrBlank() && frame == null) return "입력 없음"
        if (time != null && frame != null) return "입력 많음"

        val inputPath = "$basePath/video/sample-video.mp4" // 입력 파일 경로
        val outputPath = "$basePath/video"

        val dir = File(outputPath)
        if (!dir.exists()) dir.mkdirs()

        val existedFile = dir.listFiles { _, name -> name.endsWith(".jpg") }
        existedFile?.forEach { it.delete() }

        val filename = if (time != null) "sample-time.jpg" else "sample-frame.jpg"
        val command = if (time != null) {
            listOf(
                ffmpegPath,
                "-ss", time,
                "-i", inputPath,
                "-vframes", "1",
                "-q:v", "2",
                "${outputPath}/${filename}"
            )
        } else {
            listOf(
                ffmpegPath,
                "-i", inputPath,
                "-vf", "select='eq(n\\,${frame})'",
                "-vsync", "0",
                "-frames:v", "1",
                "${outputPath}/${filename}"
            )
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        thread {
            process.inputStream.bufferedReader().forEachLine {
                println(it)
            }
        }

        val exitCode = process.waitFor()

        println("FFmpeg 종료 코드: $exitCode")

        return filename
    }

    @PostMapping("/video/upload")
    fun uploadVideo(
        @RequestParam("video") video: MultipartFile
    ): String {
        val outputPath = Paths.get(basePath, "video").toAbsolutePath().normalize().toFile()

        if (!outputPath.exists()) outputPath.mkdirs()

        val file = File(outputPath, "sample-video.mp4")
        video.transferTo(file)

        return "Video uploaded."
    }

    @GetMapping("/image/{name}")
    fun getImage(@PathVariable name: String): Long {
        val filePath = "$basePath/image"
        val dir = File(filePath)

        val file = dir.listFiles { _, filename -> filename.endsWith(name) }?.firstOrNull()
        if (file == null) return 0

        return Files.size(file.toPath())
    }

    @GetMapping("/image/make")
    fun makeSampleImages(
        @RequestParam(defaultValue = "100_000") count: Int
    ): ResponseEntity<String> {
        val outputPath = "$basePath/image"
        generateFrames(outputPath, count)
        return ResponseEntity.ok("Images created.")
    }

    @GetMapping("/image/list")
    fun getImageList(
        pageable: Pageable
    ): Page<String> {
        val outputPath = "$basePath/image"
        val dir = File(outputPath)

        if (!dir.exists()) dir.mkdirs()

        val allFiles = dir.listFiles { _, name -> name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()

        val fromIndex = (pageable.pageNumber - 1) * pageable.pageSize
        val toIndex = (fromIndex + pageable.pageSize).coerceAtMost(allFiles.size)

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

        val rand = Random()

        for (i in 1..frameCount) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()

            try {
                // 배경 그라디언트
                val gradient = GradientPaint(0f, 0f, Color.RED, width.toFloat(), height.toFloat(), Color.BLUE)
                (g as Graphics2D).paint = gradient
                g.fillRect(0, 0, width, height)

                // 노이즈
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        if (rand.nextDouble() < 0.02) {
                            val noiseColor = Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 255)
                            image.setRGB(x, y, noiseColor.rgb)
                        }
                    }
                }

                // 랜덤 도형
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

                // 텍스트
                g.color = Color.WHITE
                g.font = Font("Arial", Font.BOLD, 64)
                g.drawString("Frame $i", 200, 360)
            } finally {
                g.dispose() // 꼭 해줘야 메모리 누수 방지됨
            }

            // 파일 저장
            val fileName = String.format("frame_%06d.png", i)
            val file = File(dir, fileName)
            ImageIO.write(image, "png", file)

            // Hint: let GC know image is no longer needed
            // Not strictly necessary, but may help under load
            // (just sets to null to drop reference)
            // image = null

            if (i % 100 == 0) println("Generated $i frames...")
        }

        println("All $frameCount frames generated in: ${dir.absolutePath}")
    }
}