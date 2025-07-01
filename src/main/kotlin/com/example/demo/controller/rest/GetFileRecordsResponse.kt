package com.example.demo.controller.rest

import com.example.demo.service.command.GetFileRecordsResult
import com.fasterxml.jackson.annotation.JsonUnwrapped

data class GetFileRecordsResponse(
    @JsonUnwrapped
    val records: List<FileRecordDTO>
) {
    data class FileRecordDTO(
        val filename: String,
        val url: String,
        val size: Long
    ) {
        companion object {
            fun from(record: GetFileRecordsResult.FileRecordDTO) = FileRecordDTO(
                filename = record.filename,
                url = record.url,
                size = record.size
            )
        }
    }

    companion object {
        fun from(result: GetFileRecordsResult) = GetFileRecordsResponse(
            records = result.records.map { FileRecordDTO.from(it) }
        )
    }
}
