package com.example.demo.util.ffmpeg

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread

@Component
class VideoStreamer(
    @Value("\${file.video-path}")
    private val videoDir: String,
) {

    fun transcodeToHls(
        inputPath: String,
        baseName: String,
        segmentTime: Int = 10,
        isStream: Boolean = false,
    ): String {
        val outputPath = Paths.get(videoDir, "$baseName.m3u8").toString()
        Files.createDirectories(Paths.get(videoDir, baseName))

        if (File(outputPath).exists()) {
            return "이미 스트리밍 준비 완료됨: /${File(outputPath).name}"
        }

        val command = buildList {
            add("ffmpeg")
            if (isStream) {
                add("-listen")
                add("1")
            }
            add("-i")
            add(inputPath)
            addAll(COMMON_COMMAND)
            add("-f")
            add("hls")
            add("-hls_time")
            add("4")
            add("-hls_list_size")
            add("0")
            add("-hls_flags")
            add("independent_segments")
            add("-hls_playlist_type")
            add("event")
            add("-hls_base_url")
            add("/${baseName}/")
            add("-hls_segment_filename")
            add("${videoDir}/$baseName/stream%d.ts")
            add(outputPath)
        }

        runFfmpegInBackground(command)

        val segment = Paths.get(videoDir, "$baseName/stream3.ts").toString()

        return waitUntilSegmentIsCreated(segment)
    }

    fun transcodeToDash(
        inputPath: String,
        baseName: String,
    ): String {
        val outputPath = Paths.get(videoDir, "$baseName.mpd").toString()
        Files.createDirectories(Paths.get(videoDir, baseName))

        if (File(outputPath).exists()) {
            return "이미 스트리밍 준비 완료됨: /${File(outputPath).name}"
        }

        val command = listOf(
            "ffmpeg",
            "-i", inputPath,
            *COMMON_COMMAND,
            "-f", "dash",
            "-seg_duration", "10",
            "-use_template", "1",
            "-use_timeline", "1",
            "-live", "1",
            outputPath
        )

        runFfmpegInBackground(command)

        val segment = Paths.get(videoDir, "chunk-stream-1.m4s").toString()

        return waitUntilSegmentIsCreated(segment)
    }

    private fun runFfmpegInBackground(command: List<String>) {
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

    private fun waitUntilSegmentIsCreated(segment: String): String {
        val maxWaitMs = 50000L
        val intervalMs = 100L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (File(segment).exists()) {
                return "OK"
            }
            Thread.sleep(intervalMs)
        }

        return "FAIL"
    }

    companion object {
        val COMMON_COMMAND = arrayOf(
            "-g", "120",
            "-an",
            "-keyint_min", "120",
            "-sc_threshold", "0",
            "-pix_fmt", "yuv420p",
            "-c:v", "h264_amf",
            "-c:a", "aac"
        )
    }
}