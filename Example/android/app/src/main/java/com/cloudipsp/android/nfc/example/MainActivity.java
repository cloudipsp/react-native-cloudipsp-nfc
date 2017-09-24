package com.cloudipsp.android.nfc.example;

import android.content.Intent;
import android.os.Bundle;

import com.cloudipsp.android.nfc.CloudipspNfcIntentProcessor;
import com.facebook.react.ReactActivity;

public class MainActivity extends ReactActivity {
    @Override
    protected String getMainComponentName() {
        return "cloudipsp";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            CloudipspNfcIntentProcessor.process(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        CloudipspNfcIntentProcessor.process(intent);
    }
}
