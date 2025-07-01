package com.example.demo.service

import com.example.demo.entity.FileRecord
import com.example.demo.repository.VideoRecordRepository
import com.example.demo.entity.VideoRecord
import com.example.demo.repository.FileRecordRepository
import com.example.demo.service.command.GetFileRecordsResult
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream

@Transactional
@Service
class FileService(
    private val fileRecordRepository: FileRecordRepository,
    private val videoRecordRepository: VideoRecordRepository,

    @Value("\${file.video-path}")
    private val videoDir: String,
) {
    fun getFileRecords(): GetFileRecordsResult {
        val records = fileRecordRepository.findAll()
        return GetFileRecordsResult.from(records)
    }

    fun upload(file: MultipartFile) {
        val filename = file.originalFilename ?: "unknown"

        if (!fileRecordRepository.existsByFilename(filename)) {
            fileRecordRepository.save(
                FileRecord(
                    filename = filename,
                    url = "$FILE_SERVER_URL/${filename}",
                    size = file.size
                )
            )
        }
    }

    fun uploadVideo(filename: String, inputStream: InputStream): String {
        val file = File(videoDir, filename)

        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        val existed = videoRecordRepository.existsByFilename(filename)
        if (existed) {
            return "File with the same name already exists."
        }

        videoRecordRepository.save(VideoRecord(filename))

        return "File uploaded successfully to ${file.absolutePath}"
    }

    companion object {
        const val FILE_SERVER_URL = "http://172.16.7.76/files"
    }
}