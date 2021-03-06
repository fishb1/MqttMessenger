package fb.ru.mqtttest.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import fb.ru.mqtttest.App;
import fb.ru.mqtttest.common.UserSession;

/**
 * Диспетчер, запускается из ланчера.
 */
public class LauncherActivity extends Activity {

    UserSession mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Implement DI here
        mSession = ((App) getApplication()).getUserSession();
        // ----
        if (!mSession.isStarted()) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(new Intent(this, HomeActivity.class)
                    .setAction(getIntent().getAction()));
        }
        finish();
    }
}
