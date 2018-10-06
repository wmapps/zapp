package de.christinecoenen.code.zapp.app.mediathek.controller;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import de.christinecoenen.code.zapp.R;
import de.christinecoenen.code.zapp.utils.system.NotificationHelper;

// TODO: move to app wide controller package
public class BackgroundPlayerService extends IntentService implements
	PlayerNotificationManager.MediaDescriptionAdapter, PlayerNotificationManager.NotificationListener {

	private static final String ACTION_START_IN_BACKGROUND = "de.christinecoenen.code.zapp.app.mediathek.controller.action.START_IN_BACKGROUND";
	private static final String ACTION_NOTIFICATION_CLICKED = "de.christinecoenen.code.zapp.app.mediathek.controller.action.NOTIFICATION_CLICKED";

	private final Binder binder = new Binder();

	private TestPlayer player;
	private PlayerNotificationManager playerNotificationManager;
	private PowerManager.WakeLock wakeLock;
	private WifiManager.WifiLock wifiLock;
	private Intent foregroundActivityIntent;

	public BackgroundPlayerService() {
		super("BackgroundPlayerService");
	}

	private static void startInBackground(Context context) {
		Intent intent = new Intent(context, BackgroundPlayerService.class);
		intent.setAction(ACTION_START_IN_BACKGROUND);
		context.startService(intent);
	}

	private static Intent getNotificationClickedIntent(Context context) {
		Intent intent = new Intent(context, BackgroundPlayerService.class);
		intent.setAction(ACTION_NOTIFICATION_CLICKED);
		return intent;
	}

	/**
	 * This service is only running when
	 * 1. an UI element is currently bound and not in paused state OR
	 * 2. the playback is running in background
	 * <p>
	 * So is is save to aquire locks and release them in {@link #onDestroy()}
	 */
	@Override
	public void onCreate() {
		super.onCreate();

		player = new TestPlayer(this);

		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		if (powerManager != null) {
			wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zapp::BackgroundPlayerService");
		}

		WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Zapp::BackgroundPlayerService");
		}

		// TODO: is it save to hold locks here?
		wakeLock.acquire(TimeUnit.MINUTES.toMillis(120));
		wifiLock.acquire();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
		handleIntent(intent);
		return START_STICKY;
	}

	@Override
	protected void onHandleIntent(@Nullable Intent intent) {

	}

	@Override
	public void onDestroy() {
		movePlaybackToForeground();

		if (player != null) {
			player.destroy();
			player = null;
		}

		if (wakeLock.isHeld()) {
			wakeLock.release();
		}
		if (wifiLock.isHeld()) {
			wifiLock.release();
		}

		super.onDestroy();
	}

	private void handleIntent(Intent intent) {
		if (intent != null && intent.getAction() != null) {
			switch (intent.getAction()) {
				case ACTION_NOTIFICATION_CLICKED:
					handleNotificationClicked();
					break;
				case ACTION_START_IN_BACKGROUND:
					handleStartInBackground();
					break;
				default:
					throw new UnsupportedOperationException("Action not supported: " + intent.getAction());
			}
		}
	}

	/**
	 * @see Binder#movePlaybackToBackground(Intent foregroundActivityIntent)
	 */
	private void movePlaybackToBackground(Intent foregroundActivityIntent) {
		this.foregroundActivityIntent = foregroundActivityIntent;

		// start long running task
		BackgroundPlayerService.startInBackground(this);
	}

	/**
	 * @see Binder#movePlaybackToForeground()
	 */
	public void movePlaybackToForeground() {
		stopForeground(true);
		stopSelf();

		if (playerNotificationManager != null) {
			playerNotificationManager.setPlayer(null);
		}
	}

	/**
	 * As soon as somebody starts this service as background player, we create the
	 * player notification. When created this notification will move the service to
	 * foreground to avoid being destroyed by the system.
	 */
	private void handleStartInBackground() {
		playerNotificationManager = new PlayerNotificationManager(this,
			NotificationHelper.BACKGROUND_PLAYBACK_CHANNEL_ID,
			NotificationHelper.BACKGROUND_PLAYBACK_NOTIFICATION_ID,
			this);
		playerNotificationManager.setOngoing(false);
		playerNotificationManager.setPlayer(player.getExoPlayer());
		playerNotificationManager.setNotificationListener(this);
		playerNotificationManager.setSmallIcon(R.drawable.ic_zapp_tv);
		playerNotificationManager.setColor(getResources().getColor(R.color.colorPrimaryDark));
	}

	private void handleNotificationClicked() {
		foregroundActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(foregroundActivityIntent);
	}

	@Override
	public String getCurrentContentTitle(com.google.android.exoplayer2.Player player) {
		// TODO: get title of current player show
		return "Title";
	}

	@Override
	public PendingIntent createCurrentContentIntent(com.google.android.exoplayer2.Player player) {
		// a notification click will bring us back to this service
		Intent intent = BackgroundPlayerService.getNotificationClickedIntent(this);
		return PendingIntent.getService(BackgroundPlayerService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	@Override
	public String getCurrentContentText(com.google.android.exoplayer2.Player player) {
		// TODO: get topic of current player show
		return "Topic";
	}

	@Override
	public Bitmap getCurrentLargeIcon(com.google.android.exoplayer2.Player player, PlayerNotificationManager.BitmapCallback callback) {
		return null;
	}

	@Override
	public void onNotificationStarted(int notificationId, Notification notification) {
		startForeground(notificationId, notification);
	}

	@Override
	public void onNotificationCancelled(int notificationId) {
		movePlaybackToForeground();
	}

	public class Binder extends android.os.Binder {

		/**
		 * @return Player instance that will live as long as this service is up and running.
		 */
		public TestPlayer getPlayer() {
			return player;
		}

		/**
		 * Displays a player notification and starts keeping this service alive
		 * in background. Once called the service will resume running until {@link #movePlaybackToForeground()}
		 * is called or the notification is dismissed.
		 *
		 * @param foregroundActivityIntent intent to restart the calling activity once
		 */
		public void movePlaybackToBackground(Intent foregroundActivityIntent) {
			BackgroundPlayerService.this.movePlaybackToBackground(foregroundActivityIntent);
		}

		/**
		 * Call this once the playback is visible to the user. This will allow this service to
		 * be destroyed as soon as no ui component is bound any more.
		 * This will dismiss the playback notification.
		 */
		public void movePlaybackToForeground() {
			BackgroundPlayerService.this.movePlaybackToForeground();
		}
	}
}