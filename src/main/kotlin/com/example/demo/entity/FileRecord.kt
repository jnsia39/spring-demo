package com.example.demo.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tbl_file_record")
class FileRecord(
    val filename: String,
    val url: String,
    val size: Long
) {
    @Id
    val id: UUID = java.util.UUID.randomUUID()
}