package com.example.demo

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class ImageRecordInitializer(
    private val imageRecordRepository: ImageRecordRepository
) {

    @PostConstruct
    fun insertImages() {
        if (imageRecordRepository.count() == 0L) {
            val batch = mutableListOf<ImageRecord>()

            for (i in 1..100_000) {
                val filename = "frame_${i.toString().padStart(6, '0')}.png"
                batch.add(ImageRecord(filename))
            }

            imageRecordRepository.saveAll(batch)
        }

        println("✅ Inserted 100,000 image records at boot.")
    }
}