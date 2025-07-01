package com.example.demo.repository

import com.example.demo.entity.FileRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileRecordRepository: JpaRepository<FileRecord, UUID> {
    fun existsByFilename(filename: String): Boolean
}