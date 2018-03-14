package fb.ru.mqtttest.ui;

import android.app.Activity;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import fb.ru.mqtttest.R;

/**
 * Форма ввода сообщения для отправики.
 */
public class MessageActivity extends AppCompatActivity {

    public static String EXTRA_TOPIC = "EXTRA_TOPIC";
    public static String EXTRA_MESSAGE = "EXTRA_MESSAGE";

    EditText mTopic;
    EditText mMessage;
    View mContentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        mContentView = findViewById(R.id.content);
//        mTopic = findViewById(R.id.text_topic);
        mMessage = findViewById(R.id.text_message);
        findViewById(R.id.fab_publish_message).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessage.getText().toString().trim();
                if (!TextUtils.isEmpty(message)) {
                    Intent intent = new Intent();
                    //                intent.putExtra(EXTRA_TOPIC, mTopic.getText().toString());
                    intent.putExtra(EXTRA_MESSAGE, mMessage.getText().toString());
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                } else {
                    Snackbar.make(mContentView, "Empty message!", Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { // Если в меню нажали стрелочку, то вернуться назад
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }
}
