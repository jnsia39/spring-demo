package com.example.demo.util.ffmpeg

import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
class FrameExtractor {

    fun extractFrameTimestamps(videoPath: String): Map<Int, Double> {
        val command = listOf(
            "ffprobe",
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "packet=pts_time",
            "-of", "csv=p=0",
            videoPath
        )

        val process = ProcessBuilder(command).start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        return reader.lineSequence()
            .mapIndexedNotNull { index, line -> line.toDoubleOrNull()?.let { index to it } }
            .toMap()
            .also { process.waitFor() }
    }

    // 단일 프레임 추출
    fun extractFrame(inputPath: String, outputPath: String, timestamp: Double) {
        val command = listOf(
            "ffmpeg",
            "-y", // overwrite output file if it exists
            "-ss", "$timestamp",
            "-i", inputPath,
            "-vf", "scale=360:-1",
            "-frames:v", "1",
            "-q:v", "2",
            outputPath
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .inheritIO()
            .start()

        process.waitFor()
    }

}