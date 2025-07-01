package com.example.demo.repository

import com.example.demo.entity.VideoRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VideoRecordRepository: JpaRepository<VideoRecord, UUID> {
    fun existsByFilename(filename: String): Boolean
}