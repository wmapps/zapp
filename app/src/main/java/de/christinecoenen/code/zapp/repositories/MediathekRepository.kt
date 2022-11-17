package de.christinecoenen.code.zapp.repositories

import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.christinecoenen.code.zapp.models.shows.DownloadStatus
import de.christinecoenen.code.zapp.models.shows.MediathekShow
import de.christinecoenen.code.zapp.models.shows.PersistedMediathekShow
import de.christinecoenen.code.zapp.persistence.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.joda.time.DateTime

class MediathekRepository(private val database: Database) {

	fun getDownloads(limit: Int): Flow<List<PersistedMediathekShow>> {
		return database
			.mediathekShowDao()
			.getDownloads(limit)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getDownloads(searchQuery: String): PagingSource<Int, PersistedMediathekShow> {
		return database
			.mediathekShowDao()
			.getAllDownloads("%$searchQuery%")
	}

	fun getStarted(limit: Int): Flow<List<PersistedMediathekShow>> {
		return database
			.mediathekShowDao()
			.getStarted(limit)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getStarted(searchQuery: String): PagingSource<Int, PersistedMediathekShow> {
		return database
			.mediathekShowDao()
			.getAllStarted("%$searchQuery%")
	}

	fun getBookmarked(limit: Int): Flow<List<PersistedMediathekShow>> {
		// TODO: implement
		return flow {
			emit(listOf())
		}
	}

	fun getBookmarked(searchQuery: String): PagingSource<Int, PersistedMediathekShow> {
		// TODO: implement
		return object : PagingSource<Int, PersistedMediathekShow>() {
			override fun getRefreshKey(state: PagingState<Int, PersistedMediathekShow>): Int? {
				return null
			}

			override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PersistedMediathekShow> {
				return LoadResult.Page(listOf(), null, null)
			}
		}
	}

	suspend fun persistOrUpdateShow(show: MediathekShow): Flow<PersistedMediathekShow> =
		withContext(Dispatchers.IO) {
			database
				.mediathekShowDao()
				.insertOrUpdate(show)

			database
				.mediathekShowDao()
				.getFromApiId(show.apiId)
				.distinctUntilChanged()
				.flowOn(Dispatchers.IO)
		}

	suspend fun updateShow(show: PersistedMediathekShow?) = withContext(Dispatchers.IO) {
		database
			.mediathekShowDao()
			.update(show!!)
	}

	suspend fun updateDownloadStatus(downloadId: Int, downloadStatus: DownloadStatus?) =
		withContext(Dispatchers.IO) {
			database
				.mediathekShowDao()
				.updateDownloadStatus(downloadId, downloadStatus!!)
		}

	suspend fun updateDownloadProgress(downloadId: Int, progress: Int) =
		withContext(Dispatchers.IO) {
			database
				.mediathekShowDao()
				.updateDownloadProgress(downloadId, progress)
		}

	suspend fun updateDownloadedVideoPath(downloadId: Int, videoPath: String?) =
		withContext(Dispatchers.IO) {
			database
				.mediathekShowDao()
				.updateDownloadedVideoPath(downloadId, videoPath)
		}

	fun getPersistedShow(id: Int): Flow<PersistedMediathekShow> {
		return database
			.mediathekShowDao()
			.getFromId(id)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getCompletedDownloads(): Flow<List<PersistedMediathekShow>> {
		return database
			.mediathekShowDao()
			.getCompletedDownloads()
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getPersistedShowByApiId(apiId: String): Flow<PersistedMediathekShow> {
		return database
			.mediathekShowDao()
			.getFromApiId(apiId)
			.distinctUntilChanged()
			.filterNotNull()
			.flowOn(Dispatchers.IO)
	}

	fun getPersistedShowByDownloadId(downloadId: Int): Flow<PersistedMediathekShow> {
		return database
			.mediathekShowDao()
			.getFromDownloadId(downloadId)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getDownloadStatus(id: Int): Flow<DownloadStatus> {
		return database
			.mediathekShowDao()
			.getDownloadStatus(id)
			.distinctUntilChanged()
			.onStart { emit(DownloadStatus.NONE) }
			.flowOn(Dispatchers.IO)
	}

	fun getDownloadStatus(apiId: String): Flow<DownloadStatus> {
		return database
			.mediathekShowDao()
			.getDownloadStatus(apiId)
			.filterNotNull()
			.distinctUntilChanged()
			.onStart { emit(DownloadStatus.NONE) }
			.flowOn(Dispatchers.IO)
	}

	fun getDownloadProgress(id: Int): Flow<Int> {
		return database
			.mediathekShowDao()
			.getDownloadProgress(id)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getDownloadProgress(apiId: String): Flow<Int> {
		return database
			.mediathekShowDao()
			.getDownloadProgress(apiId)
			.filterNotNull()
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}

	fun getIsRelevantForUser(apiId: String): Flow<Boolean> {
		return database
			.mediathekShowDao()
			.getIsRelevantForUser(apiId)
			.distinctUntilChanged()
			.filterNotNull()
			.onStart { emit(false) }
			.flowOn(Dispatchers.IO)
	}

	suspend fun getPlaybackPosition(showId: Int): Long = withContext(Dispatchers.IO) {
		database
			.mediathekShowDao()
			.getPlaybackPosition(showId)
	}

	suspend fun setPlaybackPosition(showId: Int, positionMillis: Long, durationMillis: Long) =
		withContext(Dispatchers.IO) {
			database
				.mediathekShowDao()
				.setPlaybackPosition(showId, positionMillis, durationMillis, DateTime.now())
		}

	fun getPlaybackPositionPercent(apiId: String): Flow<Float> {
		return database
			.mediathekShowDao()
			.getPlaybackPositionPercent(apiId)
			.distinctUntilChanged()
			.filterNotNull()
			.onStart { emit(0f) }
			.flowOn(Dispatchers.IO)
	}

	fun getCompletetlyDownloadedVideoPath(apiId: String): Flow<String?> {
		return database
			.mediathekShowDao()
			.getCompletetlyDownloadedVideoPath(apiId)
			.distinctUntilChanged()
			.flowOn(Dispatchers.IO)
	}
}
