package de.luhmer.owncloudnewsreader;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import androidx.fragment.app.DialogFragment;
import de.luhmer.owncloudnewsreader.helper.NewsFileUtils;
import de.luhmer.owncloudnewsreader.notification.NextcloudNotificationManager;

import static androidx.core.content.PermissionChecker.checkSelfPermission;

public class NewsDetailImageDialogFragment extends DialogFragment {

    private static final int REQUEST_DIRECTORY = 0;
    public enum TYPE { IMAGE, URL }
    static final String TAG = NewsDetailImageDialogFragment.class.getCanonicalName();

    private int mDialogIcon;
    private String mDialogTitle;
    private String mDialogText;
    URL mImageUrl;
    private TYPE mDialogType;

    long downloadID;
    DownloadManager downloadManager;
    private BroadcastReceiver downloadCompleteReceiver;

    private LinkedHashMap<String, MenuAction> mMenuItems;

    static NewsDetailImageDialogFragment newInstanceImage(String dialogTitle, Integer titleIcon, String dialogText, URL imageUrl) {
        NewsDetailImageDialogFragment f = new NewsDetailImageDialogFragment();

        if(titleIcon == null) {
            titleIcon = android.R.drawable.ic_menu_info_details;
        }

        Bundle args = new Bundle();
        args.putSerializable("dialogType", TYPE.IMAGE);
        args.putInt("titleIcon", titleIcon);
        args.putString("title", dialogTitle);
        args.putString("text", dialogText);
        args.putSerializable("imageUrl", imageUrl);
        f.setArguments(args);
        return f;
    }

