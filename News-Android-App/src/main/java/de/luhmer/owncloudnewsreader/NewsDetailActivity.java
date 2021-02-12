/**
 * Android ownCloud News
 *
 * @author David Luhmer
 * @copyright 2013 David Luhmer david-dev@live.de
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.luhmer.owncloudnewsreader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import de.greenrobot.dao.query.LazyList;
import de.luhmer.owncloudnewsreader.database.DatabaseConnectionOrm;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.databinding.ActivityNewsDetailBinding;
import de.luhmer.owncloudnewsreader.helper.ThemeUtils;
import de.luhmer.owncloudnewsreader.model.PodcastItem;
import de.luhmer.owncloudnewsreader.model.TTSItem;
import de.luhmer.owncloudnewsreader.view.PodcastSlidingUpPanelLayout;
import de.luhmer.owncloudnewsreader.widget.WidgetProvider;


public class NewsDetailActivity extends PodcastFragmentActivity {

	private static final String TAG = NewsDetailActivity.class.getCanonicalName();
	public static final String INCOGNITO_MODE_ENABLED = "INCOGNITO_MODE_ENABLED";

	/**
	 * The {@link PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link FragmentStatePagerAdapter}.
	 */
	private SectionsPagerAdapter mSectionsPagerAdapter;
	public LazyList<RssItem> rssItems;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	private ViewPager mViewPager;
	private int currentPosition;

	private MenuItem menuItem_PlayPodcast;
	private MenuItem menuItem_Starred;
	private MenuItem menuItem_Read;

	private DatabaseConnectionOrm dbConn;
	protected ActivityNewsDetailBinding binding;

	protected @Inject
	SharedPreferences mPrefs;

	private boolean mShowFastActions;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		((NewsReaderApplication) getApplication()).getAppComponent().injectActivity(this);

		super.onCreate(savedInstanceState);

		binding = ActivityNewsDetailBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		/*
		//make full transparent statusBar
		if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT < 21) {
			setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, true);
		}
		if (Build.VERSION.SDK_INT >= 19) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		}
		if (Build.VERSION.SDK_INT >= 21) {
			setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false);
			getWindow().setStatusBarColor(Color.TRANSPARENT);
		}


		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		*/

		/*
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(Color.WHITE);
		}
		*/

		/*
		// For Debugging the WebView using Chrome Remote Debugging
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			WebView.setWebContentsDebuggingEnabled(true);
		}
		*/

		if (binding.toolbarLayout.toolbar != null) {
			setSupportActionBar(binding.toolbarLayout.toolbar);
		}
		/*
		if (bottomAppBar != null) {
			setSupportActionBar(bottomAppBar);
		}
		*/
		//getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

		dbConn = new DatabaseConnectionOrm(this);
		Intent intent = getIntent();

		int item_id = 0;
		if (intent.hasExtra(NewsReaderListActivity.ITEM_ID)) {
			item_id = intent.getExtras().getInt(NewsReaderListActivity.ITEM_ID);
		}
		if (intent.hasExtra(NewsReaderListActivity.TITLE)) {
			getSupportActionBar().setTitle(intent.getExtras().getString(NewsReaderListActivity.TITLE));
		}

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		rssItems = dbConn.getAllRssItems();

		//If the Activity gets started from the Widget, read the item id and get the selected index in the cursor.
		if (intent.hasExtra(WidgetProvider.RSS_ITEM_ID)) {
			long rss_item_id = intent.getExtras().getLong(WidgetProvider.RSS_ITEM_ID);
			for (RssItem rssItem : rssItems) {
				if (rss_item_id == rssItem.getId()) {
					getSupportActionBar().setTitle(rssItem.getTitle());
					break;
				} else {
					item_id++;
				}
			}
		}

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		binding.progressIndicator.setMax(mSectionsPagerAdapter.getCount());

		// Set up the ViewPager with the sections adapter.
		mViewPager = findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);


		try {
			mViewPager.setCurrentItem(item_id, true);
			if (savedInstanceState == null) {
				// Only do that when activity is started for the first time. Not on orientation changes etc..
				pageChanged(item_id);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		mViewPager.addOnPageChangeListener(onPageChangeListener);
		// mBtnDisableIncognito.setOnClickListener(v -> {
		// 	toggleIncognitoMode();
		// });

		this.initFastActionBar();
	}

	@Override
	protected void onResume() {
		super.onResume();

		updateActionBarIcons();
	}

	@Override
	protected PodcastSlidingUpPanelLayout getPodcastSlidingUpPanelLayout() {
		return binding.slidingLayout;
	}

	private void toggleIncognitoMode() {
		// toggle incognito mode
		setIncognitoEnabled(!isIncognitoEnabled());

		for (int i = currentPosition - 1; i <= currentPosition + 1; i++) {
			Log.d(TAG, "change incognito for idx: " + i);
			WeakReference<NewsDetailFragment> ndf = mSectionsPagerAdapter.items.get(i);
			if (ndf != null) {
				ndf.get().syncIncognitoState();
				ndf.get().startLoadRssItemToWebViewTask();
			}
		}
	}

	/**
	 * Init fast action bar based on user settings.
	 * Only show if user selected setting CB_SHOW_FAST_ACTIONS. Otherwise hide.
	 * <p>
	 * author: emasty https://github.com/emasty
	 */
	private void initFastActionBar() {
		mShowFastActions = mPrefs.getBoolean(SettingsActivity.CB_SHOW_FAST_ACTIONS, true);

		if (mShowFastActions) {
			// Set click listener for buttons on action bar
			binding.faDetailBar.faOpenInBrowser.setOnClickListener(v -> this.openInBrowser(currentPosition));
			binding.faDetailBar.faToggle.setOnClickListener(v -> this.toggleFastActionBar()); // toggle expand / collapse
			binding.faDetailBar.faStar.setOnClickListener(v -> NewsDetailActivity.this.toggleRssItemStarredState());
			binding.faDetailBar.faMarkAsRead.setOnClickListener(v -> NewsDetailActivity.this.markRead(currentPosition));
			binding.faDetailBar.faShare.setOnClickListener(v -> this.share(currentPosition));

			binding.faDetailBar.getRoot().setVisibility(View.VISIBLE);

			// initially the bar should be opened in the expanded state
			this.toggleFastActionBar();
		} else {
			binding.faDetailBar.getRoot().setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * Expands or shrinks the fast action bar to show/hide secondary functions
	 */
	private void toggleFastActionBar() {
		int currentState = binding.faDetailBar.faCollapseLayout.getVisibility();
		switch (currentState) {
			case View.GONE:
				binding.faDetailBar.faToggle.setImageResource(R.drawable.ic_fa_expand);
				binding.faDetailBar.faCollapseLayout.setVisibility(View.VISIBLE);
				break;
			case View.VISIBLE:
				binding.faDetailBar.faToggle.setImageResource(R.drawable.ic_fa_shrink);
				binding.faDetailBar.faCollapseLayout.setVisibility(View.GONE);
				break;
			default:
				break;
		}
		//((Animatable)fastActionToggle.getDrawable()).start();
		binding.faDetailBar.faToggle.setScaleX(-1);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		rssItems.close();
	}

    private ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int pos) {
            pageChanged(pos);
        }

        @Override public void onPageScrolled(int arg0, float arg1, int arg2) { }

        @Override public void onPageScrollStateChanged(int arg0) { }
    };


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mPrefs.getBoolean(SettingsActivity.CB_NAVIGATE_WITH_VOLUME_BUTTONS_STRING, false)) {
			if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
				if (currentPosition < rssItems.size() - 1) {
					mViewPager.setCurrentItem(currentPosition + 1, true);
				}
				// capture event to avoid volume change at end of feed
				return true;
			} else if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
				if (currentPosition > 0) {
					mViewPager.setCurrentItem(currentPosition - 1, true);
				}
				// capture event to avoid volume change at beginning of feed
				return true;
			}
		}
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			NewsDetailFragment ndf = getNewsDetailFragmentAtPosition(currentPosition);//(NewsDetailFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":" + currentPosition);

			if (ndf != null && ndf.canNavigateBack()) {
				ndf.navigateBack();
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
			// capture event to suppress android system sound
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	private void pageChanged(int position) {
		stopVideoOnCurrentPage();
		currentPosition = position;
		resumeVideoPlayersOnCurrentPage();
		binding.progressIndicator.setProgress(position + 1);

		if (rssItems.get(position).getFeed() != null) {
			// Try getting the feed title and use it for the action bar title
			getSupportActionBar().setTitle(rssItems.get(position).getFeed().getFeedTitle());
		} else {
			getSupportActionBar().setTitle(rssItems.get(position).getTitle());
		}

		RssItem rssItem = rssItems.get(position);
		if (!rssItem.getRead_temp()) {
			if (!NewsReaderListActivity.stayUnreadItems.contains(rssItem.getId())) {
				markItemAsReadOrUnread(rssItems.get(position), true);
			}

			mPostDelayHandler.delayTimer();

			Log.v("PAGE CHANGED", "PAGE: " + position + " - IDFEED: " + rssItems.get(position).getId());
		}
		updateActionBarIcons();
	}


	private NewsDetailFragment getNewsDetailFragmentAtPosition(int position) {
		if (mSectionsPagerAdapter.items.get(position) != null)
			return mSectionsPagerAdapter.items.get(position).get();
		return null;
	}

	private void resumeVideoPlayersOnCurrentPage() {
		NewsDetailFragment fragment = getNewsDetailFragmentAtPosition(currentPosition);
		if (fragment != null) { // could be null if not instantiated yet
			fragment.resumeCurrentPage();
		}

	}

	private void stopVideoOnCurrentPage() {
		NewsDetailFragment fragment = getNewsDetailFragmentAtPosition(currentPosition);
		if (fragment != null) { // could be null if not instantiated yet
			fragment.pauseCurrentPage();
		}
	}

	public void updateActionBarIcons() {
		RssItem rssItem = rssItems.get(currentPosition);

		boolean isStarred = rssItem.getStarred_temp();
		boolean isRead = rssItem.getRead_temp();

		PodcastItem podcastItem = DatabaseConnectionOrm.ParsePodcastItemFromRssItem(this, rssItem);
		boolean podcastAvailable = !"".equals(podcastItem.link);

		if (menuItem_PlayPodcast != null) {
			menuItem_PlayPodcast.setVisible(podcastAvailable);
		}

		if (menuItem_Starred != null) {
			if (isStarred) {
				menuItem_Starred.setIcon(R.drawable.ic_action_star_dark);
				binding.faDetailBar.faStar.setImageResource(R.drawable.ic_action_star_dark);
			} else {
				menuItem_Starred.setIcon(R.drawable.ic_action_star_border_dark);
				binding.faDetailBar.faStar.setImageResource(R.drawable.ic_action_star_border_dark);
			}
		}

		if (menuItem_Read != null) {
			if (isRead) {
				menuItem_Read.setIcon(R.drawable.ic_check_box_white);
				menuItem_Read.setChecked(true);
				binding.faDetailBar.faMarkAsRead.setImageResource(R.drawable.ic_check_box_white);
			} else {
				menuItem_Read.setIcon(R.drawable.ic_check_box_outline_blank_white);
				menuItem_Read.setChecked(false);
				binding.faDetailBar.faMarkAsRead.setImageResource(R.drawable.ic_check_box_outline_blank_white);
			}
		}
	}


	@Override
	public void onBackPressed() {
		if (!handlePodcastBackPressed())
			super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.news_detail, menu);

		MenuItem menuItem_OpenInBrowser = menu.findItem(R.id.action_openInBrowser);
		MenuItem menuItem_ShareItem = menu.findItem(R.id.action_ShareItem);

		menuItem_Starred = menu.findItem(R.id.action_starred);
		menuItem_Read = menu.findItem(R.id.action_read);
		menuItem_PlayPodcast = menu.findItem(R.id.action_playPodcast);

		if (mShowFastActions) {
			menuItem_Starred.setVisible(false);
			menuItem_Read.setVisible(false);
			menuItem_OpenInBrowser.setVisible(false);
			menuItem_ShareItem.setVisible(false);
		}

		Set<String> selections = mPrefs.getStringSet("sp_news_detail_actionbar_icons", new HashSet<>());
		String[] selected = selections.toArray(new String[]{});
		for (String selection : selected) {
			switch (selection) {
				case "open_in_browser":
					menuItem_OpenInBrowser.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
					break;
				case "share":
					menuItem_ShareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
					break;
				case "podcast":
					menuItem_PlayPodcast.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
					break;
				//case "tts":
				//    menu.findItem(R.id.action_tts).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				//    break;
			}
		}

		updateActionBarIcons();

		initIncognitoMode();

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		RssItem rssItem = rssItems.get(currentPosition);

		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;

			case R.id.action_read:
				this.markRead(currentPosition);
				break;

			case R.id.action_starred:
				toggleRssItemStarredState();
				break;

			case R.id.action_openInBrowser:
				this.openInBrowser(currentPosition);
				break;

			case R.id.action_playPodcast:
				openPodcast(rssItem);
				break;

			case R.id.action_tts:
				this.startTTS(currentPosition);
				break;

			case R.id.action_ShareItem:
				this.share(currentPosition);
				break;

			case R.id.action_incognito_mode:
				toggleIncognitoMode();
				break;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Opens current article in selected browser
	 *
	 * @param currentPosition currently viewed article
	 */
	private void openInBrowser(int currentPosition) {
		RssItem rssItem = rssItems.get(currentPosition);
		NewsDetailFragment newsDetailFragment = getNewsDetailFragmentAtPosition(currentPosition);
		String link = "about:blank";

		if (newsDetailFragment != null && newsDetailFragment.binding.webview != null) {
			link = newsDetailFragment.binding.webview.getUrl();
		}

		if ("about:blank".equals(link)) {
			link = rssItem.getLink();
		}

		if (link.length() > 0)
		{
			newsDetailFragment.loadURL(link);
		}
	}

	/**
	 * Initiates share event for current item
	 *
	 * @param currentPosition currently viewed article
	 */
	private void share(int currentPosition) {
		RssItem rssItem = rssItems.get(currentPosition);
		String title = rssItem.getTitle();
		String content = rssItem.getLink();

		NewsDetailFragment fragment = getNewsDetailFragmentAtPosition(currentPosition);
		if (fragment != null) { // could be null if not instantiated yet
			if (!fragment.binding.webview.getUrl().equals("about:blank") && !fragment.binding.webview.getUrl().trim().equals("")) {
				content = fragment.binding.webview.getUrl();
				title = fragment.binding.webview.getTitle();
			}
		}

		Intent share = new Intent(Intent.ACTION_SEND);
		share.setType("text/plain");
		//share.putExtra(Intent.EXTRA_SUBJECT, rssFiles.get(currentPosition).getTitle());
		//share.putExtra(Intent.EXTRA_TEXT, rssFiles.get(currentPosition).getLink());
		share.putExtra(Intent.EXTRA_SUBJECT, title);
		share.putExtra(Intent.EXTRA_TEXT, content);

		startActivity(Intent.createChooser(share, "Share Item"));

	}

	/**
	 * Starts TTS for current position
	 *
	 * @param currentPosition currently viewed article
	 */
	private void startTTS(int currentPosition) {
		RssItem rssItem = rssItems.get(currentPosition);
		String text = rssItem.getTitle() + "\n\n " + Html.fromHtml(rssItem.getBody()).toString();
		// Log.d(TAG, text);
		TTSItem ttsItem = new TTSItem(rssItem.getId(), rssItem.getAuthor(), rssItem.getTitle(), text, rssItem.getFeed().getFaviconUrl());
		openMediaItem(ttsItem);
	}

	/**
	 * Toggles marked as read for current element
	 *
	 * @param currentPosition currently viewed article
	 */
	private void markRead(int currentPosition) {
		RssItem rssItem = rssItems.get(currentPosition);
		markItemAsReadOrUnread(rssItem, !menuItem_Read.isChecked());
		updateActionBarIcons();
		mPostDelayHandler.delayTimer();
	}


	public void toggleRssItemStarredState() {
		RssItem rssItem = rssItems.get(currentPosition);
		Boolean curState = rssItem.getStarred_temp();
		rssItem.setStarred_temp(!curState);
		dbConn.updateRssItem(rssItem);

		updateActionBarIcons();

		mPostDelayHandler.delayTimer();
	}

	private boolean isChromeDefaultBrowser() {
		Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));
		ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);

		Log.v(TAG, "Default Browser is: " + resolveInfo.loadLabel(getPackageManager()).toString());
		return (resolveInfo.loadLabel(getPackageManager()).toString().contains("Chrome"));
	}

	private void markItemAsReadOrUnread(RssItem item, boolean read) {
		NewsReaderListActivity.stayUnreadItems.add(item.getId());

		item.setRead_temp(read);
		dbConn.updateRssItem(item);
		updateActionBarIcons();
	}

	@Override
	public void finish() {
		Intent intent = new Intent();
		intent.putExtra("POS", mViewPager.getCurrentItem());
		setResult(RESULT_OK, intent);
		super.finish();
	}

	public boolean isIncognitoEnabled() {
		return mPrefs.getBoolean(INCOGNITO_MODE_ENABLED, false);
	}

	public void setIncognitoEnabled(boolean enabled) {
		mPrefs.edit().putBoolean(INCOGNITO_MODE_ENABLED, enabled).commit();
		initIncognitoMode();
	}

	public void initIncognitoMode() {
		int color = getResources().getColor(isIncognitoEnabled() ? R.color.material_grey_900 : R.color.colorPrimary);
		ThemeUtils.colorizeToolbar(binding.toolbarLayout.toolbar, color);
		//ThemeUtils.colorizeToolbar(bottomAppBar, color);
		//ThemeUtils.changeStatusBarColor(this, color);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//getWindow().setNavigationBarColor(color);
			getWindow().setStatusBarColor(color);

			/*
			switch (ThemeChooser.getSelectedTheme()) {
				case LIGHT:
					Log.d(TAG, "initIncognitoMode: LIGHT");
					setLightStatusBar(getWindow().getDecorView());
					getWindow().setStatusBarColor(Color.WHITE);
					break;
				case DARK:
					clearLightStatusBar(getWindow().getDecorView());
					Log.d(TAG, "initIncognitoMode: DARK");
					getWindow().setStatusBarColor(getResources().getColor(R.color.material_grey_900));
					break;
				case OLED:
					clearLightStatusBar(getWindow().getDecorView());
					Log.d(TAG, "initIncognitoMode: OLED");
					getWindow().setStatusBarColor(Color.BLACK);
					break;
			}
			*/
		}

	}

	/*
	private void setLightStatusBar(@NonNull View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			int flags = view.getSystemUiVisibility(); // get current flag
			flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;   // add LIGHT_STATUS_BAR to flag
			view.setSystemUiVisibility(flags);
		}
	}

	public static void clearLightStatusBar(@NonNull View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			int flags = view.getSystemUiVisibility();
			flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
			view.setSystemUiVisibility(flags);
		}
	}
	*/

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	//public class SectionsPagerAdapter extends FragmentPagerAdapter {
	public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

		SparseArray<WeakReference<NewsDetailFragment>> items = new SparseArray<>();

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);

			for (Fragment fragment : fm.getFragments()) {
				if (fragment instanceof NewsDetailFragment) {
					int id = ((NewsDetailFragment) fragment).getSectionNumber();
					Log.v(TAG, "Retaining NewsDetailFragment with ID: " + id);
					items.put(id, new WeakReference<>((NewsDetailFragment) fragment));
				}
			}
		}

		@Override
		public Fragment getItem(int position) {
			NewsDetailFragment fragment = null;

			if(items.get(position) != null) {
				fragment = items.get(position).get();
			}

			if(fragment == null) {
				fragment = new NewsDetailFragment();
				Bundle args = new Bundle();
				args.putInt(NewsDetailFragment.ARG_SECTION_NUMBER, position);
				fragment.setArguments(args);
				items.put(position, new WeakReference<>(fragment));
			}

			return fragment;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			items.remove(position);

			super.destroyItem(container, position, object);
		}

		@Override
		public int getCount() {
			//return cursor.getCount();
			return rssItems.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return null;
		}
	}

	protected void setBackgroundColorOfViewPager(int backgroundColor) {
		this.mViewPager.setBackgroundColor(backgroundColor);
	}
}