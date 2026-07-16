package org.matrix.chromext.backup

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod

sealed interface BackupInput {
  val name: String

  fun openStream(): InputStream

  data class Serialized(override val name: String, val content: ByteArray) : BackupInput {
    constructor(name: String, content: String) : this(name, content.toByteArray(Charsets.UTF_8))

    override fun openStream(): InputStream = ByteArrayInputStream(content)
  }

  data class IntermediateFile(override val name: String, val file: File) : BackupInput {
    override fun openStream(): InputStream = file.inputStream()
  }
}

data class RestoredBackup(val directory: File) {
  fun file(name: String): File? =
      File(directory, BackupArchive.safeEntryName(name)).takeIf(File::isFile)

  fun text(name: String): String? = file(name)?.readText()
}

object BackupArchive {
  const val MAX_ENTRY_COUNT = 2_000
  const val MAX_UNPACKED_BYTES = 512L * 1024L * 1024L

  fun pack(inputs: List<BackupInput>, target: File, password: String = ""): File {
    require(inputs.isNotEmpty()) { "Backup contents cannot be empty" }
    target.parentFile?.mkdirs()
    target.delete()
    val names = hashSetOf<String>()
    val zip = ZipFile(target, password.takeIf(String::isNotEmpty)?.toCharArray())
    inputs.forEach { input ->
      val name = safeEntryName(input.name)
      require(names.add(name)) { "Duplicate backup entry: $name" }
      val parameters =
          ZipParameters().apply {
            fileNameInZip = name
            isEncryptFiles = password.isNotEmpty()
            if (isEncryptFiles) {
              encryptionMethod = EncryptionMethod.AES
              aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
          }
      input.openStream().buffered().use { zip.addStream(it, parameters) }
    }
    return target
  }

  fun unpack(source: File, targetDirectory: File, password: String = ""): RestoredBackup {
    targetDirectory.deleteRecursively()
    targetDirectory.mkdirs()
    val zip = ZipFile(source, password.takeIf(String::isNotEmpty)?.toCharArray())
    val entries = zip.fileHeaders
    require(entries.size <= MAX_ENTRY_COUNT) { "Backup contains too many files" }
    var declaredBytes = 0L
    entries.forEach { entry ->
      val name = safeEntryName(entry.fileName)
      val output = File(targetDirectory, name)
      val root = targetDirectory.canonicalFile
      require(output.canonicalFile.toPath().startsWith(root.toPath())) {
        "Unsafe ZIP entry: ${entry.fileName}"
      }
      declaredBytes += entry.uncompressedSize.coerceAtLeast(0L)
      require(declaredBytes <= MAX_UNPACKED_BYTES) { "Backup is too large" }
    }
    var actualBytes = 0L
    entries.forEach { entry ->
      val output = File(targetDirectory, safeEntryName(entry.fileName))
      if (entry.isDirectory) {
        output.mkdirs()
      } else {
        output.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
          output.outputStream().buffered().use { out ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              actualBytes += count
              require(actualBytes <= MAX_UNPACKED_BYTES) { "Backup is too large" }
              out.write(buffer, 0, count)
            }
          }
        }
      }
    }
    return RestoredBackup(targetDirectory)
  }

  internal fun safeEntryName(name: String): String {
    val normalized = name.replace('\\', '/').trimStart('/')
    require(
        normalized.isNotBlank() && normalized.split('/').none { it == ".." || it.isBlank() }) {
          "Unsafe backup entry name: $name"
        }
    return normalized
  }
}
