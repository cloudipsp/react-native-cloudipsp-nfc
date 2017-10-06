package com.cloudipsp.android.nfc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.PromiseImpl;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableNativeMap;
import com.github.devnied.emvnfccard.exception.CommunicationException;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvParser;
import com.github.devnied.emvnfccard.parser.IProvider;
import com.google.android.gms.iid.InstanceID;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.util.List;

/**
 * Created by vberegovoy on 06.09.17.
 */

public class CloudipspNfcReactModule extends ReactContextBaseJavaModule {
    private static final String ACTION = NfcAdapter.ACTION_TECH_DISCOVERED;
    private static final String ERR_TAG = "CloudipspNFC: ";

    private final NfcAdapter adapter;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private Promise subscribbed;
    private CardInfo cardInfo;
    static CloudipspNfcReactModule instance;
    private String kkhCached;

    public CloudipspNfcReactModule(ReactApplicationContext context) {
        super(context);

        checkPermission(context);
        checkMetaData(context, getActivities(context));

        adapter = NfcAdapter.getDefaultAdapter(context);
        instance = this;
    }

    @Override
    public String getName() {
        return "CloudipspNfc";
    }

    @ReactMethod
    public void isSupporting(Promise promise) {
        promise.resolve(adapter != null);
    }

    @ReactMethod
    public void isEnabled(Promise promise) {
        if (adapter == null) {
            promise.resolve(false);
        } else {
            promise.resolve(adapter.isEnabled());
        }
    }

    @ReactMethod
    public void enable(Promise promise) {
        final Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            intent = new Intent(Settings.ACTION_NFC_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getReactApplicationContext().startActivity(intent);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void subscribe(Promise promise) {
        subscribbed = promise;
        notifySubscription();
    }

    private void notifySubscription() {
        if (subscribbed != null && cardInfo != null) {
            final WritableNativeMap map = new WritableNativeMap();
            map.putString("cardNumber", cardInfo.cardNumber);
            map.putInt("expMm", cardInfo.expMm);
            map.putInt("expYy", cardInfo.expYy);
            subscribbed.resolve(map);

            cardInfo = null;
            subscribbed = null;
        }
    }

    @ReactMethod
    public void kkh(final Promise promise) {
        if (kkhCached == null) {
            final Context context = getReactApplicationContext();

            new Thread() {
                @Override
                public void run() {
                    try {
                        final JSONObject kkhJson = new JSONObject();
                        kkhJson.put("id", InstanceID.getInstance(context).getId().hashCode());

                        final JSONObject data = new JSONObject();
                        data.put("board", Build.BOARD);
                        data.put("bootloader", Build.BOOTLOADER);
                        data.put("brand", Build.BRAND);
                        data.put("device", Build.DEVICE);
                        data.put("display", Build.DISPLAY);
                        data.put("fingerprint", Build.FINGERPRINT);
                        data.put("hardware", Build.HARDWARE);
                        data.put("host", Build.HOST);
                        data.put("id", Build.ID);
                        data.put("manufacturer", Build.MANUFACTURER);
                        data.put("model", Build.MODEL);
                        data.put("product", Build.PRODUCT);
                        data.put("os_version", Build.VERSION.CODENAME);
                        data.put("os_release", Build.VERSION.RELEASE);

                        final String appPackage = context.getPackageName();
                        data.put("app_package", appPackage);
                        final PackageInfo info;
                        info = context.getPackageManager().getPackageInfo(appPackage, 0);

                        data.put("app_version_code", info.versionCode);
                        data.put("app_version_name", info.versionName);
                        data.put("app_name", info.applicationInfo.name);

                        kkhJson.put("data", data);
                        kkhCached = Base64.encodeToString(kkhJson.toString().getBytes(), Base64.DEFAULT);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                promise.resolve(kkhCached);
                            }
                        });
                    } catch (final Exception e) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                promise.reject(e);
                            }
                        });
                    }
                }
            }.start();
        } else {
            promise.resolve(kkhCached);
        }
    }

    private static void checkPermission(Context context) {
        if (context.checkCallingOrSelfPermission("android.permission.NFC") != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException(ERR_TAG + "Application should have NFC permission");
        }
    }

    private static String[] getActivities(Context context) {
        final Intent queryIntent = new Intent(ACTION);
        queryIntent.setPackage(context.getPackageName());

        final List<ResolveInfo> infos = context.getPackageManager()
                .queryIntentActivities(queryIntent, PackageManager.GET_RESOLVED_FILTER);
        if (infos.isEmpty()) {
            throw new RuntimeException(ERR_TAG + "At least one activity should listen for action \"" + ACTION + "\"");
        }
        final String[] activities = new String[infos.size()];
        for (int i = 0; i < activities.length; ++i) {
            activities[i] = infos.get(i).activityInfo.name;
        }
        return activities;
    }

    private static void checkMetaData(Context context, String[] activities) {
        for (String activity : activities) {
            final ActivityInfo info;
            try {
                info = context.getPackageManager().getActivityInfo(
                        new ComponentName(context, activity), PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (info.metaData != null) {
                final int resource = info.metaData.getInt(ACTION);
                final XmlResourceParser parser = context.getResources().getXml(resource);

                try {
                    int eventType = parser.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            final String name = parser.getName();
                            if ("tech".equals(name)) {
                                eventType = parser.nextToken();
                                if (eventType == XmlPullParser.TEXT) {
                                    final String text = parser.getText();
                                    if ("android.nfc.tech.IsoDep".equals(text)) {
                                        return;
                                    }
                                }
                            }
                        }
                        eventType = parser.nextToken();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    parser.close();
                }
            }
        }

        final String resource = "<resources>\n" +
                "    <tech-list>\n" +
                "        <tech>android.nfc.tech.IsoDep</tech>\n" +
                "    </tech-list>\n" +
                "</resources>";
        throw new RuntimeException(ERR_TAG + "MetaInfo with \n\"" + resource + "\" must be set for " + activities[0]);
    }

    boolean readCard(Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                final IsoDep isoDep = IsoDep.get(tag);
                final IProvider prov = new IProvider() {
                    @Override
                    public byte[] transceive(byte[] command) throws CommunicationException {
                        try {
                            if (!isoDep.isConnected()) {
                                isoDep.connect();
                            }
                            return isoDep.transceive(command);
                        } catch (IOException e) {
                            throw new CommunicationException(e.getMessage());
                        }
                    }
                };
                final EmvParser parser = new EmvParser(prov, true);
                final EmvCard emvCard;
                try {
                    emvCard = parser.readEmvCard();
                } catch (CommunicationException e) {
                    return false;
                }

                try {
                    cardInfo = new CardInfo(
                            emvCard.getCardNumber(),
                            emvCard.getExpireDate().getMonth() + 1,
                            emvCard.getExpireDate().getYear() - 100
                    );
                    notifySubscription();
                    return true;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return false;
    }

    private static class CardInfo {
        public final String cardNumber;
        public final int expMm, expYy;

        private CardInfo(String cardNumber, int expMm, int expYy) {
            this.cardNumber = cardNumber;
            this.expMm = expMm;
            this.expYy = expYy;
        }
    }
}
