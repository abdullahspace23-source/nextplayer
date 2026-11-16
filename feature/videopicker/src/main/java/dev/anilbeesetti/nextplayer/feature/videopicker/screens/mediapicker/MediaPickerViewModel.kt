package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetRecentlyPlayedVideoUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferEvent
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.media.services.TransferProgress
import dev.anilbeesetti.nextplayer.core.media.services.TransferResult
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.findClosestFolder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MediaPickerViewModel(
    private val getSortedMediaUseCase: GetSortedMediaUseCase,
    private val getRecentlyPlayedVideoUseCase: GetRecentlyPlayedVideoUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val mediaOperationsService: MediaOperationsService,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaSynchronizer: MediaSynchronizer,
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val systemService: SystemService,
    private val context: Context,
    @InjectedParam private val input: Input,
    @InjectedParam internal var output: Output,
) : MviViewModel<MediaPickerUiState, MediaPickerAction>() {

    data class Input(val folderId: String?)

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val playVideos: (List<Uri>) -> Unit,
        val openFolder: (String) -> Unit,
        val openSettings: () -> Unit,
        val openSearch: () -> Unit,
        val openVault: () -> Unit,
    )

    val folderPath = input.folderId

    private val stateInternal = MutableStateFlow(
        value = MediaPickerUiState(
            folderName = folderPath?.let { File(folderPath).prettyName },
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    override val state: StateFlow<MediaPickerUiState> = stateInternal.asStateFlow()

    private var mediaCollectJob: Job? = null
    private var transferJob: Job? = null

    init {
        if (ContextCompat.checkSelfPermission(
                context,
                storagePermission,
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startMediaCollection()
        }
        collectPreferences()
        collectPlaylists()
    }

    override fun onAction(action: MediaPickerAction) {
        when (action) {
            is MediaPickerAction.OnNavigateUpClick -> output.navigateUp()

            is MediaPickerAction.OnPlayVideo -> output.playVideo(action.uri)

            is MediaPickerAction.OnFolderClick -> output.openFolder(action.folderPath)

            is MediaPickerAction.OnSettingsClick -> output.openSettings()

            is MediaPickerAction.OnSearchClick -> output.openSearch()

            is MediaPickerAction.OnVaultClick -> output.openVault()

            is MediaPickerAction.Refresh -> refresh()

            is MediaPickerAction.RenameVideo -> renameVideo(
                action.uri,
                action.to,
            )

            is MediaPickerAction.RenameFolder -> renameFolder(
                action.path,
                action.to,
            )

            is MediaPickerAction.UpdateMenu -> updateMenu(action.preferences)

            is MediaPickerAction.OnPermissionAccepted -> startMediaCollection()

            is MediaPickerAction.PlaySelectedItems -> playSelectedItems(action.selectionItems)

            is MediaPickerAction.DeleteSelectedItems -> deleteSelectedItems(
                action.selectionItems,
                action.permanently,
            )

            is MediaPickerAction.ShareSelectedItems -> shareSelectedItems(action.selectionItems)

            is MediaPickerAction.ShowMediaInfo -> showMediaInfo(action.video)

            is MediaPickerAction.DismissMediaInfo -> stateInternal.update {
                it.copy(mediaInfo = null)
            }
