package com.xnvalabs.smarteyex.data.glasses

import java.util.UUID
import java.util.zip.CRC32

/** Status yang dilaporkan firmware kacamata. Semua nilai berasal dari paket STATUS, bukan tebakan aplikasi. */
data class GlassesStatus(
    val batteryPercent: Int,
    val cameraReady: Boolean,
    val micReady: Boolean,
    val charging: Boolean,
    val ledOn: Boolean,
    val protocolVersion: Int,
)

/** Event yang dikirim kacamata lewat characteristic EVENT. */
sealed interface GlassesEvent {
    data object Pong : GlassesEvent
    data class Status(val status: GlassesStatus) : GlassesEvent
    data class FrameBegin(val frameId: Int, val totalLength: Int) : GlassesEvent
    data class FrameEnd(val frameId: Int, val crc32: Long) : GlassesEvent
    data class Error(val code: Int) : GlassesEvent
}

/**
 * Kontrak biner antara aplikasi Android dan firmware ESP32 (lihat firmware/README.md).
 * Semua angka multi-byte memakai little-endian.
 */
object GlassesProtocol {
    const val VERSION = 1

    val SERVICE_UUID: UUID = UUID.fromString("7e5e0001-5a1e-4e58-9c1d-0a5e7e5e0001")
    /** App -> kacamata (write). */
    val COMMAND_UUID: UUID = UUID.fromString("7e5e0002-5a1e-4e58-9c1d-0a5e7e5e0001")
    /** Kacamata -> app (notify): STATUS, PONG, FRAME_BEGIN, FRAME_END, ERROR. */
    val EVENT_UUID: UUID = UUID.fromString("7e5e0003-5a1e-4e58-9c1d-0a5e7e5e0001")
    /** Kacamata -> app (notify): potongan data JPEG. */
    val FRAME_UUID: UUID = UUID.fromString("7e5e0004-5a1e-4e58-9c1d-0a5e7e5e0001")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val OP_PING = 0x01
    const val OP_GET_STATUS = 0x02
    const val OP_CAPTURE = 0x03
    const val OP_SET_LED = 0x04

    const val EV_PONG = 0x81
    const val EV_STATUS = 0x82
    const val EV_FRAME_BEGIN = 0x83
    const val EV_FRAME_END = 0x84
    const val EV_ERROR = 0x85

    const val ERR_CAMERA_NOT_READY = 1
    const val ERR_CAPTURE_FAILED = 2
    const val ERR_BUSY = 3
    const val ERR_UNKNOWN_COMMAND = 4

    const val MAX_FRAME_BYTES = 512 * 1024

    fun ping(): ByteArray = byteArrayOf(OP_PING.toByte())
    fun getStatus(): ByteArray = byteArrayOf(OP_GET_STATUS.toByte())
    fun capture(): ByteArray = byteArrayOf(OP_CAPTURE.toByte())
    fun setLed(on: Boolean): ByteArray = byteArrayOf(OP_SET_LED.toByte(), if (on) 1 else 0)

    fun errorMessage(code: Int): String = when (code) {
        ERR_CAMERA_NOT_READY -> "Kamera kacamata belum siap atau belum terpasang di firmware."
        ERR_CAPTURE_FAILED -> "Kacamata gagal mengambil gambar."
        ERR_BUSY -> "Kacamata masih sibuk. Coba lagi sebentar."
        ERR_UNKNOWN_COMMAND -> "Kacamata tidak mengenali perintah (versi firmware berbeda?)."
        else -> "Kacamata melaporkan error ($code)."
    }

