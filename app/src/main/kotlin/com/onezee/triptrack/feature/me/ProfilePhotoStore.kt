// iOS: ProfilePhotoStore.swift. Public identity has its own durable queue, independent of Cloud Sync.
package com.onezee.triptrack.feature.me

import android.util.AtomicFile
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.EmptyResponse
import com.onezee.triptrack.model.ProfilePhotoDTO
import com.onezee.triptrack.network.*
import com.onezee.triptrack.sync.PhotoEncoder
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

// iOS: ProfilePhotoStore.Photo. Upload object keys are never used as display URLs.
data class ProfileCoverPhoto(val id: UUID, val filename: String? = null, val takenAt: Instant? = null,
    val thumbnailUploaded: Boolean = false, val originalUploaded: Boolean = false,
    val thumbnailURL: String? = null, val originalURL: String? = null)

// iOS: ProfilePhotoStore network seam (MockURLProtocol in ProfilePhotoStoreTests).
interface ProfilePhotoService {
    suspend fun list(): List<ProfilePhotoDTO>
    suspend fun upload(id: UUID, type: String, bytes: ByteArray, takenAt: Instant?)
    suspend fun delete(id: UUID)
    suspend fun order(ids: List<UUID>)
}

// iOS: ProfilePhotoStore.swift / R2PhotoStorage.uploadProfilePhotoPart.
class ApiProfilePhotoService(private val apiClient: ApiClient) : ProfilePhotoService {
    override suspend fun list() = apiClient.post<EmptyRequest, ProfilePhotoListResponse>(ApiEndpoint.profilePhotoList, EmptyRequest()).photos
    override suspend fun upload(id: UUID, type: String, bytes: ByteArray, takenAt: Instant?) {
        apiClient.uploadMultipart<PhotoUploadResponse>(ApiEndpoint.profilePhotoUpload, ProfilePhotoUploadRequest(id, type, takenAt).fields(), MultipartFile("file", "photo.jpg", "image/jpeg", bytes))
    }
    override suspend fun delete(id: UUID) { apiClient.post<ProfilePhotoDeleteRequest, EmptyResponse>(ApiEndpoint.profilePhotoDelete, ProfilePhotoDeleteRequest(id.toString())) }
    override suspend fun order(ids: List<UUID>) { apiClient.post<ProfilePhotoOrderRequest, EmptyResponse>(ApiEndpoint.profilePhotoOrder, ProfilePhotoOrderRequest(ids.map(UUID::toString))) }
}

