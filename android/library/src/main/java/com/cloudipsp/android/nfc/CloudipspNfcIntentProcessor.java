package com.cloudipsp.android.nfc;

import android.content.Intent;

/**
 * Created by vberegovoy on 24.09.17.
 */

public class CloudipspNfcIntentProcessor {
    public static boolean process(Intent intent) {
        if (CloudipspNfcReactModule.instance == null) {
            return false;
        }
        return CloudipspNfcReactModule.instance.readCard(intent);
    }
}
