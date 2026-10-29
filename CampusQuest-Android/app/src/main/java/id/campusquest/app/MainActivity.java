package id.campusquest.app;

import android.app.Activity;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private WebView webView;
    private NfcAdapter nfcAdapter;
    private volatile boolean scanMode = false;
    private volatile String scanPurpose = "nfc";
    private volatile String writePayload = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(23, 33, 63));
        window.setNavigationBarColor(Color.rgb(23, 33, 63));
        window.getDecorView().setSystemUiVisibility(0);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244, 246, 251));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportZoom(false);
        webView.addJavascriptInterface(new NfcBridge(), "AndroidNFC");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void enableReader() {
        if (nfcAdapter == null) {
            scanMode = false;
            writePayload = null;
            jsError("HP ini tidak memiliki NFC.");
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            scanMode = false;
            writePayload = null;
            jsError("Aktifkan NFC di pengaturan HP.");
            return;
        }
        Bundle options = new Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
        nfcAdapter.enableReaderMode(this, this, flags, options);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        String payload = writePayload;
        if (payload != null) {
            writePayload = null;
            scanMode = false;
            try {
                writeNdef(tag, payload);
                disableReader();
                js("nativeNfcWriteSuccess();");
            } catch (Exception ex) {
                disableReader();
                jsError("Gagal menulis NFC: " + safeMessage(ex));
            }
            return;
        }

        if (!scanMode) return;
        String mode = scanPurpose;
        scanMode = false;
        try {
            String value = readNdefText(tag);
            disableReader();
            js("nativeNfcRead(" + JSONObject.quote(value) + "," + JSONObject.quote(mode) + ");");
        } catch (Exception ex) {
            disableReader();
            jsError("Tag tidak terbaca: " + safeMessage(ex));
        }
    }

    private void writeNdef(Tag tag, String payload) throws Exception {
        NdefMessage message = new NdefMessage(new NdefRecord[]{NdefRecord.createTextRecord("en", payload)});
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                if (!ndef.isWritable()) throw new IOException("Tag bersifat read-only.");
                if (ndef.getMaxSize() < message.toByteArray().length) {
                    throw new IOException("Kapasitas tag tidak cukup untuk token CampusQuest.");
                }
                ndef.writeNdefMessage(message);
            } finally {
                try { ndef.close(); } catch (Exception ignored) { }
            }
            return;
        }

        NdefFormatable formatable = NdefFormatable.get(tag);
        if (formatable == null) {
            throw new IOException("Tag tidak mendukung NDEF. Gunakan gelang/tag NFC yang kompatibel.");
        }
        try {
            formatable.connect();
            formatable.format(message);
        } finally {
            try { formatable.close(); } catch (Exception ignored) { }
        }
    }

    private String readNdefText(Tag tag) throws Exception {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) throw new IOException("Tag tidak mendukung data NDEF.");
        NdefMessage message;
        try {
            ndef.connect();
            message = ndef.getNdefMessage();
        } finally {
            try { ndef.close(); } catch (Exception ignored) { }
        }
        if (message == null || message.getRecords().length == 0) throw new IOException("Tag kosong.");
        for (NdefRecord record : message.getRecords()) {
            if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
                return readText(record);
            }
        }
        throw new IOException("Tidak ditemukan record teks NDEF pada tag.");
    }

    private String readText(NdefRecord record) throws IOException {
        byte[] payload = record.getPayload();
        if (payload.length == 0) return "";
        int languageLength = payload[0] & 0x3F;
        if (payload.length < 1 + languageLength) throw new IOException("Data tag tidak valid.");
        Charset charset = ((payload[0] & 0x80) == 0) ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        return new String(payload, 1 + languageLength, payload.length - 1 - languageLength, charset);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty() ? "Periksa tag dan coba lagi." : message;
    }

    private void disableReader() {
        runOnUiThread(() -> {
            if (nfcAdapter != null) {
                try { nfcAdapter.disableReaderMode(MainActivity.this); } catch (Exception ignored) { }
            }
        });
    }

    private void js(String code) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(code, null);
        });
    }

    private void jsError(String message) {
        js("nativeNfcError(" + JSONObject.quote(message == null ? "Operasi NFC gagal." : message) + ");");
    }

    private class NfcBridge {
        @JavascriptInterface
        public boolean hasNfc() { return nfcAdapter != null; }

        @JavascriptInterface
        public boolean isEnabled() { return nfcAdapter != null && nfcAdapter.isEnabled(); }

        @JavascriptInterface
        public void startScan(String mode) {
            runOnUiThread(() -> {
                writePayload = null;
                scanMode = true;
                scanPurpose = (mode == null || mode.trim().isEmpty()) ? "nfc" : mode;
                enableReader();
            });
        }

        @JavascriptInterface
        public void stopScan() {
            runOnUiThread(() -> {
                scanMode = false;
                writePayload = null;
                disableReader();
            });
        }

        @JavascriptInterface
        public void writeTag(String payload) {
            runOnUiThread(() -> {
                if (nfcAdapter == null) {
                    jsError("HP ini tidak memiliki NFC.");
                    return;
                }
                if (!nfcAdapter.isEnabled()) {
                    jsError("Aktifkan NFC di pengaturan HP.");
                    return;
                }
                writePayload = payload;
                scanMode = false;
                js("toast('Siap menulis. Tempelkan gelang/tag NFC yang mendukung NDEF ke belakang HP.');");
                enableReader();
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanMode = false;
        writePayload = null;
        if (nfcAdapter != null) {
            try { nfcAdapter.disableReaderMode(this); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidNFC");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
