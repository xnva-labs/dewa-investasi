package com.xnvalabs.smarteyex.data.glasses

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository

/**
 * Tautan BLE nyata ke kacamata SmartEyeX (ESP32). Semua status di sini berasal dari callback GATT
 * dan paket STATUS firmware; "CONNECTED" hanya muncul setelah service ditemukan dan kedua
 * notifikasi aktif. Tidak ada angka baterai/kamera yang dikarang saat belum terhubung.
 *
 * Izin BLUETOOTH_SCAN / BLUETOOTH_CONNECT diperiksa di setiap pintu masuk publik ([hasPermissions]),
 * karena itu lint MissingPermission di-suppress untuk seluruh objek ini.
 */
@SuppressLint("MissingPermission")
object GlassesRepository {
    enum class Link { IDLE, SCANNING, CONNECTING, CONNECTED }

    data class UiState(
        val link: Link = Link.IDLE,
        val deviceName: String? = null,
        val status: GlassesStatus? = null,
        val message: String? = null,
        val capturing: Boolean = false,
        val frameCount: Int = 0,
    )

    val state = mutableStateOf(UiState())

    /** Foto terakhir dari kacamata. Hanya di memori, tidak pernah ditulis ke disk. */
    @Volatile
    var lastFrame: ByteArray? = null
        private set

    private const val SCAN_TIMEOUT_MS = 15_000L
    private const val CAPTURE_TIMEOUT_MS = 12_000L
    private const val MAX_RECONNECT = 3
    private const val REQUESTED_MTU = 247

    private val main = Handler(Looper.getMainLooper())
    private val assembler = FrameAssembler()

