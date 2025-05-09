package com.example.types.storage

import org.jetbrains.exposed.sql.Table

object CapsuleContents : Table(name = "capsule_contents") {
    val id = varchar("id", 100)
    val capsuleId = varchar("capsule_id", 100).references(TimeCapsules.id)
    val contentType = enumerationByName("content_type", 10, ContentType::class)
    val content = text("content").nullable()
    val createdAt = integer("created_at")

    override val primaryKey = PrimaryKey(id)
}

enum class ContentType {
    text, image, video, audio
}