    protected static NewsDetailImageDialogFragment newInstanceUrl(String dialogTitle, String dialogText) {
        NewsDetailImageDialogFragment f = new NewsDetailImageDialogFragment();

        Bundle args = new Bundle();
        args.putSerializable("dialogType", TYPE.URL);
        args.putInt("titleIcon", android.R.drawable.ic_menu_info_details);
        args.putString("title", dialogTitle);
        args.putString("text", dialogText);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogIcon = getArguments().getInt("titleIcon");
        mDialogTitle = getArguments().getString("title");
        mDialogText = getArguments().getString("text");
        mImageUrl = (URL) getArguments().getSerializable("imageUrl");
        mDialogType = (TYPE) getArguments().getSerializable("dialogType");

        mMenuItems = new LinkedHashMap<>();

        //Build the menu
        switch(mDialogType) {
            case IMAGE:
                if(mImageUrl.toString().startsWith("http")) { //Only allow download for http[s] images (prevent download of cached images)
                    mMenuItems.put(getString(R.string.action_img_download), new MenuActionLongClick() {
                        @Override
                        public void execute() {
                            if (haveStoragePermission()) {
                                downloadImage(mImageUrl);
                            }
                        }

                        public void executeLongClick() {
                            changeDownloadDir();
                        }
                    });
                    mMenuItems.put(getString(R.string.action_img_open), new MenuAction() {
                        @Override
                        public void execute() {
                            openLinkInBrowser(mImageUrl);
                        }
                    });
                    mMenuItems.put(getString(R.string.action_img_sharelink), new MenuAction() {
                        @Override
                        public void execute() {
                            shareImage();
                        }
                    });
                    mMenuItems.put(getString(R.string.action_img_copylink), new MenuAction() {
                        @Override
                        public void execute() {
                            copyToClipboard(mDialogTitle, mImageUrl.toString());
                        }
                    });
                } else if (mImageUrl.toString().startsWith("file:///")) {
                    mMenuItems.put(getString(R.string.action_img_download), new MenuActionLongClick() {
                        @Override
                        public void execute() {
                            if (haveStoragePermission()) {
                                storeCachedImage(mImageUrl.getPath());
                            }
                        }

                        public void executeLongClick() {
                            changeDownloadDir();
                        }
                    });
                } else {
                    mDialogTitle = "Unknown Type";
                    mDialogText = "The URL type of image url: \"" + mImageUrl.toString() + "\" is unknown, please report this issue.";
                }
                break;
            case URL:
                mMenuItems.put(getString(R.string.action_link_open), new MenuAction() {
                    @Override
                    public void execute() {
                        try {
                            openLinkInBrowser(new URL(mDialogText));
                        } catch (MalformedURLException e) {
                            Toast.makeText(getActivity(), getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });
                mMenuItems.put(getString(R.string.action_link_share), new MenuAction() {
                    @Override
                    public void execute() {
                        shareLink();
                    }
                });
                mMenuItems.put(getString(R.string.action_link_copy), new MenuAction() {
                    @Override
                    public void execute() {
                        copyToClipboard(mDialogTitle, mDialogText);
                    }
                });
                break;
        }

        int style = DialogFragment.STYLE_NO_TITLE;
        int theme = R.style.FloatingDialog;
        setStyle(style, theme);
    }

    @Override
    public void onStart() {
        showDownloadShowcase();
        super.onStart();
    }

    private void showDownloadShowcase() {
        if(mMenuItems.containsKey(getActivity().getString(R.string.action_img_download))) {
            List<String> menuItemsList = new ArrayList<>(mMenuItems.keySet());
            int position = menuItemsList.indexOf(getActivity().getString(R.string.action_img_download));
            Log.v(TAG, "Position of Download Menu: " + position);
            /*
            // Bug in the Library.. ShowcaseView is rendered behind the DialogFragment //TODO check https://github.com/deano2390/MaterialShowcaseView/issues/51 for updates
            new MaterialShowcaseView.Builder(getActivity())
                    .setTarget(mListView /*.getChildAt(position) *//*)
                    .setDismissText("GOT IT")
                    .setContentText("Long press to change the target download directory")
                    .setDelay(300) // optional but starting animations immediately in onCreate can make them choppy
                    .singleUse("LONG_PRESS_DOWNLOAD_TARGET_DIR") // provide a unique ID used to ensure it is only shown once
                    .show();
            */
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_dialog_image, container, false);

        TextView tvTitle   = v.findViewById(R.id.ic_menu_title);
        TextView tvText    = v.findViewById(R.id.ic_menu_item_text);
        ImageView imgTitle = v.findViewById(R.id.ic_menu_gallery);

        tvTitle.setText(mDialogTitle);
        tvText.setText(mDialogText);
        imgTitle.setImageResource(mDialogIcon);

        if(mDialogType == TYPE.IMAGE) {
            registerImageDownloadReceiver();
            if(mDialogText.equals(mDialogTitle) || mDialogText.equals("")) {
                tvText.setVisibility(View.GONE);
            }
        }

        ListView mListView = (ListView) v.findViewById(R.id.ic_menu_item_list);
        List<String> menuItemsList = new ArrayList<>(mMenuItems.keySet());

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                getActivity(),
                R.layout.fragment_dialog_listviewitem,
                menuItemsList);

        mListView.setAdapter(arrayAdapter);
        mListView.setLongClickable(true);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String key = arrayAdapter.getItem(i);
                MenuAction mAction = mMenuItems.get(key);
                mAction.execute();
            }
        });

        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                String key = arrayAdapter.getItem(position);
                try {
                    MenuActionLongClick mAction = (MenuActionLongClick) mMenuItems.get(key);
                    mAction.executeLongClick();
                } catch (ClassCastException e) {
                    return false;
                }
                return true;
            }
        });

        return v;
    }

    @Override
    public void onDestroyView() {
        unregisterImageDownloadReceiver();
        super.onDestroyView();
    }


    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getActivity(), getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void shareImage() {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, mDialogText);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, mImageUrl.toString());
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.intent_title_share)));
        dismiss();
    }

    private void shareLink() {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, mDialogTitle);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, mDialogText);
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.intent_title_share)));
        dismiss();
    }


    private void openLinkInBrowser(URL url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url.toString()));
        startActivity(i);
        dismiss();
    }

    void downloadImage(URL url) {
        Toast.makeText(getActivity().getApplicationContext(), getString(R.string.toast_img_download_wait), Toast.LENGTH_SHORT).show();

        if(isExternalStorageWritable()) {
            String filename = url.getFile().substring(url.getFile().lastIndexOf('/') + 1, url.getFile().length());
            downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url.toString()));
            request.setDestinationUri(getDownloadDir(filename));
            request.setTitle(getString(R.string.app_name) + " - " + getString(R.string.action_img_download));
            request.setDescription(filename);
            request.setVisibleInDownloadsUi(true);
            //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            //request.setAllowedOverRoaming(false);
            //request.setVisibleInDownloadsUi(false);
            //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            downloadID = downloadManager.enqueue(request);
            getDialog().hide();
        } else {
            Toast.makeText(getActivity().getApplicationContext(), getString(R.string.toast_img_notwriteable), Toast.LENGTH_LONG).show();
            dismiss();
        }
    }


    void storeCachedImage(String path) {
        final String CHANNEL_ID = "Store cached Image";
        if(isExternalStorageWritable()) {
            String filename = path.substring(path.lastIndexOf('/') + 1, path.length());
            File dstPath = new File(getDownloadDir(filename).getPath());
            try {
                NewsFileUtils.copyFile(new FileInputStream(path), new FileOutputStream(dstPath));
            } catch (IOException e) {
                Toast.makeText(getActivity().getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
            NextcloudNotificationManager.showNotificationSaveSingleCachedImageService(getActivity().getApplicationContext(), CHANNEL_ID, dstPath);
            getDialog().hide();
        } else {
            Toast.makeText(getActivity().getApplicationContext(), getString(R.string.toast_img_notwriteable), Toast.LENGTH_LONG).show();
            dismiss();
        }
    }

    public boolean haveStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.v("Permission error","You have permission");
                return true;
            } else {
                Log.e("Permission error","Asking for permission");
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //you dont need to worry about these stuff below api level 23
            Log.e("Permission error","You already have the permission");
            return true;
        }
    }


    void changeDownloadDir() {
        final Intent chooserIntent = new Intent(getActivity(), DirectoryChooserActivity.class);
        final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                .initialDirectory(getActivity().getPreferences(Context.MODE_PRIVATE).getString("manualImageDownloadLocation", ""))
                .newDirectoryName("new folder")
                .allowNewDirectoryNameModification(true)
                .allowReadOnlyDirectory(false)
                .build();

        chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
        startActivityForResult(chooserIntent, REQUEST_DIRECTORY);
    }

    private void setNewDownloadDir(String path) {
        if(path.equals("")) {
            path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
        }
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("manualImageDownloadLocation", path);
        editor.commit();
    }

    private Uri getDownloadDir(String filename) {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        String dir = sharedPref.getString("manualImageDownloadLocation", "");
        if(dir.equals("")) { //sharedPref has never been set
            setNewDownloadDir(""); //set to default public download dir
            return getDownloadDir(filename);
        }
        String tmp = "file://" +dir +"/" +filename;
        return Uri.parse(tmp);
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DIRECTORY) {
            if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                String dir = data.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR);
                setNewDownloadDir(dir);
            }
        }
    }

    private void unregisterImageDownloadReceiver() {
        if (downloadCompleteReceiver != null) {
            getActivity().unregisterReceiver(downloadCompleteReceiver);
            downloadCompleteReceiver = null;
        }
    }

    private void registerImageDownloadReceiver() {
        if(downloadCompleteReceiver != null) return;

        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long refID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadID == refID) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(refID);
                    Cursor cursor = downloadManager.query(query);
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(columnIndex);
                    int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                    int reason = cursor.getInt(columnReason);

                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            Toast.makeText(getActivity().getApplicationContext(), getString(R.string.toast_img_saved), Toast.LENGTH_LONG).show();

                            //String imagePath = downloadManager.getUriForDownloadedFile(refID).toString();

                            String downloadFileLocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                            File image = new File(Uri.parse(downloadFileLocalUri).getPath());

                            NextcloudNotificationManager.showNotificationDownloadSingleImageComplete(context, image);

                            if(isVisible()) {
                                dismiss();
                            }
                            break;
                        case DownloadManager.STATUS_FAILED:
                            Toast.makeText(getActivity().getApplicationContext(), getString(R.string.error_download_failed) + ": " + reason, Toast.LENGTH_LONG).show();
                            if(isVisible()) {
                                dismiss();
                            }
                            break;
                        default:
                            Log.e(TAG, "this should never happen! - unknown download status");
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        getActivity().registerReceiver(downloadCompleteReceiver, intentFilter);
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    interface MenuAction {
        void execute();
    }
    interface MenuActionLongClick extends MenuAction {
        void executeLongClick();
    }
}