    /** Mengembalikan null bila paket terpotong, kosong, atau tipenya tidak dikenal. */
    fun parseEvent(data: ByteArray): GlassesEvent? {
        if (data.isEmpty()) return null
        return when (data[0].toInt() and 0xFF) {
            EV_PONG -> GlassesEvent.Pong
            EV_STATUS -> {
                if (data.size < 4) return null
                val flags = data[2].toInt() and 0xFF
                GlassesEvent.Status(
                    GlassesStatus(
                        batteryPercent = (data[1].toInt() and 0xFF).coerceIn(0, 100),
                        cameraReady = flags and 0x01 != 0,
                        micReady = flags and 0x02 != 0,
                        charging = flags and 0x04 != 0,
                        ledOn = flags and 0x08 != 0,
                        protocolVersion = data[3].toInt() and 0xFF,
                    ),
                )
            }
            EV_FRAME_BEGIN -> {
                if (data.size < 6) return null
                val total = readU32(data, 2)
                if (total <= 0L || total > MAX_FRAME_BYTES) return null
                GlassesEvent.FrameBegin(data[1].toInt() and 0xFF, total.toInt())
            }
            EV_FRAME_END -> {
                if (data.size < 6) return null
                GlassesEvent.FrameEnd(data[1].toInt() and 0xFF, readU32(data, 2))
            }
            EV_ERROR -> {
                if (data.size < 2) return null
                GlassesEvent.Error(data[1].toInt() and 0xFF)
            }
            else -> null
        }
    }

    fun crc32(bytes: ByteArray): Long = CRC32().also { it.update(bytes, 0, bytes.size) }.value

    private fun readU32(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 4) value = value or ((data[offset + i].toLong() and 0xFF) shl (8 * i))
        return value
    }
}

/**
 * Menyusun ulang JPEG dari potongan BLE. Menolak urutan yang loncat, ukuran yang tidak cocok,
 * dan CRC yang salah supaya gambar rusak tidak pernah dianggap foto valid.
 */
class FrameAssembler(private val maxBytes: Int = GlassesProtocol.MAX_FRAME_BYTES) {
    sealed interface Result {
        data class Complete(val bytes: ByteArray) : Result
        data class Failed(val reason: String) : Result
    }

    private var buffer: ByteArray? = null
    private var written = 0
    private var expectedSeq = 0
    private var frameId = -1
    private var broken: String? = null

    fun reset() {
        buffer = null
        written = 0
        expectedSeq = 0
        frameId = -1
        broken = null
    }

    fun begin(id: Int, totalLength: Int) {
        reset()
        if (totalLength <= 0 || totalLength > maxBytes) {
            broken = "Ukuran frame tidak valid ($totalLength byte)."
            return
        }
        buffer = ByteArray(totalLength)
        frameId = id
    }

    /** [packet] = 2 byte nomor urut (LE) + data. */
    fun chunk(packet: ByteArray) {
        val buf = buffer
        if (buf == null) {
            if (broken == null) broken = "Data gambar datang sebelum FRAME_BEGIN."
            return
        }
        if (broken != null) return
        if (packet.size < 3) {
            broken = "Potongan gambar kosong."
            return
        }
        val seq = (packet[0].toInt() and 0xFF) or ((packet[1].toInt() and 0xFF) shl 8)
        if (seq != expectedSeq) {
            broken = "Potongan gambar hilang atau tidak berurutan (diharapkan $expectedSeq, dapat $seq)."
            return
        }
        val payload = packet.size - 2
        if (written + payload > buf.size) {
            broken = "Data gambar melebihi ukuran yang diumumkan."
            return
        }
        System.arraycopy(packet, 2, buf, written, payload)
        written += payload
        expectedSeq = (expectedSeq + 1) and 0xFFFF
    }

    fun end(id: Int, expectedCrc: Long): Result {
        val buf = buffer
        val problem = broken
        val currentId = frameId
        val count = written
        reset()
        if (problem != null) return Result.Failed(problem)
        if (buf == null) return Result.Failed("FRAME_END tanpa FRAME_BEGIN.")
        if (id != currentId) return Result.Failed("ID frame tidak cocok.")
        if (count != buf.size) return Result.Failed("Gambar belum lengkap ($count dari ${buf.size} byte).")
        if (GlassesProtocol.crc32(buf) != expectedCrc) return Result.Failed("Checksum gambar salah — data rusak saat transfer.")
        return Result.Complete(buf)
    }
}
