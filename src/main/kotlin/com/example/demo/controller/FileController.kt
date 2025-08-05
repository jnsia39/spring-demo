package com.example.demo.controller

import com.example.demo.controller.rest.GetFileRecordsResponse
import com.example.demo.service.FileService
import com.example.demo.service.ImageService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileService: FileService,
    private val imageService: ImageService,
) {
    @GetMapping
    fun getFiles(): GetFileRecordsResponse {
        val result = fileService.getFileRecords()
        return GetFileRecordsResponse.from(result)
    }

    @PostMapping("/upload")
    fun uploadFile(@RequestBody file: MultipartFile) {
        fileService.upload(file)
    }

    @GetMapping("/image/{name}")
    fun getImage(@PathVariable name: String): Long {
        return imageService.getImage(name)
    }

    @GetMapping("/image/make")
    fun makeSampleImages(@RequestParam(defaultValue = "100_000") count: Int): String {
        return imageService.makeSampleImages(count)
    }

    @GetMapping("/image/list")
    fun getImageList(pageable: Pageable): Page<String> {
        return imageService.getImageList(pageable)
    }
}