// iOS: ProfilePhotoStore. All metadata mutations run on the supplied UI dispatcher.
class ProfilePhotoStore(
    private val directory: File,
    private val service: ProfilePhotoService,
    private val encoder: PhotoEncoder,
    private val account: () -> UUID?,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val mutable = MutableStateFlow<List<ProfileCoverPhoto>>(emptyList())
    val photos = mutable.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private var deletions = emptyList<UUID>()
    private var removed = emptySet<UUID>()
    private var orderDirty = false
    var uploadRevision = 0L
        private set
    private var mutationRevision = 0L
    private var generation = UUID.randomUUID()
    val session get() = generation
    private var syncing = false
    val hasPendingWork get() = deletions.isNotEmpty() || orderDirty || photos.value.any { it.filename != null && (!it.thumbnailUploaded || !it.originalUploaded) }

    init {
        val file = File(directory, "metadata.json")
        if (file.exists()) runCatching {
            val metadata = JSONObject(AtomicFile(file).openRead().use { it.readBytes().decodeToString() })
            val array = metadata.optJSONArray("photos") ?: JSONArray()
            mutable.value = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ProfileCoverPhoto(UUID.fromString(item.getString("id")), item.optString("filename").takeIf(String::isNotEmpty),
                    item.optString("takenAt").takeIf(String::isNotEmpty)?.let(Instant::parse), item.optBoolean("thumbnailUploaded"), item.optBoolean("originalUploaded"),
                    item.optString("thumbnailURL").takeIf(String::isNotEmpty), item.optString("originalURL").takeIf(String::isNotEmpty))
            }.take(LIMIT)
            fun ids(key: String): List<UUID> = metadata.optJSONArray(key)?.let { values -> (0 until values.length()).map { UUID.fromString(values.getString(it)) } }.orEmpty()
            deletions = ids("deletions"); removed = ids("removed").toSet(); orderDirty = metadata.optBoolean("orderDirty")
        }.onFailure { mutableFailed.value = true }
    }

    suspend fun add(data: ByteArray, importSession: UUID = session, takenAt: Instant? = null) = withContext(dispatcher) {
        if (importSession != generation) throw CancellationException()
        require(photos.value.size < LIMIT)
        val bytes = requireNotNull(encoder.jpeg(data, 1440, 70))
        ensureActive()
        if (importSession != generation) throw CancellationException()
        require(photos.value.size < LIMIT)
        val id = UUID.randomUUID()
        val filename = "profile-$id.jpg"
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val atomic = AtomicFile(File(directory, filename))
            val output = atomic.startWrite()
            try { output.write(bytes); atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
        }
        if (importSession != generation) { File(directory, filename).delete(); directory.delete(); throw CancellationException() }
        mutable.value = photos.value + ProfileCoverPhoto(id, filename, takenAt)
        orderDirty = true; mutationRevision++; uploadRevision++
        save(); requestSync()
        id
    }

    suspend fun delete(id: UUID) = withContext(dispatcher) {
        val photo = photos.value.firstOrNull { it.id == id } ?: return@withContext
        photo.filename?.let { File(directory, it).delete() }
        mutable.value = photos.value.filterNot { it.id == id }
        deletions = (deletions + id).distinct(); removed = removed + id; orderDirty = true; mutationRevision++
        save(); requestSync()
    }

    suspend fun makeFirst(id: UUID) = withContext(dispatcher) {
        val photo = photos.value.firstOrNull { it.id == id } ?: return@withContext
        mutable.value = listOf(photo) + photos.value.filterNot { it.id == id }
        orderDirty = true; mutationRevision++; uploadRevision++
        save(); requestSync()
    }

    suspend fun restore(remote: List<ProfilePhotoDTO>?, requestedRevision: Long? = null) = withContext(dispatcher) {
        remote ?: return@withContext
        val fresh = requestedRevision == uploadRevision
        var current = photos.value
        if (fresh) {
            val ids = remote.mapTo(mutableSetOf()) { it.id }
            val gone = current.filter { it.id !in ids && (it.filename == null || it.thumbnailUploaded && it.originalUploaded) }
            gone.forEach { photo -> photo.filename?.let { File(directory, it).delete() } }
            current = current.filterNot { it in gone }
        }
        remote.filterNot { it.id in removed }.forEach { item ->
            val existing = current.firstOrNull { it.id == item.id }
            if (existing != null) current = current.map { photo -> if (photo.id == item.id) photo.copy(
                thumbnailURL = item.thumbnailUrl, originalURL = item.originalUrl, thumbnailUploaded = photo.thumbnailUploaded || item.thumbnailUrl != null,
                originalUploaded = photo.originalUploaded || item.originalUrl != null) else photo }
            else if (current.size < LIMIT) current = current + ProfileCoverPhoto(item.id, takenAt = item.takenAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                thumbnailUploaded = item.thumbnailUrl != null, originalUploaded = item.originalUrl != null, thumbnailURL = item.thumbnailUrl, originalURL = item.originalUrl)
        }
        if (fresh && !orderDirty) {
            val positions = remote.mapIndexed { index, item -> item.id to index }.toMap()
            current = current.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
        }
        mutable.value = current
        save()
    }

    fun requestSync() { scope.launch { sync() } }
    suspend fun sync() = withContext(dispatcher) {
        val signedIn = account() ?: return@withContext
        if (syncing || !hasPendingWork) return@withContext
        syncing = true
        val epoch = generation
        val revision = mutationRevision
        fun current() = generation == epoch && account() == signedIn
        try {
            for (id in deletions.toList()) {
                service.delete(id); if (!current()) return@withContext
                deletions = deletions - id; save()
            }
            val requested = uploadRevision
            val remote = service.list(); if (!current()) return@withContext
            restore(remote, requested)
            for (photo in photos.value.toList()) {
                val filename = photo.filename ?: continue
                if (photo.thumbnailUploaded && photo.originalUploaded) continue
                val data = withContext(Dispatchers.IO) { File(directory, filename).readBytes() }
                for (type in listOf("thumbnail", "original")) {
                    if (!current() || photos.value.none { it.id == photo.id }) return@withContext
                    val present = photos.value.first { it.id == photo.id }
                    if (if (type == "thumbnail") present.thumbnailUploaded else present.originalUploaded) continue
                    val bytes = encoder.jpeg(data, if (type == "thumbnail") 200 else 1440, if (type == "thumbnail") 50 else 70) ?: continue
                    service.upload(photo.id, type, bytes, photo.takenAt); if (!current()) return@withContext
                    mutable.value = photos.value.map { item -> if (item.id == photo.id) item.copy(
                        thumbnailUploaded = item.thumbnailUploaded || type == "thumbnail", originalUploaded = item.originalUploaded || type == "original") else item }
                    uploadRevision++; save()
                }
            }
            if (orderDirty && photos.value.all { it.filename == null || it.thumbnailUploaded && it.originalUploaded }) {
                val ids = photos.value.map { it.id }
                service.order(ids); if (!current()) return@withContext
                if (ids == photos.value.map { it.id }) { orderDirty = false; uploadRevision++; save() }
            }
            mutableFailed.value = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (current()) mutableFailed.value = true }
        finally {
            if (generation == epoch) { syncing = false; if (mutationRevision != revision) requestSync() }
        }
    }

    suspend fun wipe() = withContext(dispatcher) {
        mutableFailed.value = false
        generation = UUID.randomUUID(); mutationRevision++; uploadRevision++; syncing = false
        mutable.value = emptyList(); deletions = emptyList(); removed = emptySet(); orderDirty = false
        directory.deleteRecursively()
    }

    private fun save() {
        val metadata = JSONObject().put("orderDirty", orderDirty)
            .put("deletions", JSONArray(deletions.map(UUID::toString))).put("removed", JSONArray(removed.map(UUID::toString)))
            .put("photos", JSONArray(photos.value.mapIndexed { index, photo -> JSONObject().put("id", photo.id.toString()).put("order", index)
                .put("filename", photo.filename).put("takenAt", photo.takenAt?.toString()).put("thumbnailUploaded", photo.thumbnailUploaded).put("originalUploaded", photo.originalUploaded)
                .put("thumbnailURL", photo.thumbnailURL).put("originalURL", photo.originalURL) }))
        // Nine small entries are committed without suspension: an old write cannot follow account wipe.
        directory.mkdirs()
        val file = AtomicFile(File(directory, "metadata.json"))
        val output = file.startWrite()
        try { output.write(metadata.toString().toByteArray()); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
    }
    companion object { const val LIMIT = 9 }
}
