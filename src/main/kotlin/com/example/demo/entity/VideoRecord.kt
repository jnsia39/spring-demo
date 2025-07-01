package com.example.demo.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tbl_video_record")
class VideoRecord(
    val filename: String,
) {
    @Id
    val id: UUID = UUID.randomUUID()
}