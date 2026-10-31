// iOS: ProfilePhotoStoreTests.swift. Same limits, dates and stale-snapshot cases.
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.model.ProfilePhotoDTO
import com.onezee.triptrack.sync.PhotoEncoder
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilePhotoStoreTests {
    private val directory = Files.createTempDirectory("profile-photos").toFile()
    private val jpeg = byteArrayOf(40, 30, 70)
    private val encoder = object : PhotoEncoder {
        override suspend fun jpeg(data: ByteArray, maxDimension: Int, quality: Int) = data.takeIf { it.contentEquals(jpeg) }
    }
    private val service = object : ProfilePhotoService {
        override suspend fun list() = emptyList<ProfilePhotoDTO>()
        override suspend fun upload(id: UUID, type: String, bytes: ByteArray, takenAt: Instant?) = Unit
        override suspend fun delete(id: UUID) = Unit
        override suspend fun order(ids: List<UUID>) = Unit
    }
    private fun TestScope.store() = ProfilePhotoStore(directory, service, encoder, { null }, backgroundScope, StandardTestDispatcher(testScheduler))
    @After fun clean() { directory.deleteRecursively() }
    @Test fun addAndNinePhotoLimit() = runTest {
        val store = store(); repeat(9) { store.add(jpeg) }
        assertEquals(9, store.photos.value.size)
        assertFailsWith<IllegalArgumentException> { store.add(jpeg) }
        assertEquals(9, store.photos.value.size)
    }
    @Test fun deleteRemovesFileAndPersistsTombstone() = runTest {
        val store = store(); val id = store.add(jpeg)
        val file = directory.resolve(store.photos.value.single().filename!!)
        assertTrue(file.exists()); store.delete(id); assertFalse(file.exists())
        val restored = store(); restored.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/deleted.jpg")))
        assertTrue(restored.photos.value.isEmpty())
    }
    @Test fun reorderAndMetadataRoundTrip() = runTest {
        val store = store(); val date = Instant.ofEpochSecond(1_700_000_000)
        val first = store.add(jpeg, takenAt = date); val second = store.add(jpeg)
        store.makeFirst(second)
        assertEquals(listOf(second, first), store().photos.value.map { it.id })
        assertEquals(date, store().photos.value.last().takenAt)
        assertEquals(store.photos.value, store().photos.value)
    }
    @Test fun restoreSignedUrlsWithoutLocalFiles() = runTest {
        val store = store(); val id = UUID.randomUUID()
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/thumb.jpg?signature=1", "https://example.invalid/original.jpg?signature=1")))
        assertNull(store.photos.value.single().filename)
        store.restore(null); assertEquals(1, store.photos.value.size)
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/thumb.jpg?signature=2")))
        assertEquals("https://example.invalid/thumb.jpg?signature=2", store.photos.value.single().thumbnailURL)
    }
    @Test fun remoteVariantStateSurvivesMetadataRoundTrip() = runTest {
        val store = store(); val id = UUID.randomUUID()
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/thumb.jpg", takenAt = "2026-10-22T09:00:00Z", sortOrder = 0)))
        val restored = store().photos.value.single()
        assertTrue(restored.thumbnailUploaded); assertFalse(restored.originalUploaded)
        assertEquals(Instant.parse("2026-10-22T09:00:00Z"), restored.takenAt)
        assertEquals(store.photos.value, store().photos.value)
    }
    @Test fun onlyAFreshSnapshotRemovesPhotosDeletedElsewhere() = runTest {
        val store = store(); val id = UUID.randomUUID()
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/t.jpg", "https://example.invalid/o.jpg")))
        val requested = store.uploadRevision; store.add(jpeg)
        store.restore(emptyList(), requested); assertTrue(store.photos.value.any { it.id == id })
        store.restore(emptyList(), store.uploadRevision)
        assertFalse(store.photos.value.any { it.id == id }); assertEquals(1, store.photos.value.size)
    }
    @Test fun restoreConfirmsLateVariantsAndDropsRemoteOnlyPhotos() = runTest {
        val store = store(); val id = UUID.randomUUID()
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/t.jpg")), store.uploadRevision)
        assertFalse(store.photos.value.single().originalUploaded)
        store.restore(listOf(ProfilePhotoDTO(id, "https://example.invalid/t.jpg", "https://example.invalid/o.jpg")), store.uploadRevision)
        assertTrue(store.photos.value.single().originalUploaded)
        store.restore(listOf(ProfilePhotoDTO(UUID.randomUUID(), "https://example.invalid/p.jpg")), store.uploadRevision)
        store.restore(emptyList(), store.uploadRevision); assertTrue(store.photos.value.isEmpty())
    }
    @Test fun aSnapshotFromBeforeAReorderCannotUndoIt() = runTest {
        val store = store(); val first = store.add(jpeg); val second = store.add(jpeg)
        val requested = store.uploadRevision; store.makeFirst(second)
        store.restore(listOf(ProfilePhotoDTO(first), ProfilePhotoDTO(second)), requested)
        assertEquals(listOf(second, first), store.photos.value.map { it.id })
    }
    @Test fun importFromAPreviousSessionIsRefused() = runTest {
        val store = store(); val session = store.session; store.wipe()
        assertFailsWith<CancellationException> { store.add(jpeg, session) }
        assertTrue(store.photos.value.isEmpty())
    }
    @Test fun wipeRemovesFolderMetadataAndMemory() = runTest {
        val store = store(); store.add(jpeg); store.wipe()
        assertTrue(store.photos.value.isEmpty()); assertFalse(directory.exists()); assertTrue(store().photos.value.isEmpty())
    }
    @Test fun invalidImageDoesNotCreateMetadata() = runTest {
        val store = store(); assertFailsWith<IllegalArgumentException> { store.add(byteArrayOf(1, 2, 3)) }
        assertTrue(store.photos.value.isEmpty()); assertFalse(directory.resolve("metadata.json").exists())
    }
    @Test fun failedUploadDoesNotLeaveAWarningInTheNextAccount() = runTest {
        val account = UUID.randomUUID()
        val offline = object : ProfilePhotoService by service {
            override suspend fun list(): List<ProfilePhotoDTO> = throw java.io.IOException()
        }
        val store = ProfilePhotoStore(directory, offline, encoder, { account }, backgroundScope, StandardTestDispatcher(testScheduler))
        store.add(jpeg); store.sync(); assertTrue(store.failed.value)
        store.wipe(); assertFalse(store.failed.value); assertTrue(store.photos.value.isEmpty())
    }
}
