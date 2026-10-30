package com.onezee.triptrack.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.feature.trip.TripMessage
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.logic.CommentContentRules
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: DiscussionArchive.swift
interface CommentArchive {
    suspend fun load(tripId: UUID): List<TripComment>
    suspend fun save(tripId: UUID, comments: List<TripComment>)
}

// iOS: TripCommentsStore.swift (composer validation via ContentFilter)
fun interface CommentValidator { fun validate(text: String): TripMessage? }

// iOS: ContentFilter.swift (validate .comment)
object TripCommentValidator : CommentValidator {
    override fun validate(text: String): TripMessage? = when (CommentContentRules.validate(text)) {
        CommentContentRules.Failure.tooLong -> TripMessage.commentTooLong
        CommentContentRules.Failure.inappropriateLanguage -> TripMessage.inappropriateLanguage
        null -> null
    }
}

// iOS: TripComment.swift
data class CommentState(val id: UUID, val author: AuthorState, val text: String, val createdAt: Instant,
    val isMine: Boolean, val parentId: UUID?, val replyToName: String?) {
    companion object { fun from(comment: TripComment) = CommentState(comment.id, AuthorState.from(comment.user), comment.text, comment.createdAt, comment.isMine, comment.parentId, comment.replyToName) }
}

// iOS: TripCommentsStore.swift
data class TripCommentsState(val comments: List<CommentState> = emptyList(), val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false, val isPosting: Boolean = false, val nextCursor: String? = null,
    val hasLoadedOnce: Boolean = false, val loadFailed: Boolean = false, val unavailable: Boolean = false,
    val isArchived: Boolean = false, val text: String = "", val replyTo: CommentState? = null,
    val message: TripMessage? = null)

// iOS: TripCommentsStore.swift. A detail owns its VM so stacked screens never share pages.
class TripCommentsViewModel(
    private val tripId: UUID,
    private val api: TripSocialApi,
    private val archive: CommentArchive,
    private val validator: CommentValidator = TripCommentValidator,
    private val me: () -> SocialAuthor?,
    private val worker: CoroutineDispatcher,
    private val countChanged: (UUID, Int) -> Unit,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val mutable = MutableStateFlow(TripCommentsState())
    val state = mutable.asStateFlow()
    private var generation = 0
    fun setText(text: String) { mutable.update { it.copy(text = text, message = null) } }
    fun replyTo(comment: CommentState?) { mutable.update { it.copy(replyTo = comment) } }
    fun load(isOnServer: Boolean = true): Job? {
        if (state.value.isLoading) return null
        val gen = ++generation
        mutable.update { it.copy(isLoading = true) }
        return viewModelScope.launch {
            try {
                if (!isOnServer) {
                    val archived = withContext(worker) { archive.load(tripId) }
                    mutable.update { it.copy(comments = archived.map(CommentState::from), nextCursor = null,
                        loadFailed = false, unavailable = false, isArchived = archived.isNotEmpty()) }
                } else {
                    val response = withContext(worker) { api.comments(TripCommentsRequest(tripId, 30, null)) }
                    mutable.update { it.copy(comments = response.comments.map(CommentState::from), nextCursor = response.nextCursor,
                        loadFailed = false, unavailable = false, isArchived = false) }
                    // A cache failure cannot turn a live conversation into a failed load.
                    try { withContext(worker) { archive.save(tripId, response.comments) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The live page is still authoritative. */ }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val archived = try { withContext(worker) { archive.load(tripId) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                mutable.update { it.copy(loadFailed = archived.isEmpty(), unavailable = archived.isEmpty() && error is ApiError.InvalidHttpStatus && error.status == 404,
                    comments = if (archived.isNotEmpty()) archived.map(CommentState::from) else it.comments,
                    isArchived = archived.isNotEmpty(), nextCursor = if (archived.isNotEmpty()) null else it.nextCursor) }
            } finally { if (generation == gen) mutable.update { it.copy(isLoading = false, hasLoadedOnce = true) } }
        }
    }
    fun loadMore(): Job? {
        val current = state.value
        val cursor = current.nextCursor ?: return null
        if (current.isLoadingMore || current.isLoading || current.isArchived) return null
        val gen = generation
        mutable.update { it.copy(isLoadingMore = true) }
        return viewModelScope.launch {
            try {
                val response = withContext(worker) { api.comments(TripCommentsRequest(tripId, 30, cursor)) }
                if (generation == gen) mutable.update { it.copy(comments = (it.comments + response.comments.map(CommentState::from)).distinctBy { it.id }, nextCursor = response.nextCursor) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = TripMessage.loadFailed) } }
            finally { mutable.update { it.copy(isLoadingMore = false) } }
        }
    }
    fun post(): Job? {
        val current = state.value
        if (current.isPosting || current.isArchived || current.unavailable) return null
        val text = current.text.trim()
        if (text.isEmpty()) return null
        validator.validate(text)?.let { error -> mutable.update { it.copy(message = error) }; return null }
        val author = me() ?: return null
        val root = current.replyTo?.let { it.parentId ?: it.id }
        val tempId = UUID.randomUUID()
        val temp = CommentState(tempId, AuthorState.from(author), text, clock(), true, root, current.replyTo?.author?.displayName)
        mutable.update { it.copy(isPosting = true, comments = listOf(temp) + it.comments, message = null) }
        return viewModelScope.launch {
            try {
                val response = withContext(worker) { api.createComment(SocialCommentCreateRequest(tripId, text, root)) }
                mutable.update { it.copy(comments = it.comments.map { comment -> if (comment.id == tempId) comment.copy(id = response.id, createdAt = response.createdAt) else comment },
                    text = if (it.text == current.text) "" else it.text, replyTo = null) }
                countChanged(tripId, 1)
            } catch (cancelled: CancellationException) { mutable.update { it.copy(comments = it.comments.filterNot { it.id == tempId }) }; throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(comments = it.comments.filterNot { it.id == tempId }, message = if (error is ApiError.TooManyRequests) TripMessage.commentRateLimited else TripMessage.commentPostFailed) } }
            finally { mutable.update { it.copy(isPosting = false) } }
        }
    }
    fun delete(id: UUID): Job? {
        if (state.value.isArchived) return null
        val index = state.value.comments.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = state.value.comments[index]
        mutable.update { it.copy(comments = it.comments.filterNot { it.id == id }) }
        return viewModelScope.launch {
            try { withContext(worker) { api.deleteComment(id) }; countChanged(tripId, -1) }
            catch (cancelled: CancellationException) { restore(removed, index); throw cancelled }
            catch (error: Exception) {
                if (error !is ApiError.UnknownServer || error.code != "COMMENT_NOT_FOUND") {
                    restore(removed, index)
                    mutable.update { it.copy(message = TripMessage.commentDeleteFailed) }
                }
            }
        }
    }
    private fun restore(comment: CommentState, index: Int) {
        mutable.update { it.copy(comments = it.comments.toMutableList().apply { if (none { it.id == comment.id }) add(minOf(index, size), comment) }.toList()) }
    }
}
