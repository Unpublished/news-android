/**
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

package de.luhmer.owncloudnewsreader;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.helper.VersionCheckHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import com.nextcloud.android.sso.ui.UiExceptionManager;

import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Inject;

import androidx.fragment.app.DialogFragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import de.luhmer.owncloudnewsreader.authentication.AuthenticatorActivity;
import de.luhmer.owncloudnewsreader.database.DatabaseConnectionOrm;
import de.luhmer.owncloudnewsreader.di.ApiProvider;
import de.luhmer.owncloudnewsreader.model.NextcloudNewsVersion;
import de.luhmer.owncloudnewsreader.ssl.OkHttpSSLClient;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static de.luhmer.owncloudnewsreader.Constants.MIN_NEXTCLOUD_FILES_APP_VERSION_CODE;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class LoginDialogFragment extends DialogFragment {

    final String TAG = LoginDialogFragment.class.getCanonicalName();

	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	protected @Inject ApiProvider mApi;
    protected @Inject SharedPreferences mPrefs;
	//private UserLoginTask mAuthTask = null;

    Activity mActivity;

    // Values for email and password at the time of the login attempt.
    private String mUsername;
    private String mPassword;
    private String mOc_root_path;
    private boolean mCbDisableHostnameVerification;

    // UI references.
    protected @BindView(R.id.username) EditText mUsernameView;
    protected @BindView(R.id.password) EditText mPasswordView;
    protected @BindView(R.id.password_container) TextInputLayout mPasswordContainerView;
    protected @BindView(R.id.edt_owncloudRootPath) EditText mOc_root_path_View;
    protected @BindView(R.id.cb_AllowAllSSLCertificates) CheckBox mCbDisableHostnameVerificationView;
    protected @BindView(R.id.imgView_ShowPassword) ImageView mImageViewShowPwd;
    protected @BindView(R.id.swSingleSignOn) Switch mSwSingleSignOn;

    SingleSignOnAccount importedAccount = null;
    boolean mPasswordVisible = false;
    LoginSuccessfulListener listener;

    public interface LoginSuccessfulListener {
        void loginSucceeded();
    }

    public void setActivity(Activity mActivity) {
		this.mActivity = mActivity;
	}

	/**
	 * @param listener the listener to set
	 */
	public void setListener(LoginSuccessfulListener listener) {
		this.listener = listener;
	}

	@Override
	public void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);
		((NewsReaderApplication) getActivity().getApplication()).getAppComponent().injectFragment(this);
	}

    public static LoginDialogFragment newInstance() {
        LoginDialogFragment loginDialogFragment = new LoginDialogFragment();
        Bundle args = new Bundle();
        loginDialogFragment.setArguments(args);
        return loginDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //setRetainInstance(true);

        // Build the dialog and set up the button click handlers
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_signin, null);
        ButterKnife.bind(this, view);

        builder.setView(view);
		builder.setTitle(getString(R.string.action_sign_in_short));

		builder.setPositiveButton(getString(R.string.action_sign_in_short), null);

        mImageViewShowPwd.setOnClickListener(ImgViewShowPasswordListener);
		mPasswordView.addTextChangedListener(PasswordTextChangedListener);

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mUsername = mPrefs.getString(SettingsActivity.EDT_USERNAME_STRING, "");
        mPassword = mPrefs.getString(SettingsActivity.EDT_PASSWORD_STRING, "");
        mOc_root_path = mPrefs.getString(SettingsActivity.EDT_OWNCLOUDROOTPATH_STRING, "");
        boolean useSSO = mPrefs.getBoolean(SettingsActivity.SW_USE_SINGLE_SIGN_ON, false);
        mCbDisableHostnameVerification = mPrefs.getBoolean(SettingsActivity.CB_DISABLE_HOSTNAME_VERIFICATION_STRING, false);

		if(!mPassword.isEmpty()) {
            mImageViewShowPwd.setVisibility(View.GONE);
        }

    	// Set up the login form.
 		mUsernameView.setText(mUsername);
 		mPasswordView.setText(mPassword);
 		mOc_root_path_View.setText(mOc_root_path);

 		mCbDisableHostnameVerificationView.setChecked(mCbDisableHostnameVerification);
 		mCbDisableHostnameVerificationView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
				mPrefs.edit()
					.putBoolean(SettingsActivity.CB_DISABLE_HOSTNAME_VERIFICATION_STRING, isChecked)
					.commit();
			}
		});

        if(useSSO) {
            mSwSingleSignOn.setChecked(true);
            syncUiElementState();
        }

        mSwSingleSignOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    if (!VersionCheckHelper.verifyMinVersion(getActivity(), MIN_NEXTCLOUD_FILES_APP_VERSION_CODE)) {
                        mSwSingleSignOn.setChecked(false);
                        return;

                    }
                }

                syncUiElementState();

                mUsernameView.setText("");
                mPasswordView.setText("");
                mOc_root_path_View.setText("");
                mCbDisableHostnameVerificationView.setChecked(false);

                mPasswordContainerView.setVisibility(View.VISIBLE);
                mImageViewShowPwd.setVisibility(View.VISIBLE);
                mCbDisableHostnameVerificationView.setVisibility(View.VISIBLE);

                if(isChecked) {
                    try {
                        AccountImporter.pickNewAccount(LoginDialogFragment.this);
                    } catch (NextcloudFilesAppNotInstalledException e) {
                        UiExceptionManager.showDialogForException(getActivity(), e);
                    }
                } else {
                    importedAccount = null;
                }
            }
        });



		AlertDialog dialog = builder.create();
		// Set dialog to resize when soft keyboard pops up
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);



		return dialog;
    }

    private void syncUiElementState() {
        boolean useSSO = mSwSingleSignOn.isChecked();
        mUsernameView.setEnabled(!useSSO);
        mPasswordView.setEnabled(!useSSO);
        mOc_root_path_View.setEnabled(!useSSO);
        mCbDisableHostnameVerificationView.setEnabled(!useSSO);
    }

	@Override
	public void onStart() {
		super.onStart();
		final AlertDialog dialog = (AlertDialog) getDialog();
		// Override the onClickListeners, as the default implementation would dismiss the dialog
		if (dialog != null) {
			Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
			positiveButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
				    if(mSwSingleSignOn.isChecked() && importedAccount == null) {
                        Toast.makeText(getContext(), "Please select account first by disabling and re-enabling sso", Toast.LENGTH_LONG).show();
                    } else {
                        attemptLogin();
                    }
				}
			});
		}
	}


	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		if(mActivity instanceof AuthenticatorActivity)
			mActivity.finish();
	}

	private TextWatcher PasswordTextChangedListener = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {

		}

		@Override
		public void afterTextChanged(Editable s) {
            if(s.toString().isEmpty()) {
                mImageViewShowPwd.setVisibility(View.VISIBLE);
            }
		}
	};

	private View.OnClickListener ImgViewShowPasswordListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mPasswordVisible = !mPasswordVisible;

            if(mPasswordVisible) {
                mPasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                mPasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        }
    };

	private ProgressDialog BuildPendingDialogWhileLoggingIn()
	{
		ProgressDialog pDialog = new ProgressDialog(getActivity());
        pDialog.setTitle(getString(R.string.login_progress_signing_in));
        return pDialog;
	}

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
        //if (mAuthTask != null) {
		//	return;
		//}

		// Reset errors.
		mUsernameView.setError(null);
		mPasswordView.setError(null);
		mOc_root_path_View.setError(null);

		// Store values at the time of the login attempt.
		mUsername = mUsernameView.getText().toString().trim();
		mPassword = mPasswordView.getText().toString();
		mOc_root_path = mOc_root_path_View.getText().toString().trim();

		boolean cancel = false;
		View focusView = null;

        // Only run checks if we don't use sso
        if(!mSwSingleSignOn.isChecked()) {
            // Check for a valid password.
            if (TextUtils.isEmpty(mPassword)) {
                mPasswordView.setError(getString(R.string.error_field_required));
                focusView = mPasswordView;
                cancel = true;
            }
            // Check for a valid email address.
            if (TextUtils.isEmpty(mUsername)) {
                mUsernameView.setError(getString(R.string.error_field_required));
                focusView = mUsernameView;
                cancel = true;
            }

            if (TextUtils.isEmpty(mOc_root_path)) {
                mOc_root_path_View.setError(getString(R.string.error_field_required));
                focusView = mOc_root_path_View;
                cancel = true;
            } else {
                try {
                    URL url = new URL(mOc_root_path);
                    if (!url.getProtocol().equals("https"))
                        ShowAlertDialog(getString(R.string.login_dialog_title_security_warning),
                                getString(R.string.login_dialog_text_security_warning), getActivity());
                } catch (MalformedURLException e) {
                    mOc_root_path_View.setError(getString(R.string.error_invalid_url));
                    focusView = mOc_root_path_View;
                    cancel = true;
                }
            }
        }

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
            Editor editor = mPrefs.edit();
            editor.putString(SettingsActivity.EDT_OWNCLOUDROOTPATH_STRING, mOc_root_path);
            editor.putString(SettingsActivity.EDT_PASSWORD_STRING, mPassword);
            editor.putString(SettingsActivity.EDT_USERNAME_STRING, mUsername);
            editor.putBoolean(SettingsActivity.SW_USE_SINGLE_SIGN_ON, importedAccount != null);
            editor.commit();

            final ProgressDialog dialogLogin = BuildPendingDialogWhileLoggingIn();
            dialogLogin.show();

			if(mSwSingleSignOn.isChecked()) {
			    SingleAccountHelper.setCurrentAccount(getActivity(), importedAccount.name);
			}


			mApi.initApi(new NextcloudAPI.ApiConnectedListener() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "onConnected() called");
                    finishLogin(dialogLogin);
                }

                @Override
                public void onError(Exception ex) {
                    dialogLogin.dismiss();
                    Log.d(TAG, "onError() called with: ex = [" + ex + "]");
                    ShowAlertDialog(getString(R.string.login_dialog_title_error), ex.getMessage(), getActivity());
                }
            });
		}
	}

	void finishLogin(final ProgressDialog dialogLogin) {
        mApi.getAPI().version()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<NextcloudNewsVersion>() {
                    boolean loginSuccessful = false;

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        Log.v(TAG, "onSubscribe() called with: d = [" + d + "]");
                    }

                    @Override
                    public void onNext(@NonNull NextcloudNewsVersion version) {
                        Log.v(TAG, "onNext() called with: status = [" + version.version + "]");

                        loginSuccessful = true;
                        mPrefs.edit().putString(Constants.NEWS_WEB_VERSION_NUMBER_STRING, version.version).apply();

                        if(version.version.equals("0")) {
                            ShowAlertDialog(getString(R.string.login_dialog_title_error), getString(R.string.login_dialog_text_zero_version_code), getActivity());
                            loginSuccessful = false;
                        }

                        importedAccount = null;
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        dialogLogin.dismiss();
                        Log.v(TAG, "onError() called with: e = [" + e + "]");

                        Throwable t = OkHttpSSLClient.HandleExceptions(e);

                        if(t instanceof NextcloudHttpRequestFailedException && ((NextcloudHttpRequestFailedException) t).getStatusCode() == 302) {
                            ShowAlertDialog(
                                    getString(R.string.login_dialog_title_error),
                                    getString(R.string.login_dialog_text_news_app_not_installed_on_server,
                                            "https://github.com/nextcloud/news/blob/master/docs/install.md#installing-from-the-app-store"),
                                    getActivity());
                        } else {
                            ShowAlertDialog(getString(R.string.login_dialog_title_error), t.getMessage(), getActivity());
                        }
                    }

                    @Override
                    public void onComplete() {
                        dialogLogin.dismiss();

                        Log.v(TAG, "onComplete() called");

                        if(loginSuccessful) {
                            //Reset Database
                            DatabaseConnectionOrm dbConn = new DatabaseConnectionOrm(getActivity());
                            dbConn.resetDatabase();

                            listener.loginSucceeded();
                            LoginDialogFragment.this.getDialog().cancel();
                            if(mActivity instanceof AuthenticatorActivity)
                                mActivity.finish();
                        }
                    }
                });
    }

	public static void ShowAlertDialog(String title, String text, Activity activity)
	{
		// Linkify the message
		final SpannableString s = new SpannableString(text);
		Linkify.addLinks(s, Linkify.ALL);

		AlertDialog aDialog = new AlertDialog.Builder(activity)
				.setTitle(title)
				.setMessage(s)
				.setPositiveButton(activity.getString(android.R.string.ok) , null)
				.create();
		aDialog.show();

		// Make the textview clickable. Must be called after show()
		((TextView)aDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
	}

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        AccountImporter.onActivityResult(requestCode, resultCode, data, LoginDialogFragment.this, new AccountImporter.IAccountAccessGranted() {
            @Override
            public void accountAccessGranted(SingleSignOnAccount account) {
                mUsernameView.setText(account.username);
                mPasswordView.setText("");
                mOc_root_path_View.setText(account.url);

                mPasswordContainerView.setVisibility(View.GONE);
                mImageViewShowPwd.setVisibility(View.GONE);
                mCbDisableHostnameVerificationView.setVisibility(View.GONE);

                LoginDialogFragment.this.importedAccount = account;

                attemptLogin();
            }
        });
    }
}
