package com.vegadados.payloadbinext

import chromeos_update_engine.UpdateMetadata
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels
import kotlin.system.exitProcess


const val DEBUG = false

var workingFile = ""
var offset = -1L
var targetBlkSize = -1

fun log(text: Any?) {
    if (!DEBUG) return
    println("[DEBUG] $text")
}

fun ByteArray.toLong(): Long {
    var result = 0L
    for (i in this.indices) {
        result = result shl 8
        result = result or (this[i].toLong() and 0xFF)
    }
    return result
}

fun ByteArray.toInt(): Int {
    return this.toLong().toInt()
}

fun writeFromOperation(
    op: UpdateMetadata.InstallOperation,
    partOutput: RandomAccessFile,
    otaRaf: RandomAccessFile
) {
    otaRaf.seek(offset + op.dataOffset)
    partOutput.seek(op.dstExtentsList[0].startBlock * targetBlkSize)

    when (op.type) {
        UpdateMetadata.InstallOperation.Type.REPLACE_XZ -> {
            // log("Type.REPLACE_XZ")
            val fis = Channels.newInputStream(otaRaf.channel)

            val xzCis = XZCompressorInputStream(fis)
            val outputFos = FileOutputStream(partOutput.fd)
            IOUtils.copy(xzCis, outputFos)
        }

        UpdateMetadata.InstallOperation.Type.REPLACE_BZ -> {
            // log("Type.REPLACE_BZ")
            val fis = Channels.newInputStream(otaRaf.channel)

            val bis = BufferedInputStream(fis)
            val bzCis = BZip2CompressorInputStream(bis)
            val outputFos = FileOutputStream(partOutput.fd)
            IOUtils.copy(bzCis, outputFos)
        }

        UpdateMetadata.InstallOperation.Type.REPLACE -> {
            // log("Type.REPLACE")
            val data = ByteArray(op.dataLength.toInt())
            otaRaf.read(data)
            partOutput.write(data)
        }

        /*

        Type.ZERO support is disabled on this Payload Extractor.
        I did not find any payload.bin that contains Type.ZERO
        since we don't know if our current implementation works, this is disabled.

        UpdateMetadata.InstallOperation.Type.ZERO -> {
            log("Type.ZERO")
            val data = op.dataLength.toInt()
            partOutput.write(data)
        }

        */

        else -> {
            println("Error: Type ${op.type} not supported.")
            exitProcess(-1)
        }
    }
}

fun extractPartition(
    metadataPartition: UpdateMetadata.PartitionUpdate,
    otaRaf: RandomAccessFile,
    outputDirName: String
) {
    println("Extracting: ${metadataPartition.partitionName}")
    val partOutput = RandomAccessFile("$outputDirName/${metadataPartition.partitionName}.img", "rw")
    metadataPartition.operationsList.forEach { writeFromOperation(it, partOutput, otaRaf) }
    partOutput.close()
}

fun main(vararg args: String) {
    if (args.isEmpty()) {
        println("Usage: java -jar PayloadBinExtractor.jar <path/to/payload.bin> <optional: output dir>")
        exitProcess(-1)
    }

    workingFile = args[0]
    val outputDir = if (args.size > 1) args[1] else "output"

    val otaRaf = RandomAccessFile(workingFile, "r")

    // Start file header, first 4 bytes
    // Magic number of payload.bin is "CrAU"
    // HEX 43 72 41 55,  ByteArr [ 67, 114, 65, 85 ]
    val magicBytes = ByteArray(4)
    otaRaf.read(magicBytes)
    val expectedMagic = byteArrayOf(67, 114, 65, 85)
    if (!magicBytes.contentEquals(expectedMagic)) {
        println("Error: Unsupported file.")
        otaRaf.close()
        exitProcess(-1)
    }

    // Version info, next 8 bytes
    val fileFormatVersionBytes = ByteArray(8)
    otaRaf.read(fileFormatVersionBytes)
    val version = fileFormatVersionBytes.toLong()
    log("version: $version")

    // Manifest size info, next 8 bytes
    val manifestSizeBytes = ByteArray(8)
    otaRaf.read(manifestSizeBytes)
    log("manifestSizeBytes: ${manifestSizeBytes.toLong()}")

    // When version is higher than 1
    // next 4 bytes are used metadata signature
    val metadataSigSizeBytes = ByteArray(4)
    if (version > 1L) {
        otaRaf.read(metadataSigSizeBytes)
        log("metadataSigSizeBytes: ${metadataSigSizeBytes.toInt()}")
    }

    // Manifest data, next bytes varies from "manifestSizeBytes" && "metadataSigSizeBytes"
    val manifest = ByteArray(manifestSizeBytes.toInt())
    otaRaf.read(manifest)

    val metadataSig = ByteArray(metadataSigSizeBytes.toInt())
    if (version > 1L) {
        otaRaf.read(metadataSig)
    }

    // offset
    offset = otaRaf.channel.position()

    // Parse metadata from file
    val dam = UpdateMetadata.DeltaArchiveManifest.parseFrom(manifest)
    targetBlkSize = dam.blockSize

    // Require full OTAs
    if (dam.minorVersion != 0) {
        println("Error: Input file looks like a incremental OTA, and it isn't supported, make sure to use only full OTAs.")
        exitProcess(-1)
    }

    // Initialization
    println("Initializing extracting partitions")
    println("Partitions found: ${dam.partitionsList.map { it.partitionName }}")

    val outputFolder = File(outputDir)
    if (outputFolder.exists()) {
        println("Error: Output folder already exists, delete \"$outputDir\" folder to continue.")
        exitProcess(-1)
    }
    outputFolder.mkdir()

    dam.partitionsList.forEach { extractPartition(it, otaRaf, outputDir) }

    otaRaf.close()

    println("Finished.")
}
