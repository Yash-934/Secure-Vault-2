package com.example.domain.model

import com.example.data.VaultItem

enum class VaultMode {
    LOCKED,
    REAL,
    DECOY
}

/**
 * Pre-populated dummy media items for the Decoy Vault.
 * When an intruder forces the user to enter a PIN, entering the Decoy PIN
 * opens this Decoy Vault displaying innocent dummy items (recipes, wallpapers, notes)
 * protecting the user's real encrypted files.
 */
object DecoyDummyData {
    val dummyVaultItems = listOf(
        VaultItem(
            id = 9001,
            originalName = "favorite_pasta_recipe.jpg",
            encryptedFileName = "decoy_1.bin",
            mimeType = "image/jpeg",
            sizeBytes = 2450000L,
            addedTimestamp = System.currentTimeMillis() - 864000000L,
            isVideo = false
        ),
        VaultItem(
            id = 9002,
            originalName = "mountain_vacation_sunset.jpg",
            encryptedFileName = "decoy_2.bin",
            mimeType = "image/jpeg",
            sizeBytes = 3800000L,
            addedTimestamp = System.currentTimeMillis() - 432000000L,
            isVideo = false
        ),
        VaultItem(
            id = 9003,
            originalName = "puppy_training_clip.mp4",
            encryptedFileName = "decoy_3.bin",
            mimeType = "video/mp4",
            sizeBytes = 18400000L,
            addedTimestamp = System.currentTimeMillis() - 172800000L,
            isVideo = true
        ),
        VaultItem(
            id = 9004,
            originalName = "tax_receipts_sample.pdf",
            encryptedFileName = "decoy_4.bin",
            mimeType = "application/pdf",
            sizeBytes = 1200000L,
            addedTimestamp = System.currentTimeMillis() - 86400000L,
            isVideo = false
        ),
        VaultItem(
            id = 9005,
            originalName = "project_notes_public.txt",
            encryptedFileName = "decoy_5.bin",
            mimeType = "text/plain",
            sizeBytes = 45000L,
            addedTimestamp = System.currentTimeMillis() - 43200000L,
            isVideo = false
        )
    )
}
