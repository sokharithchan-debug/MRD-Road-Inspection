package kh.gov.mrd.roadinspection;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * Wraps the road inspection web app in a native shell.
 *
 * The page is served from the APK's assets over https://appassets.androidplatform.net/,
 * which WebView treats as a secure origin. That is what lets the page use GPS and keep
 * its survey data in localStorage, while every byte still comes from inside the app —
 * nothing is fetched from the network.
 */
public class MainActivity extends Activity {

    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + APP_HOST + "/index.html";

    private static final int REQ_LOCATION = 101;
    private static final int REQ_FILE = 102;

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;

    private long lastBackPress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Surveys run with the phone on the dashboard — never let the screen sleep.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(APP_HOST)
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // The survey screen is laid out to fill the phone exactly, so the system
        // font-size setting must not scale the text and push buttons off-screen.
        s.setTextZoom(100);

        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.addJavascriptInterface(new ExportBridge(), "RoadInspectionExport");

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    /* ---------- Serving the app from assets ---------- */

    private class AppWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (APP_HOST.equals(url.getHost())) {
                return false;
            }
            // Anything outside the app opens in the phone's browser, not in here.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, url));
            } catch (Exception ignored) {
                // no browser installed — just stay put
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(DOWNLOAD_HOOK, null);
        }
    }

    /* ---------- GPS, camera and the file picker ---------- */

    private class AppWebChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false);
                return;
            }
            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            cameraOutputUri = null;

            Intent camera = buildCameraIntent();
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");

            Intent launch;
            if (camera != null && params.isCaptureEnabled()) {
                // <input capture="environment"> — the inspector wants the camera, not a gallery.
                launch = camera;
            } else if (camera != null) {
                launch = Intent.createChooser(pick, getString(R.string.choose_photo));
                launch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            } else {
                launch = pick;
            }

            try {
                startActivityForResult(launch, REQ_FILE);
            } catch (ActivityNotFoundException e) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
                toast(getString(R.string.no_camera));
                return false;
            }
            return true;
        }
    }

    private Intent buildCameraIntent() {
        Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (capture.resolveActivity(getPackageManager()) == null) {
            return null;
        }
        try {
            File dir = new File(getCacheDir(), "captures");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File photo = new File(dir, "IMG_" + System.currentTimeMillis() + ".jpg");
            cameraOutputUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return capture;
        } catch (Exception e) {
            cameraOutputUri = null;
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) {
            return;
        }
        if (filePathCallback == null) {
            return;
        }
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            Uri picked = (data != null) ? data.getData() : null;
            if (picked == null) {
                picked = cameraOutputUri;      // the camera wrote straight into our file
            }
            if (picked != null) {
                result = new Uri[]{picked};
            }
        }
        // Passing null back matters: without it the file input stays stuck forever.
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
        cameraOutputUri = null;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION || pendingGeoCallback == null) {
            return;
        }
        boolean granted = false;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
        if (!granted) {
            toast(getString(R.string.need_location));
        }
    }

    /* ---------- Saving exports ---------- */

    /**
     * The web app builds its CSV, PDF, KML, Word and ZIP files in the browser and hands them
     * to a blob: link. WebView has no download manager for those, so this script catches the
     * click and streams the bytes over to {@link ExportBridge} in base64 chunks.
     */
    private static final String DOWNLOAD_HOOK =
            "(function(){"
          + "if(window.__riDownloadHook)return;window.__riDownloadHook=true;"
          + "var B=window.RoadInspectionExport,SZ=262144;"
          + "function send(name,dataUrl){"
          + "  var b64=dataUrl.substring(dataUrl.indexOf(',')+1);"
          + "  if(!b64.length){B.chunk(name,'',true);return;}"
          + "  for(var i=0;i<b64.length;i+=SZ){B.chunk(name,b64.substr(i,SZ),i+SZ>=b64.length);}"
          + "}"
          + "function grab(href,name){"
          + "  fetch(href).then(function(r){return r.blob();}).then(function(b){"
          + "    var fr=new FileReader();"
          + "    fr.onloadend=function(){send(name,String(fr.result));};"
          + "    fr.onerror=function(){B.failed(name);};"
          + "    fr.readAsDataURL(b);"
          + "  }).catch(function(){B.failed(name);});"
          + "}"
          + "function handle(a){"
          + "  if(!a)return false;"
          + "  var href=a.getAttribute('href')||a.href||'';"
          + "  var name=a.getAttribute('download');"
          + "  if(!name||href.indexOf('blob:')!==0)return false;"
          + "  grab(href,name);return true;"
          + "}"
          + "var click=HTMLAnchorElement.prototype.click;"
          + "HTMLAnchorElement.prototype.click=function(){"
          + "  if(handle(this))return;"
          + "  return click.apply(this,arguments);"
          + "};"
          // jsPDF fires a synthetic MouseEvent on a link that is never added to the page,
          // so a listener on document would never see it.
          + "var dispatch=HTMLAnchorElement.prototype.dispatchEvent;"
          + "HTMLAnchorElement.prototype.dispatchEvent=function(ev){"
          + "  if(ev&&ev.type==='click'&&handle(this))return true;"
          + "  return dispatch.apply(this,arguments);"
          + "};"
          + "document.addEventListener('click',function(ev){"
          + "  var t=ev.target,a=(t&&t.closest)?t.closest('a[download]'):null;"
          + "  if(a&&handle(a)){ev.preventDefault();ev.stopPropagation();}"
          + "},true);"
          + "})();";

    private class ExportBridge {
        private final HashMap<String, FileOutputStream> open = new HashMap<>();
        private final HashMap<String, File> temps = new HashMap<>();

        /** Called from the page, on WebView's JavaScript bridge thread. */
        @JavascriptInterface
        public void chunk(String name, String base64, boolean last) {
            String safe = safeName(name);
            try {
                FileOutputStream out = open.get(safe);
                if (out == null) {
                    File dir = new File(getCacheDir(), "exports");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File tmp = new File(dir, System.currentTimeMillis() + "_" + safe);
                    out = new FileOutputStream(tmp);
                    open.put(safe, out);
                    temps.put(safe, tmp);
                }
                if (base64 != null && base64.length() > 0) {
                    // Every chunk is a whole number of base64 groups, so it decodes on its own.
                    out.write(Base64.decode(base64, Base64.NO_WRAP));
                }
                if (last) {
                    out.close();
                    final File tmp = temps.remove(safe);
                    open.remove(safe);
                    final String fileName = safe;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            publish(fileName, tmp);
                        }
                    });
                }
            } catch (Exception e) {
                cleanUp(safe);
                failed(name);
            }
        }

        @JavascriptInterface
        public void failed(String name) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toast(getString(R.string.export_failed));
                }
            });
        }

        private void cleanUp(String safe) {
            try {
                FileOutputStream out = open.remove(safe);
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
            File tmp = temps.remove(safe);
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /** Copies a finished export where the phone's file manager and other apps can reach it. */
    private void publish(String name, File tmp) {
        if (tmp == null || !tmp.exists()) {
            toast(getString(R.string.export_failed));
            return;
        }
        Uri shared = null;
        String mime = mimeOf(name);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/RoadInspection");
                shared = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (shared != null) {
                    OutputStream out = getContentResolver().openOutputStream(shared);
                    copy(tmp, out);
                }
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File dest = new File(dir, name);
                OutputStream out = new FileOutputStream(dest);
                copy(tmp, out);
                shared = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", dest);
            }
        } catch (Exception e) {
            shared = null;
        } finally {
            tmp.delete();
        }

        if (shared == null) {
            toast(getString(R.string.export_failed));
            return;
        }

        toast(getString(R.string.export_saved, name));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, shared);
        send.putExtra(Intent.EXTRA_SUBJECT, name);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_export)));
        } catch (Exception ignored) {
            // No app to share with — the file is saved either way.
        }
    }

    private static void copy(File from, OutputStream out) throws Exception {
        if (out == null) {
            throw new IllegalStateException("no output stream");
        }
        InputStream in = new FileInputStream(from);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Keeps the Khmer file names the app generates, minus anything a file system would reject. */
    private static String safeName(String name) {
        if (name == null || name.trim().length() == 0) {
            return "export.bin";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(cleaned.length() - 120);
        }
        return cleaned;
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".kml")) return "application/vnd.google-earth.kml+xml";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /* ---------- Lifecycle ---------- */

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // A stray back tap in the middle of a survey should not close the app.
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2500) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            toast(getString(R.string.press_again));
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
