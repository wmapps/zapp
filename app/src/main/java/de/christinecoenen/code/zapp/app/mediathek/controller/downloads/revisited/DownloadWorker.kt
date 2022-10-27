package de.christinecoenen.code.zapp.app.mediathek.controller.downloads.revisited

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import de.christinecoenen.code.zapp.app.mediathek.controller.downloads.DownloadFileInfoManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration.Companion.milliseconds

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
	CoroutineWorker(appContext, workerParams), KoinComponent {

	companion object {
		private const val ProgressKey = "Progress"
		private const val SourceUrlKey = "SourceUrl"
		private const val TargetFileUriKey = "TargetFileUri"
		private const val TitleKey = "Title"
		private const val BufferSize = DEFAULT_BUFFER_SIZE
		private val NotificationDelay = 100.milliseconds

		fun constructInputData(sourceUrl: String, targetFileUri: String, title: String) =
			workDataOf(
				SourceUrlKey to sourceUrl,
				TargetFileUriKey to targetFileUri,
				TitleKey to title
			)

		/**
		 * @return download progress between 0 and 100
		 */
		fun getProgress(workInfo: WorkInfo) = workInfo.progress.getInt(ProgressKey, 0)
	}

	private val httpClient: OkHttpClient by inject()
	private val downloadFileInfoManager: DownloadFileInfoManager by inject()

	private val notificationManager = NotificationManagerCompat.from(applicationContext)

	private val sourceUrl by lazy { inputData.getString(SourceUrlKey) }
	private val targetFileUri by lazy { inputData.getString(TargetFileUriKey) }
	private val title by lazy { inputData.getString(TitleKey) ?: "" }
	private val notificationId by lazy { id.hashCode() }

	private var progress = 0

	private val downloadProgressNotification = DownloadProgressNotification(
		appContext, title, getCancelIntent()
	)

	override suspend fun doWork(): Result {
		reportProgress()

		if (sourceUrl == null || targetFileUri == null) {
			return failure()
		}

		val request = Request.Builder().url(sourceUrl!!).build()
		val response = httpClient.newCall(request).execute()

		if (!response.isSuccessful || response.body() == null) {
			Timber.w("server response not successful")
			return failure()
		}

		val body = response.body()!!
		try {
			downloadFileInfoManager.openOutputStream(targetFileUri!!).use { outputSream ->
				if (outputSream == null) {
					Timber.w("fileoutputstream not readable")
					return failure()
				}

				body.byteStream().use { inputStream ->
					download(inputStream, outputSream, body.contentLength())
				}
			}
		} catch (e: Exception) {
			Timber.w(e)
			return failure()
		}

		progress = 100
		reportProgress()

		return success()
	}

	private fun success(): Result {
		MainScope().launch {
			delay(NotificationDelay)

			val notification = DownloadCompletedEventNotification(applicationContext, title)
			notificationManager.notify(notificationId, notification.build())
		}

		return Result.success()
	}

	private fun failure(): Result {
		MainScope().launch {
			delay(NotificationDelay)

			val notification = DownloadFailedEventNotification(applicationContext, title)
			notificationManager.notify(notificationId, notification.build())
		}

		return Result.failure()
	}

	private suspend fun download(
		inputStream: InputStream,
		outputStream: OutputStream,
		contentLength: Long
	) = withContext(Dispatchers.IO) {
		var bytesCopied = 0L
		var readCount = 0L
		val buffer = ByteArray(BufferSize)
		var bytes = inputStream.read(buffer)

		while (bytes >= 0 && !isStopped) {
			outputStream.write(buffer, 0, bytes)
			bytesCopied += bytes

			bytes = inputStream.read(buffer)
			readCount++

			// TODO: use a better, time based debounce
			if (readCount % 500 == 0L) {
				progress = ((bytesCopied * 100) / contentLength).toInt()
				reportProgress()
			}
		}
	}

	override suspend fun getForegroundInfo() =
		ForegroundInfo(id.hashCode(), downloadProgressNotification.build(progress))

	private suspend fun reportProgress() {
		val update = workDataOf(ProgressKey to progress)
		setProgress(update)

		setForeground(getForegroundInfo())
	}

	private fun getCancelIntent(): PendingIntent = WorkManager.getInstance(applicationContext)
		.createCancelPendingIntent(id)
}
