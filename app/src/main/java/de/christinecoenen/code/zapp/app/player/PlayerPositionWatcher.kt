package de.christinecoenen.code.zapp.app.player

import androidx.media3.common.Player
import org.joda.time.Duration
import java.util.*
import kotlin.concurrent.timer

class PlayerPositionWatcher(
	val player: Player,
	val onPositionChanged: () -> Unit
) : Player.Listener {

	companion object {
		private const val TimerName = "PlayPositionTimer"
		private val TimerStartDelay = Duration(1)
		private val TimerInterval = Duration(1)
	}

	private var timer: Timer? = null

	init {
		player.addListener(this)
	}

	fun dispose() {
		player.removeListener(this)
		timer?.cancel()
	}

	override fun onIsPlayingChanged(isPlaying: Boolean) {
		timer?.cancel()

		if (isPlaying) {
			timer = timer(TimerName, true, TimerStartDelay.millis, TimerInterval.millis) {
				onPositionChanged()
			}
		}
	}
}
