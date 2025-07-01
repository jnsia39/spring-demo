package com.example.demo.service.command

import com.example.demo.entity.FileRecord

data class GetFileRecordsResult(
    val records: List<FileRecordDTO>
) {
    data class FileRecordDTO(
        val filename: String,
        val url: String,
        val size: Long
    ) {
        companion object {
            fun from(record: FileRecord) = FileRecordDTO(
                filename = record.filename,
                url = record.url,
                size = record.size
            )
        }
    }

    companion object {
        fun from(records: List<FileRecord>) = GetFileRecordsResult(
            records.map { FileRecordDTO.from(it) }
        )
    }
}
