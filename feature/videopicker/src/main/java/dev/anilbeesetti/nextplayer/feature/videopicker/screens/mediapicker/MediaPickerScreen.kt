package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.media.services.TransferProgress
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.BindTopLevelBottomBarVisible
import dev.anilbeesetti.nextplayer.core.ui.components.BindTopLevelFab
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.LocalNavigationBottomPadding
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextOutlinedTextField
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.TextIconToggleButton
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.rememberRestorableFocusState
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.composables.PermissionMissingView
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.core.ui.preview.DayNightPreview
import dev.anilbeesetti.nextplayer.core.ui.preview.VideoPickerPreviewParameterProvider
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.CenterCircularProgressBar
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaInfoDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.NoVideosFound
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.QuickSettingsDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.RenameDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.SelectionAction
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinDotsIndicator
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.PinKeypad
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.VaultBiometricSetupDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault.VaultProgressDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VAULT_PIN_LENGTH
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager
import dev.anilbeesetti.nextplayer.feature.videopicker.state.toSelectedFolder
import dev.anilbeesetti.nextplayer.feature.videopicker.state.toSelectedVideo
import kotlin.math.roundToInt

@Composable
fun MediaPickerScreen(viewModel: MediaPickerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED,
    )

    MediaPickerScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun MediaPickerScreenContent(
    state: MediaPickerUiState,
    onAction: (MediaPickerAction) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val focusState = rememberRestorableFocusState()
    val hasMedia = (state.mediaDataState as? DataState.Success)?.value
        ?.let { it.folders.isNotEmpty() || it.videos.isNotEmpty() } == true
    val navigationBottomPadding = LocalNavigationBottomPadding.current

    // Re-enter the content region so its previously focused item is restored.
    val topBarDownModifier = if (isTv && hasMedia) {
        Modifier.focusProperties { down = focusState.requester }
    } else {
        Modifier
    }
    val permissionState = rememberPermissionState(permission = storagePermission)
    var wasPermissionGranted by remember { mutableStateOf(permissionState.status.isGranted) }

    LaunchedEffect(permissionState.status.isGranted) {
        val isPermissionGranted = permissionState.status.isGranted
        if (isPermissionGranted && !wasPermissionGranted) {
            onAction(MediaPickerAction.OnPermissionAccepted)
        }
        wasPermissionGranted = isPermissionGranted
    }
    val lazyGridState = rememberLazyGridState()
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { onAction(MediaPickerAction.OnPlayVideo(uri = it)) } },
    )

    var showQuickSettingsDialog by rememberSaveable { mutableStateOf(value = false) }
    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(value = null) }
    var showRenameFolderPath: String? by rememberSaveable { mutableStateOf(value = null) }
    var showRenameFolderName: String? by rememberSaveable { mutableStateOf(value = null) }
    var showDeleteVideosConfirmation by rememberSaveable { mutableStateOf(value = false) }

    val mediaHolder = (state.mediaDataState as? DataState.Success)?.value
    val onFabClick = {
        val selectedItem = state.recentlyPlayedVideo?.toSelectedVideo()
            ?: mediaHolder?.folders?.firstOrNull()?.toSelectedFolder()
            ?: mediaHolder?.videos?.firstOrNull()?.toSelectedVideo()

        when (selectedItem) {
            is SelectionItem.Video -> onAction(
                MediaPickerAction.OnPlayVideo(selectedItem.uriString.toUri()),
            )

            is SelectionItem.Folder -> onAction(
                MediaPickerAction.PlaySelectedItems(selectionItems = setOf(element = selectedItem)),
            )

            null -> selectVideoFileLauncher.launch(input = "video/*")
        }
    }

    BindTopLevelBottomBarVisible(
        visible =
            state.folderName != null || !selectionManager.isInSelectionMode,
    )

    if (state.folderName == null) {
        BindTopLevelFab(
            key = TopLevelFabKey.MEDIA,
            icon = NextIcons.Play,
            onClick = onFabClick,
        )
    }

    val selectedItemsSize = selectionManager.selectionItems.size
    val totalItemsSize =
        (state.mediaDataState as? DataState.Success)?.value?.run { folders.size + videos.size } ?: 0

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    val titleText = (state.folderName ?: stringResource(R.string.app_name))
                        .takeIf { !selectionManager.isInSelectionMode } ?: ""
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold.takeIf { state.folderName == null },
                    )
                },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(shape = CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { selectionManager.exitSelectionMode() }
                                .padding(8.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                            Text(
                                text = stringResource(
                                    R.string.m_n_selected,
                                    selectedItemsSize,
                                    totalItemsSize,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (state.folderName != null) {
                        FilledTonalIconButton(
                            onClick = { onAction(MediaPickerAction.OnNavigateUpClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv = isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    val folder = (state.mediaDataState as? DataState.Success)?.value
                                    if (folder != null) {
                                        folder.folders.forEach {
                                            selectionManager.selectFolder(folder = it)
                                        }
                                        folder.videos.forEach {
                                            selectionManager.selectVideo(video = it)
                                        }
                                    }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                            modifier = topBarDownModifier.tvFocusRing(isTv = isTv),
                        ) {
                            Icon(
                                imageVector = if (selectedItemsSize != totalItemsSize) {
                                    NextIcons.SelectAll
                                } else {
                                    NextIcons.DeselectAll
                                },
                                contentDescription = if (selectedItemsSize != totalItemsSize) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onAction(MediaPickerAction.OnSearchClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv = isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.Search,
                                contentDescription = stringResource(id = R.string.search),
                            )
                        }
                        IconButton(
                            onClick = { showQuickSettingsDialog = true },
                            modifier = topBarDownModifier.tvFocusRing(isTv = isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.DashBoard,
                                contentDescription = stringResource(id = R.string.menu),
                            )
                        }
                        IconButton(
                            onClick = { onAction(MediaPickerAction.OnSettingsClick) },
                            modifier = topBarDownModifier.tvFocusRing(isTv = isTv),
                        ) {
                            Icon(
                                imageVector = NextIcons.Settings,
                                contentDescription = stringResource(id = R.string.settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val selectedFolder =
                selectionManager.selectionItems.singleOrNull() as? SelectionItem.Folder
            SelectionActionsSheet(
                show =
                    selectionManager.isInSelectionMode &&
                        selectionManager.selectionItems.isNotEmpty(),
                contentFocusRequester = focusState.requester,
                showRenameAction =
                    selectionManager.isSingleVideoSelected || selectedFolder != null,
                showInfoAction = selectionManager.isSingleVideoSelected,
                showHideAction = selectionManager.selectionItems.isNotEmpty(),
                onPlayAction = {
                    onAction(MediaPickerAction.PlaySelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onAddToPlaylistAction = {
                    onAction(MediaPickerAction.ShowAddToPlaylist(selectionManager.selectionItems))
                },
                onRenameAction = {
                    val selectedItem =
                        selectionManager.selectionItems.firstOrNull()
                            ?: return@SelectionActionsSheet
                    when (selectedItem) {
                        is SelectionItem.Video -> {
                            val video = (state.mediaDataState as? DataState.Success)?.value?.videos
                                ?.find { it.uriString == selectedItem.id }
                                ?: return@SelectionActionsSheet
                            showRenameActionFor = video
                        }

                        is SelectionItem.Folder -> {
                            showRenameFolderPath = selectedItem.path
                            showRenameFolderName = selectedItem.name
                        }
                    }
                },
                onInfoAction = {
                    val selectedVideo =
                        selectionManager.selectionItems.firstOrNull()
                            ?: return@SelectionActionsSheet
                    val video = (state.mediaDataState as? DataState.Success)?.value?.videos
                        ?.find { it.uriString == selectedVideo.id } ?: return@SelectionActionsSheet
                    onAction(MediaPickerAction.ShowMediaInfo(video = video))
                    selectionManager.exitSelectionMode()
                },
                onShareAction = {
                    onAction(MediaPickerAction.ShareSelectedItems(selectionManager.selectionItems))
                },
                onCopyAction = {
                    onAction(MediaPickerAction.CopySelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onMoveAction = {
                    onAction(MediaPickerAction.MoveSelectedItems(selectionManager.selectionItems))
                    selectionManager.exitSelectionMode()
                },
                onHideAction = {
                    onAction(
                        MediaPickerAction.RequestHideSelectedItems(selectionManager.selectionItems),
                    )
                    selectionManager.exitSelectionMode()
                },
                onDeleteAction = { showDeleteVideosConfirmation = true },
            )
        },
        floatingActionButton = {
            if (state.folderName != null && !selectionManager.isInSelectionMode) {
                FloatingActionButton(
                    onClick = onFabClick,
                    modifier = Modifier
                        .tvFocusRing(shape = MaterialTheme.shapes.large)
                        .focusProperties { if (isTv && hasMedia) up = focusState.requester },
                    shape = Mate
