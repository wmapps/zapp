package de.christinecoenen.code.zapp.app.personal.details.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.paging.PagingDataAdapter
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekItemType
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekItemViewHolder
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.MediathekShowListItemListener
import de.christinecoenen.code.zapp.databinding.MediathekListFragmentItemBinding
import de.christinecoenen.code.zapp.models.shows.PersistedMediathekShow
import de.christinecoenen.code.zapp.utils.view.PersistedMediathekShowDiffUtilItemCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PagedPersistedShowListAdapter(
	private val scope: LifecycleCoroutineScope,
	private val listener: MediathekShowListItemListener
) : PagingDataAdapter<PersistedMediathekShow, MediathekItemViewHolder>(
	PersistedMediathekShowDiffUtilItemCallback()
) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediathekItemViewHolder {
		val layoutInflater = LayoutInflater.from(parent.context)
		val binding = MediathekListFragmentItemBinding.inflate(layoutInflater, parent, false)
		val holder = MediathekItemViewHolder(binding, MediathekItemType.Download, false)

		binding.root.setOnClickListener {
			getItem(holder.bindingAdapterPosition)?.let {
				listener.onShowClicked(it.mediathekShow)
			}
		}
		binding.root.setOnLongClickListener {
			getItem(holder.bindingAdapterPosition)?.let {
				listener.onShowLongClicked(it.mediathekShow, binding.root)
			}
			true
		}

		return holder
	}

	override fun onBindViewHolder(holder: MediathekItemViewHolder, position: Int) {
		getItem(position)?.let {
			scope.launch(Dispatchers.Main) {
				holder.setShow(it.mediathekShow)
			}
		}
	}

	override fun onViewRecycled(holder: MediathekItemViewHolder) {
		super.onViewRecycled(holder)
		holder.recycle()
	}
}
