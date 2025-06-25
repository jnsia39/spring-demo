package com.example.demo

import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Random
import java.util.regex.Pattern
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val imageRecordRepository: ImageRecordRepository,

    @Value("\${file.video-path}")
    private val videoDir: String,
    @Value("\${file.image-path}")
    private val imageDir: String,
) {
    @PostMapping("/video")
    fun encodeSampleVideo(@RequestParam format: String = "MPEG-DASH"): String {
        File(videoDir).mkdirs()

        val inputPath = Paths.get(videoDir, "sample-video.mp4").toString()

        val command: List<String>
        val segment0: String

        if (format == "MPEG-DASH") {
            val outputPath = Paths.get(videoDir, "stream.mpd").toString()
            segment0 = Paths.get(videoDir, "chunk-stream0-00001.m4s").toString()

            if (File(outputPath).exists()) {
                return "이미 스트리밍 준비 완료됨: /stream.mpd"
            }

            command = listOf(
                "ffmpeg",
                "-i", inputPath,
                "-g", "120",
                "-keyint_min", "120",
                "-sc_threshold", "0",
                "-c:v", "h264_amf",         // 비디오 코덱 설정 (AMF 하드웨어 인코딩)
                "-c:a", "aac",              // 오디오 코덱 설정
                "-f", "dash",               // 출력 포맷을 MPEG-DASH로 설정
                "-seg_duration", "10",      // 각 세그먼트의 길이를 10초로 설정
                "-use_template", "1",       // 세그먼트 이름에 템플릿 사용 (e.g., segment$Number$.m4s)
                "-use_timeline", "1",       // 타임라인 기반 MPD 사용
                outputPath
            )
        } else {
            val outputPath = Paths.get(videoDir, "stream.m3u8").toString()
            segment0 = Paths.get(videoDir, "stream0.ts").toString()

            if (File(outputPath).exists()) {
                return "이미 스트리밍 준비 완료됨: /stream.m3u8"
            }

            command = listOf(
                "ffmpeg",
                "-i", inputPath,
                "-g", "120",
                "-keyint_min", "120",
                "-sc_threshold", "0",
                "-c:v", "h264_amf",         // 비디오 코덱을 H.264로 설정
                "-c:a", "aac",                // 오디오 코덱을 AAC로 설정
                "-f", "hls",                  // 출력 포맷을 HLS로 설정
                "-hls_time", "10",             // 각 세그먼트의 길이를 10초로 설정
                "-hls_list_size", "0",        // 재생목록에 포함될 세그먼트 수 무제한 (전체 목록 유지)
                "-hls_flags", "independent_segments", // 각 세그먼트를 독립적으로 디코딩 가능하게 설정
                outputPath
            )
        }

        // FFmpeg 백그라운드 실행
        thread {
            val process = ProcessBuilder(command)
                .directory(File(videoDir))
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().forEachLine {
                println("[FFmpeg] $it")
            }

            process.waitFor()
            println("FFmpeg 종료됨 (코드: ${process.exitValue()})")
        }

        // 세그먼트 생성 대기 (최대 50초, 100ms 단위로 확인)
        val maxWaitMs = 50000L
        val intervalMs = 100L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (File(segment0).exists()) {
                return "OK"
            }

            Thread.sleep(intervalMs)
        }

        return "FAIL"
    }

    @PostMapping("/video/stop")
    fun stopSampleVideo(): String {
        val dir = File(videoDir)

        dir.listFiles { _, name -> name.endsWith(".m3u8") }?.forEach { it.delete() }
        dir.listFiles { _, name -> name.endsWith(".mpd") }?.forEach { it.delete() }
        dir.listFiles { _, name -> name.endsWith(".ts") }?.forEach { it.delete() }
        dir.listFiles { _, name -> name.endsWith(".m4s") }?.forEach { it.delete() }

        return "Sample video stopped."
    }

    @GetMapping("/video/frame")
    fun getSampleFrame(
        @RequestParam time: String?,
        @RequestParam frame: Int?
    ): String {
        if (time.isNullOrBlank() && frame == null) return "입력 없음"
        if (time != null && frame != null) return "입력 많음"

        val inputPath = Paths.get(videoDir, "sample-video.mp4").toString()
        val outputPath = File(videoDir).also { it.mkdirs() }

        outputPath.listFiles { _, name -> name.endsWith(".jpg") }?.forEach { it.delete() }

        val filename = if (time != null) "sample-time.jpg" else "sample-frame.jpg"
        val outputFile = Paths.get(videoDir, filename).toString()

        val command = if (time != null) {
            listOf("ffmpeg", "-ss", time, "-i", inputPath, "-vframes", "1", "-q:v", "2", outputFile)
        } else {
            listOf("ffmpeg", "-i", inputPath, "-vf", "select='eq(n\\,$frame)'", "-vsync", "0", "-frames:v", "1", outputFile)
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

//    @PostMapping("/video/upload")
//    fun uploadVideo(
//        @RequestParam("video") video: MultipartFile
//    ): String {
//        val dir = File(videoDir).also { it.mkdirs() }
//
//        val dest = File(dir, "sample-video.mp4")
//        video.transferTo(dest)
//
//        encodeSampleVideo()
//
//        return "Video uploaded."
//    }

    @PostMapping("/video/upload")
    fun uploadVideo(
        @RequestParam("chunk") chunk: MultipartFile,
        @RequestParam("index") index: Int,
        @RequestParam("total") total: Int
    ): ResponseEntity<String> {
        val uploadDir = Paths.get(videoDir)

        // 업로드 디렉토리 생성
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir)
        }

        // 청크 파일 저장 (예: filename_0, filename_1 ...)
        val chunkFile = uploadDir.resolve("sample-video_$index")
        chunk.inputStream.use { input ->
            Files.copy(input, chunkFile, StandardCopyOption.REPLACE_EXISTING)
        }

        // 모든 청크가 업로드되었는지 확인 후 병합
        if ((0 until total).all { Files.exists(uploadDir.resolve("sample-video_$it")) }) {
            Paths.get(videoDir, "sample-video.mp4").deleteIfExists()
            val mergedFile = uploadDir.resolve("sample-video.mp4")
            Files.newOutputStream(mergedFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { output ->
                for (i in 0 until total) {
                    val part = uploadDir.resolve("sample-video_$i")
                    Files.newInputStream(part).use { input -> input.copyTo(output) }
                    Files.delete(part) // 병합 후 청크 삭제
                }
            }

            return ResponseEntity.ok("업로드 및 병합 완료")
        }

        return ResponseEntity.ok("청크 업로드 완료")
    }

    @GetMapping("/image/{name}")
    fun getImage(@PathVariable name: String): Long {
        val dir = File(imageDir)
        val file = dir.listFiles { _, filename -> filename.endsWith(name) }?.firstOrNull()
        return file?.let { Files.size(it.toPath()) } ?: 0L
    }

    @GetMapping("/image/make")
    fun makeSampleImages(@RequestParam(defaultValue = "100_000") count: Int): ResponseEntity<String> {
        generateFrames(imageDir, count)
        return ResponseEntity.ok("Images created.")
    }

    @GetMapping("/image/list")
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

    @GetMapping("/video/duration")
    fun getVideoDuration(): Int {
        val inputPath = Paths.get(videoDir, "sample-video.mp4").toString()

        val command = listOf("ffmpeg", "-i", inputPath, "-hide_banner")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // 모든 출력 읽어 들이기
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()

        // "Duration: 00:01:23.45" 부분만 꺼내기
        val pattern = Regex("""Duration:\s+(\d{2}):(\d{2}):(\d{2})""")
        val match = pattern.find(output)
        return match?.let {
            it.groupValues[1].toInt() * 60 * 60 + it.groupValues[2].toInt() * 60 + it.groupValues[3].toInt()
        } ?: throw RuntimeException("비디오 길이 정보를 찾을 수 없습니다.\nFFmpeg 출력:\n$output")
    }
}