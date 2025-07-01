package com.example.demo.service

import com.example.demo.entity.VideoEncodeFormat
import com.example.demo.repository.VideoRecordRepository
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.thread

@Transactional
@Service
class VideoService(
    private val videoRecordRepository: VideoRecordRepository,

    @Value("\${file.video-path}")
    private val videoDir: String,
) {
    fun getVideoList(): List<String> {
        return videoRecordRepository.findAll().map { it.filename }
    }

    fun encodeVideo(filename: String, format: VideoEncodeFormat): String {
        File(videoDir).mkdirs()

        val inputPath = Paths.get(videoDir, filename).toString()

        val baseName = File(filename).nameWithoutExtension
        val (command, outputPath, segment0) = buildFfmpegCommand(format, inputPath, baseName)

        if (File(outputPath).exists()) {
            return "이미 스트리밍 준비 완료됨: /${File(outputPath).name}"
        }

        runFfmpegInBackground(command, baseName)

        return waitForSegment(segment0)
    }

    fun resetEncoding(): String {
        val dir = File(videoDir)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }

        videoRecordRepository.deleteAll()

        return "Reset encoding and deleted all video files."
    }

    private fun buildFfmpegCommand(format: VideoEncodeFormat, inputPath: String, baseName: String): Triple<List<String>, String, String> {
        val commonCommand = arrayOf(
            "-g", "120",
            "-an",
            "-keyint_min", "120",
            "-sc_threshold", "0",
            "-pix_fmt", "yuv420p",
            "-c:v", "h264_amf",
            "-c:a", "aac"
        )

        File("$videoDir/$baseName").mkdirs()

        return if (format == VideoEncodeFormat.HLS) {
            val outputPath = Paths.get(videoDir, "$baseName.m3u8").toString()
            val segment0 = Paths.get(videoDir, "$baseName/stream1.ts").toString()
            val command = listOf(
                "ffmpeg",
                "-i", inputPath,
                *commonCommand,
                "-f", "hls",
                "-hls_time", "6",
                "-hls_list_size", "0",
                "-hls_flags", "independent_segments",
                "-hls_playlist_type", "event",
                "-hls_base_url", "/${baseName}/",
                "-hls_segment_filename", "${videoDir}/$baseName/stream%d.ts",
                outputPath
            )

            Triple(command, outputPath, segment0)
        } else {
            val outputPath = Paths.get(videoDir, "$baseName.mpd").toString()
            val segment0 = Paths.get(videoDir, "chunk-stream-1.m4s").toString()
            val command = listOf(
                "ffmpeg",
                "-i", inputPath,
                *commonCommand,
                "-f", "dash",
                "-seg_duration", "10",
                "-use_template", "1",
                "-use_timeline", "1",
                "-live", "1",
                outputPath
            )

            Triple(command, outputPath, segment0)
        }
    }

    private fun runFfmpegInBackground(command: List<String>, baseName: String) {
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
    }

    private fun waitForSegment(segmentPath: String): String {
        val maxWaitMs = 50000L
        val intervalMs = 100L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (File(segmentPath).exists()) {
                return "OK"
            }
            Thread.sleep(intervalMs)
        }

        return "FAIL"
    }
}