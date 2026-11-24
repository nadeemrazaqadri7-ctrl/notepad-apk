package com.offline.notepad;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQ = 1001;
    private static final int SAVE_BACKUP_REQ = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingBackupContent;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(0xFF121216);
        window.setNavigationBarColor(0xFF121216);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(intent, FILE_CHOOSER_REQ);
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    try {
                        startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQ);
                    } catch (Exception ex) {
                        filePathCallback = null;
                        return false;
                    }
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveBackupFile(final String fileName, final String content) {
            runOnUiThread(() -> {
                pendingBackupContent = content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(intent, SAVE_BACKUP_REQ);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open file saver", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQ) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else if (requestCode == SAVE_BACKUP_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupContent != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os != null) {
                        os.write(pendingBackupContent.getBytes(StandardCharsets.UTF_8));
                        Toast.makeText(this, "Backup saved!", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to save backup", Toast.LENGTH_SHORT).show();
                }
            }
            pendingBackupContent = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript(
            "(function(){var v=document.getElementById('viewModal'),e=document.getElementById('editModal');if(e&&e.classList.contains('open')){e.classList.remove('open');return '1';}if(v&&v.classList.contains('open')){v.classList.remove('open');return '1';}return '0';})()",
            val -> {
                if (!"\"1\"".equals(val)) {
                    finish();
                }
            }
        );
    }
}
