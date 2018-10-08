package fb.ru.mqtttest.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;

import javax.net.ssl.HttpsURLConnection;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.R;
import fb.ru.mqtttest.common.Settings;
import fb.ru.mqtttest.common.UserSession;
import fb.ru.mqtttest.rest.ApiService;
import fb.ru.mqtttest.rest.DeviceConfig;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * A login screen that offers login via login/password.
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mAddressView;
    private EditText mLoginView;

    private View mProgressView;
    private View mLoginFormView;
    // Model references
    private UserSession mSession;
    private Settings mSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: Implement DI here
        mSession = ((App) getApplication()).getUserSession();
        mSettings = ((App) getApplication()).getSettings();
        // ----
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mAddressView = findViewById(R.id.server);
        String url = Settings.DEFAULT_REST_API_URL;
        mAddressView.setText(TextUtils.isEmpty(url) ? Settings.DEFAULT_REST_API_URL : url); // Если затрется при повороте экрана, то ничего страшного...
        mAddressView.setEnabled(false);
        mLoginView = findViewById(R.id.login);
        mLoginView.requestFocus();
        findViewById(R.id.sign_in_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });
        findViewById(R.id.scan_qr).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = new IntentIntegrator(LoginActivity.this);
                integrator.setOrientationLocked(false);
                integrator.setBeepEnabled(true);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
                integrator.setBarcodeImageEnabled(true);
                integrator.setPrompt("");
                integrator.initiateScan();
            }
        });
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            mLoginView.setText(result.getContents());
            attemptLogin();
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid login, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Store values at the time of the login attempt.
        String address = mAddressView.getText().toString();
        String login = mLoginView.getText().toString();

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);
        mAuthTask = new UserLoginTask(address, login);
        mAuthTask.execute();
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });
        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mAddress;
        private final String mPinCode;

        UserLoginTask(String server, String pin) {
            mAddress = server;
            mPinCode = pin;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            ApiService apiService = new Retrofit.Builder()
                    .baseUrl(mAddress)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
            Call<DeviceConfig> loginCall = apiService.activatePin(mPinCode);
            try {
                Response<DeviceConfig> response = loginCall.execute();
                if (response.code() == HttpsURLConnection.HTTP_OK) {
                    if (!mSession.isStarted()) {
                        mSession.start(response.body());
                        return true;
                    }
                }
            } catch (IOException e) {
                Log.e("LoginActivity", "Error!", e);
            }
            return false;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);
            if (success) {
                mSettings.setRestApiUrl(mAddress);
                startActivity(new Intent(LoginActivity.this, LauncherActivity.class));
                finish();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

