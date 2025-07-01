package com.example.demo.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tbl_image_record")
class ImageRecord(
    @Column(nullable = true)
    val url: String? = null,

    @Id
    val id: UUID = UUID.randomUUID()
)