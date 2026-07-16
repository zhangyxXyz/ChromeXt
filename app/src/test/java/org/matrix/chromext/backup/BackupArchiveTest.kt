package org.matrix.chromext.backup

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test
  fun plainArchiveRoundTripsTextAndBinaryFiles() {
    val source = temporary.newFile("snapshot.bin").apply { writeBytes(byteArrayOf(0, 1, 2, -1)) }
    val archive = File(temporary.root, "plain.zip")

    BackupArchive.pack(
        listOf(
            BackupInput.Serialized("manifest.json", "{\"version\":1}"),
            BackupInput.IntermediateFile("browsers/chrome.bin", source),
        ),
        archive,
    )

    val restored = BackupArchive.unpack(archive, File(temporary.root, "plain-restored"))
    assertEquals("{\"version\":1}", restored.text("manifest.json"))
    assertArrayEquals(source.readBytes(), restored.file("browsers/chrome.bin")!!.readBytes())
  }

  @Test
  fun encryptedArchiveRequiresMatchingPassword() {
    val archive = File(temporary.root, "encrypted.zip")
    BackupArchive.pack(
        listOf(BackupInput.Serialized("settings.json", "secret")), archive, "correct-password")

    val wrongPassword =
        runCatching {
              BackupArchive.unpack(archive, File(temporary.root, "wrong"), "wrong-password")
            }
            .isSuccess
    assertFalse(wrongPassword)

    val restored =
        BackupArchive.unpack(archive, File(temporary.root, "correct"), "correct-password")
    assertEquals("secret", restored.text("settings.json"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun unsafeEntryNameIsRejected() {
    BackupArchive.pack(
        listOf(BackupInput.Serialized("../outside.txt", "unsafe")),
        File(temporary.root, "unsafe.zip"),
    )
  }
}
