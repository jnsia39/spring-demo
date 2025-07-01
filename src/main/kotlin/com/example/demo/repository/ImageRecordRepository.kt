package com.example.demo.repository

import com.example.demo.entity.ImageRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ImageRecordRepository : JpaRepository<ImageRecord, UUID>