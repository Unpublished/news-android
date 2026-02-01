/*
 * Android ownCloud News
 *
 * @author David Luhmer
 * @copyright 2013 David Luhmer david-dev@live.de
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package de.luhmer.owncloudnewsreader

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import de.luhmer.owncloudnewsreader.databinding.FragmentContainerBinding
import de.luhmer.owncloudnewsreader.helper.ThemeChooser
import javax.inject.Inject

/**
 * A settings activity that presents application settings using Jetpack Compose.
 * The settings UI itself is still rendered by SettingsFragment for compatibility,
 * but the activity wrapper uses Compose for the AppBar and layout structure.
 */
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var mPrefs: SharedPreferences

    var resultIntent = Intent()

    companion object {
        const val EDT_USERNAME_STRING = "edt_username"
        const val EDT_PASSWORD_STRING = "edt_password"
        const val EDT_OWNCLOUDROOTPATH_STRING = "edt_owncloudRootPath"
        const val SW_USE_SINGLE_SIGN_ON = "sw_use_single_sign_on"
        const val EDT_CLEAR_CACHE = "edt_clearCache"
        const val CB_SYNCONSTARTUP_STRING = "cb_AutoSyncOnStart"
        const val CB_SHOWONLYUNREAD_STRING = "cb_ShowOnlyUnread"
        const val CB_NAVIGATE_WITH_VOLUME_BUTTONS_STRING = "cb_NavigateWithVolumeButtons"
        const val LV_CACHE_IMAGES_OFFLINE_STRING = "lv_cacheImagesOffline"
        const val CB_MARK_AS_READ_WHILE_SCROLLING_STRING = "cb_MarkAsReadWhileScrolling"
        const val CB_SYNC_WHEN_SCROLLED_TO_BOTTOM_STRING = "cb_SyncWhenScrolledToBottom"
        const val CB_SHOW_FAST_ACTIONS = "cb_ShowFastActions"
        const val CB_PREF_BACK_OPENS_DRAWER = "cb_prefBackButtonOpensDrawer"
        const val CB_DISABLE_HOSTNAME_VERIFICATION_STRING = "cb_DisableHostnameVerification"
        const val CB_SKIP_DETAILVIEW_AND_OPEN_BROWSER_DIRECTLY_STRING = "cb_openInBrowserDirectly"
        const val PREF_SERVER_SETTINGS = "pref_server_settings"
        const val PREF_SYNC_SETTINGS = "pref_sync_settings"
        const val SYNC_INTERVAL_IN_MINUTES_STRING_DEPRECATED = "SYNC_INTERVAL_IN_MINUTES_STRING"
        const val SP_APP_THEME = "sp_app_theme"
        const val CB_OLED_MODE = "cb_oled_mode"
        const val CB_DETAILED_VIEW_ZOOM = "cb_detailed_view_zoom"
        const val CB_EXTERNAL_PLAYER = "cb_external_player"
        const val SP_FEED_LIST_LAYOUT = "sp_feed_list_layout"
        const val RI_FEED_LIST_LAYOUT = "ai_feed_list_layout"
        const val SP_FONT_SIZE = "sp_font_size"
        const val RI_CACHE_CLEARED = "CACHE_CLEARED"
        const val SP_MAX_CACHE_SIZE = "sp_max_cache_size"
        const val SP_SORT_ORDER = "sp_sort_order"
        const val SP_DISPLAY_BROWSER = "sp_display_browser"
        const val SP_SEARCH_IN = "sp_search_in"
        const val SP_SWIPE_RIGHT_ACTION = "sp_swipe_right_action"
        const val SP_SWIPE_LEFT_ACTION = "sp_swipe_left_action"
        const val SP_SWIPE_RIGHT_ACTION_DEFAULT = "1"
        const val SP_SWIPE_LEFT_ACTION_DEFAULT = "2"
        const val CB_VERSION = "cb_version"
        const val CB_REPORT_ISSUE = "cb_reportIssue"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as NewsReaderApplication).appComponent.injectActivity(this)

        ThemeChooser.chooseTheme(this)
        super.onCreate(savedInstanceState)
        ThemeChooser.afterOnCreate(this)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    onNavigateUp = { finish() }
                )
            }
        }

        // some settings might add a few flags to the result Intent at runtime
        // (e.g. clearing cache / switching list layout / theme / ...)
        setResult(Activity.RESULT_OK, resultIntent)

        // Add SettingsFragment to the container after setContent
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()

        // Fix GHSL-2021-1033
        val feedListLayout = mPrefs.getString(SP_FEED_LIST_LAYOUT, "0")
        resultIntent.putExtra(RI_FEED_LIST_LAYOUT, feedListLayout)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_activity_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Navigate up"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Use AndroidViewBinding to embed the fragment container
            AndroidViewBinding(
                factory = FragmentContainerBinding::inflate,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
