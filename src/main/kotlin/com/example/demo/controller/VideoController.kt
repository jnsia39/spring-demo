package com.example.demo.controller

import com.example.demo.entity.VideoEncodeFormat
import com.example.demo.service.FileService
import com.example.demo.service.VideoService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread

@RestController
@RequestMapping("/api/v1/videos")
class VideoController(
    private val fileService: FileService,
    private val videoService: VideoService,

    @Value("\${file.video-path}")
    private val videoDir: String,
) {
    @GetMapping
    fun getVideoList(): List<String> {
        return videoService.getVideoList()
    }

    @GetMapping("/stream")
    fun encodeVideoByStream(): String {
        return videoService.encodeVideoByStream()
    }

    @GetMapping("/{filename}")
    fun getCroppedFrame(
        @PathVariable filename: String,
        @RequestParam width: Int,
        @RequestParam height: Int,
        @RequestParam x: Int,
        @RequestParam y: Int
    ): String {
        val inputPath = Paths.get(videoDir, filename).toString()
        val outputPath = Paths.get(videoDir, "cropped-$filename").toString()
        val command = listOf(
            "ffmpeg",
            "-i", inputPath,
            "-vf", "crop=$width:$height:$x:$y",
            "-vframes", "1",
            outputPath
        )

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

        return "cropped-$filename"
    }

    @GetMapping("/thumbnail/{filename}")
    fun getThumbnail(
        @PathVariable filename: String,
        @RequestParam time: String = "00:00:01.000",
        response: HttpServletResponse,
    ) {
        val bytes = videoService.getThumbnail(filename, time)

        response.contentType = "image/jpeg"
        response.outputStream.write(bytes)
    }

    @GetMapping("/frame/{filename}")
    fun getFrames(
        @PathVariable filename: String,
        @RequestParam frames: List<Int>
    ): List<String> {
//        return videoService.slowExtractFrames(filename, frames)
        return videoService.extractFrames(filename, frames);
    }

    @PostMapping("/upload")
    fun uploadVideo(
        @RequestParam("video") video: MultipartFile
    ): String {
        val filename = video.originalFilename ?: "sample-video.mp4"
        val result = fileService.uploadVideo(
            filename,
            video.inputStream,
        )

        return result
    }

    @PostMapping("/encode")
    fun encodeVideo(
        @RequestParam("filename") filename: String,
        @RequestParam("format") format: VideoEncodeFormat = VideoEncodeFormat.HLS
    ): String {
        return videoService.encodeVideo(filename, format)
    }

    @PostMapping("/reset")
    fun resetEncoding(): String {
        return videoService.resetEncoding()
    }

    @GetMapping("/duration/{filename}")
    fun getVideoDuration(
        @PathVariable filename: String
    ): Int {
        val inputPath = Paths.get(videoDir, filename).toString()

        val command = listOf("ffmpeg", "-i", inputPath, "-hide_banner")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()

        val pattern = Regex("""Duration:\s+(\d{2}):(\d{2}):(\d{2})""")
        val match = pattern.find(output)
        return match?.let {
            it.groupValues[1].toInt() * 60 * 60 + it.groupValues[2].toInt() * 60 + it.groupValues[3].toInt()
        } ?: throw RuntimeException("비디오 길이 정보를 찾을 수 없습니다.\nFFmpeg 출력:\n$output")
    }
}