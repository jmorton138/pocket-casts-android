package au.com.shiftyjelly.pocketcasts.player.view.bookmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.asFlow
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.player.viewmodel.BookmarksViewModel
import au.com.shiftyjelly.pocketcasts.player.viewmodel.BookmarksViewModel.Companion.UNKNOWN_SOURCE_MESSAGE
import au.com.shiftyjelly.pocketcasts.player.viewmodel.PlayerViewModel
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.views.dialog.OptionsDialog
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import au.com.shiftyjelly.pocketcasts.views.multiselect.MultiSelectBookmarksHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class BookmarksFragment : BaseFragment() {

    companion object {
        private const val ARG_SOURCE_VIEW = "sourceView"
        private const val ARG_EPISODE_UUID = "episodeUuid"
        private const val ARG_FORCE_DARK_THEME = "forceDarkTheme"
        fun newInstance(
            sourceView: SourceView,
            episodeUuid: String? = null,
            forceDarkTheme: Boolean = false,
        ) = BookmarksFragment().apply {
            arguments = bundleOf(
                ARG_SOURCE_VIEW to sourceView.analyticsValue,
                ARG_EPISODE_UUID to episodeUuid,
                ARG_FORCE_DARK_THEME to forceDarkTheme,
            )
        }
    }

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val bookmarksViewModel: BookmarksViewModel by viewModels()

    @Inject
    lateinit var multiSelectHelper: MultiSelectBookmarksHelper

    @Inject
    lateinit var settings: Settings

    private val sourceView: SourceView
        get() = SourceView.fromString(arguments?.getString(ARG_SOURCE_VIEW))

    private val episodeUuid: String?
        get() = arguments?.getString(ARG_EPISODE_UUID)

    private val forceDarkTheme: Boolean
        get() = arguments?.getBoolean(ARG_FORCE_DARK_THEME) ?: false

    private val overrideTheme: Theme.ThemeType
        get() = when (sourceView) {
            SourceView.EPISODE_DETAILS -> if (forceDarkTheme && theme.isLightTheme) Theme.ThemeType.DARK else theme.activeTheme
            SourceView.PLAYER -> if (Theme.isDark(context)) theme.activeTheme else Theme.ThemeType.DARK
            else -> throw IllegalStateException("$UNKNOWN_SOURCE_MESSAGE: $sourceView")
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setContent {
            AppTheme(overrideTheme) {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                // Hack to allow nested scrolling inside bottom sheet viewpager
                // https://stackoverflow.com/a/70195667/193545
                Surface(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
                    val listData = playerViewModel.listDataLive.asFlow()
                        .collectAsState(initial = null)

                    val episodeUuid = episodeUuid(listData)
                    if (episodeUuid != null) {
                        BookmarksPage(
                            episodeUuid = episodeUuid,
                            backgroundColor = requireNotNull(backgroundColor(listData)),
                            textColor = requireNotNull(textColor(listData)),
                            sourceView = sourceView,
                            bookmarksViewModel = bookmarksViewModel,
                            onRowLongPressed = { bookmark ->
                                multiSelectHelper.defaultLongPress(
                                    multiSelectable = bookmark,
                                    fragmentManager = childFragmentManager,
                                    forceDarkTheme = sourceView == SourceView.PLAYER,
                                )
                            },
                            showOptionsDialog = { showOptionsDialog(it) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun episodeUuid(listData: State<PlayerViewModel.ListData?>) =
        when (sourceView) {
            SourceView.PLAYER -> listData.value?.podcastHeader?.episodeUuid
            SourceView.EPISODE_DETAILS -> episodeUuid
            else -> throw IllegalStateException("$UNKNOWN_SOURCE_MESSAGE: $sourceView")
        }

    @Composable
    private fun backgroundColor(listData: State<PlayerViewModel.ListData?>) =
        when (sourceView) {
            SourceView.PLAYER -> listData.value?.let { Color(it.podcastHeader.backgroundColor) }
            SourceView.EPISODE_DETAILS -> MaterialTheme.theme.colors.primaryUi01
            else -> throw IllegalStateException("$UNKNOWN_SOURCE_MESSAGE: $sourceView")
        }

    @Composable
    private fun textColor(listData: State<PlayerViewModel.ListData?>) =
        when (sourceView) {
            SourceView.PLAYER -> listData.value?.let { Color(it.podcastHeader.backgroundColor) }
            SourceView.EPISODE_DETAILS -> MaterialTheme.theme.colors.primaryText02
            else -> throw IllegalStateException("$UNKNOWN_SOURCE_MESSAGE: $sourceView")
        }

    private val showOptionsDialog: (Int) -> Unit = { selectedValue ->
        activity?.supportFragmentManager?.let {
            OptionsDialog()
                .setForceDarkTheme(sourceView == SourceView.PLAYER)
                .addTextOption(
                    titleId = LR.string.bookmarks_select_option,
                    imageId = IR.drawable.ic_multiselect,
                    click = {
                        multiSelectHelper.isMultiSelecting = true
                    }
                )
                .addTextOption(
                    titleId = LR.string.bookmarks_sort_option,
                    imageId = IR.drawable.ic_sort,
                    valueId = selectedValue,
                    click = {
                        BookmarksSortByDialog(
                            settings = settings,
                            changeSortOrder = bookmarksViewModel::changeSortOrder,
                            sourceView = SourceView.PLAYER,
                            forceDarkTheme = true,
                        ).show(
                            context = requireContext(),
                            fragmentManager = it
                        )
                    }
                ).show(it, "bookmarks_options_dialog")
        }
    }
}