    private var appContext: Context? = null
    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var userDisconnected = false
    private var reconnectAttempts = 0
    private var lastAddress: String? = null
    private var pendingFailure: String? = null

    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            update { it.copy(link = Link.IDLE, message = "Kacamata tidak ditemukan. Pastikan menyala, dekat, dan belum tersambung ke ponsel lain.") }
        }
    }
    private val captureTimeout = Runnable {
        if (state.value.capturing) {
            update { it.copy(capturing = false, message = "Kacamata tidak mengirim foto (timeout).") }
        }
    }
    private val reconnectRunnable = Runnable { reconnectNow() }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    fun startScanAndConnect() {
        val ctx = appContext ?: return setMessage("Modul kacamata belum siap.")
        if (state.value.link != Link.IDLE) return
        if (!hasPermissions(ctx)) return setMessage("Izin Bluetooth belum diberikan.")
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return setMessage("Perangkat ini tidak punya Bluetooth.")
        if (!adapter.isEnabled) return setMessage("Bluetooth mati. Nyalakan dulu.")
        val scanner = adapter.bluetoothLeScanner ?: return setMessage("Scanner Bluetooth LE tidak tersedia.")

        userDisconnected = false
        pendingFailure = null
        reconnectAttempts = 0
        update { it.copy(link = Link.SCANNING, deviceName = null, status = null, message = "Mencari kacamata SmartEyeX...") }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(GlassesProtocol.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        runCatching { scanner.startScan(filters, settings, scanCallback) }
            .onFailure {
                scanning = false
                AppDiagnostics.warn("BLE scan start failed", it)
                update { s -> s.copy(link = Link.IDLE, message = "Gagal memulai pencarian Bluetooth.") }
            }
        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    fun disconnect() {
        userDisconnected = true
        main.removeCallbacks(reconnectRunnable)
        main.removeCallbacks(captureTimeout)
        if (scanning) stopScan()
        val g = gatt
        if (g == null) {
            update { it.copy(link = Link.IDLE, status = null, capturing = false, message = "Terputus.") }
        } else {
            runCatching { g.disconnect() }.onFailure { handleDisconnected(g, -1) }
        }
    }

    fun ping() {
        if (!ensureConnected()) return
        if (!write(GlassesProtocol.ping())) setMessage("Gagal mengirim ping.")
    }

    fun refreshStatus() {
        if (!ensureConnected()) return
        if (!write(GlassesProtocol.getStatus())) setMessage("Gagal meminta status.")
    }

    /** Minta satu foto. Ditolak bila Camera OFF di Privacy Control atau kamera kacamata belum siap. */
    fun requestCapture() {
        if (!ensureConnected()) return
        if (!PrivacyRepository.settings.value.cameraEnabled) return setMessage("Camera OFF — aktifkan di Privacy Control.")
        if (state.value.status?.cameraReady == false) return setMessage("Kamera kacamata belum siap (firmware melaporkan tidak aktif).")
        if (state.value.capturing) return
        assembler.reset()
        update { it.copy(capturing = true, message = "Mengambil foto dari kacamata...") }
        if (!write(GlassesProtocol.capture())) {
            update { it.copy(capturing = false, message = "Gagal mengirim perintah foto.") }
            return
        }
        main.removeCallbacks(captureTimeout)
        main.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS)
    }

    /** Hapus foto terakhir dari memori. */
    fun clearFrame() {
        lastFrame = null
        update { it.copy(frameCount = it.frameCount + 1) }
    }

    // ---- internals -------------------------------------------------------------------------

    private fun update(block: (UiState) -> UiState) {
        state.value = block(state.value)
    }

    private fun setMessage(text: String) {
        update { it.copy(message = text) }
    }

    private fun ensureConnected(): Boolean {
        if (state.value.link == Link.CONNECTED && gatt != null && commandChar != null) return true
        setMessage("Kacamata belum terhubung.")
        return false
    }

    private fun stopScan() {
        scanning = false
        main.removeCallbacks(scanTimeout)
        val ctx = appContext ?: return
        val scanner = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            main.removeCallbacks(scanTimeout)
            update { it.copy(link = Link.IDLE, message = "Pencarian Bluetooth gagal (kode $errorCode).") }
        }
    }

    private fun connect(device: BluetoothDevice) {
        val ctx = appContext ?: return
        lastAddress = device.address
        update { it.copy(link = Link.CONNECTING, deviceName = device.name ?: "SmartEyeX", message = "Menyambung ke kacamata...") }
        gatt = runCatching { device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure {
                AppDiagnostics.warn("BLE connectGatt failed", it)
                update { s -> s.copy(link = Link.IDLE, message = "Gagal memulai koneksi.") }
            }
            .getOrNull()
    }

    private fun reconnectNow() {
        val ctx = appContext ?: return
        val address = lastAddress ?: return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null || adapter?.isEnabled != true) {
            update { it.copy(link = Link.IDLE, status = null, message = "Tidak bisa menyambung ulang. Bluetooth mati?") }
            return
        }
        connect(device)
    }

    private fun handleDisconnected(g: BluetoothGatt, status: Int) {
        runCatching { g.close() }
        if (gatt === g) gatt = null
        commandChar = null
        main.removeCallbacks(captureTimeout)
        val wasConnected = state.value.link == Link.CONNECTED
        if (!userDisconnected && wasConnected && reconnectAttempts < MAX_RECONNECT) {
            reconnectAttempts++
            update { it.copy(link = Link.CONNECTING, status = null, capturing = false, message = "Koneksi putus. Menyambung ulang ($reconnectAttempts/$MAX_RECONNECT)...") }
            main.postDelayed(reconnectRunnable, 2_000L * reconnectAttempts)
            return
        }
        val text = pendingFailure
            ?: if (userDisconnected) "Terputus." else "Koneksi ke kacamata gagal atau putus (kode $status)."
        pendingFailure = null
        update { it.copy(link = Link.IDLE, status = null, capturing = false, message = text) }
    }

    private fun fail(g: BluetoothGatt, text: String) {
        AppDiagnostics.warn("Glasses link failed: $text")
        userDisconnected = true
        pendingFailure = text
        update { it.copy(message = text) }
        runCatching { g.disconnect() }.onFailure { handleDisconnected(g, -1) }
    }

    @Suppress("DEPRECATION")
    private fun write(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = commandChar ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                c.value = bytes
                g.writeCharacteristic(c)
            }
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(characteristic, true)) return false
        val cccd = characteristic.getDescriptor(GlassesProtocol.CCCD_UUID) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }.getOrDefault(false)
    }

    private fun onNotification(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            GlassesProtocol.FRAME_UUID -> assembler.chunk(value)
            GlassesProtocol.EVENT_UUID -> handleEvent(GlassesProtocol.parseEvent(value) ?: return)
        }
    }

    private fun handleEvent(event: GlassesEvent) {
        when (event) {
            GlassesEvent.Pong -> setMessage("Kacamata merespons (ping OK).")
            is GlassesEvent.Status -> update { it.copy(status = event.status) }
            is GlassesEvent.FrameBegin -> assembler.begin(event.frameId, event.totalLength)
            is GlassesEvent.FrameEnd -> {
                main.removeCallbacks(captureTimeout)
                when (val result = assembler.end(event.frameId, event.crc32)) {
                    is FrameAssembler.Result.Complete -> {
                        lastFrame = result.bytes
                        update { it.copy(capturing = false, frameCount = it.frameCount + 1, message = "Foto diterima (${result.bytes.size / 1024} KB).") }
                    }
                    is FrameAssembler.Result.Failed -> update { it.copy(capturing = false, message = result.reason) }
                }
            }
            is GlassesEvent.Error -> {
                main.removeCallbacks(captureTimeout)
                update { it.copy(capturing = false, message = GlassesProtocol.errorMessage(event.code)) }
            }
        }
    }

    /**
     * GATT callbacks arrive on a Binder thread. Everything is re-posted to the main thread so that
     * the state machine, the frame assembler, and Compose state are only touched from one thread.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post { onConnectionState(g, status, newState) }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post { if (gatt === g) g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post { onServices(g, status) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { onDescriptorWritten(g, descriptor, status) }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: return
            val uuid = characteristic.uuid
            main.post { if (gatt === g) onNotification(uuid, value) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val uuid = characteristic.uuid
            val copy = value.copyOf()
            main.post { if (gatt === g) onNotification(uuid, copy) }
        }
    }

    private fun onConnectionState(g: BluetoothGatt, status: Int, newState: Int) {
        if (gatt != null && gatt !== g) {
            // Callback from a previous connection attempt that has already been replaced.
            runCatching { g.close() }
            return
        }
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            if (!runCatching { g.requestMtu(REQUESTED_MTU) }.getOrDefault(false)) g.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
            handleDisconnected(g, status)
        }
    }

    private fun onServices(g: BluetoothGatt, status: Int) {
        if (gatt !== g) return
        val service = g.getService(GlassesProtocol.SERVICE_UUID)
        val command = service?.getCharacteristic(GlassesProtocol.COMMAND_UUID)
        val event = service?.getCharacteristic(GlassesProtocol.EVENT_UUID)
        val frame = service?.getCharacteristic(GlassesProtocol.FRAME_UUID)
        if (status != BluetoothGatt.GATT_SUCCESS || command == null || event == null || frame == null) {
            fail(g, "Perangkat ini bukan kacamata SmartEyeX (service tidak lengkap).")
            return
        }
        commandChar = command
        if (!subscribe(g, event)) fail(g, "Gagal mengaktifkan notifikasi dari kacamata.")
    }

    private fun onDescriptorWritten(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (gatt !== g) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail(g, "Kacamata menolak langganan notifikasi (kode $status).")
            return
        }
        val service = g.getService(GlassesProtocol.SERVICE_UUID)
        val frame = service?.getCharacteristic(GlassesProtocol.FRAME_UUID)
        if (descriptor.characteristic.uuid == GlassesProtocol.EVENT_UUID && frame != null) {
            if (!subscribe(g, frame)) fail(g, "Gagal mengaktifkan jalur gambar dari kacamata.")
        } else {
            reconnectAttempts = 0
            update { it.copy(link = Link.CONNECTED, message = "Terhubung. Meminta status kacamata...") }
            write(GlassesProtocol.getStatus())
        }
    }
}
