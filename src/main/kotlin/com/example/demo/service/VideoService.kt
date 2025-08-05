package com.example.demo.service

import com.example.demo.entity.VideoEncodeFormat
import com.example.demo.repository.VideoRecordRepository
import com.example.demo.util.ffmpeg.FrameExtractor
import com.example.demo.util.ffmpeg.VideoStreamer
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread


@Transactional
@Service
class VideoService(
    private val videoRecordRepository: VideoRecordRepository,
    private val frameExtractor: FrameExtractor,
    private val videoStreamer: VideoStreamer,

    @Value("\${file.video-path}")
    private val videoDir: String,
) {
    fun getVideoList(): List<String> {
        return videoRecordRepository.findAll().map { it.filename }
    }

    fun getThumbnail(filename: String, time: String): ByteArray {
        val inputPath = Paths.get(videoDir, filename).toString()
        val command = listOf(
            "ffmpeg",
            "-ss", time,
            "-i", inputPath,
            "-f", "image2pipe",
            "-vcodec", "mjpeg",
            "-vf", "scale=240:-1",
            "-vframes", "1",
            "-nostdin",
            "-"
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(false) // 에러 스트림을 별도로 처리
            .start()

        Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { println("ffmpeg: $it") }
            }
        }.start()

        val outputBytes = process.inputStream.use { it.readBytes() }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("FFmpeg failed with exit code $exitCode")
        }

        return outputBytes
    }

    fun encodeVideo(filename: String, format: VideoEncodeFormat): String {
        File(videoDir).mkdirs()

        val inputPath = Paths.get(videoDir, filename).toString()

        val baseName = File(filename).nameWithoutExtension

        return videoStreamer.transcodeToHls(inputPath, baseName)
    }

    fun encodeVideoByStream(): String {
        val inputPath = "tcp://0.0.0.0:5460"
        return videoStreamer.transcodeToHls(inputPath, "streaming-video", isStream = true)
    }

    fun extractFrame(filename: String, timestamp: String): ByteArray {
        val inputPath = Paths.get(videoDir, filename).toString()
        val command = listOf(
            "ffmpeg",
            "-an",
            "-ss", timestamp,
            "-i", inputPath,
            "-f", "image2pipe",
            "-vcodec", "mjpeg",
            "-vframes", "1",
            "-nostdin",
            "-"
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(false) // 에러 스트림을 별도로 처리
            .start()

        Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { println("ffmpeg: $it") }
            }
        }.start()

        val outputBytes = process.inputStream.use { it.readBytes() }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("FFmpeg failed with exit code $exitCode")
        }

        return outputBytes
    }

    fun extractFrames(filename: String, frameNumbers: List<Int>): List<String> {
        val baseName = File(filename).nameWithoutExtension

        val inputPath = Paths.get(videoDir, filename).toString()

        val timestampMap = frameExtractor.extractFrameTimestamps(inputPath)
        val results = mutableMapOf<Int, String>()

        // 병렬 처리
        val threads = frameNumbers.map { frameNum ->
            Thread {
                val timestamp = timestampMap[frameNum]
                if (timestamp != null) {
                    val outputName = "$baseName/frame_$frameNum.jpg"
                    val outputPath = Paths.get(videoDir, outputName).toString()

                    frameExtractor.extractFrame(inputPath, outputPath, timestamp)

                    synchronized(results) {
                        results[frameNum] = outputName
                    }
                } else {
                    results[frameNum] = "Timestamp not found"
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        return results.toSortedMap().values.toList()
    }

    fun slowExtractFrames(filename: String, frames: List<Int>): List<String> {
        val baseName = File(filename).nameWithoutExtension

        Files.createDirectories(Paths.get(videoDir, "temp"))
        val inputPath = Paths.get(videoDir, filename).toString()
        val outputPath = Paths.get(videoDir, "temp/$baseName-frame_%d.jpg").toString()

        val selectedFrames = frames.joinToString(separator = "+") { "eq(n\\,$it)" }
        val command = listOf(
            "ffmpeg",
            "-an",
            "-skip_frame", "nokey",
            "-i", inputPath,
            "-vf", "select='$selectedFrames'",
            "-vsync", "0",
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

        return frames.map { "$baseName-frame_$it.jpg" }
    }

    fun resetEncoding(): String {
        val dir = File(videoDir)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }

        videoRecordRepository.deleteAll()

        return "Reset encoding and deleted all video files."
    }
}