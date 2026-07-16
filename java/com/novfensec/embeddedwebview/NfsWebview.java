package com.novfensec.embeddedwebview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.http.SslError;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import android.text.InputType;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import android.graphics.Bitmap;

public class NfsWebview {
    private static final String TAG = "NfsWebview";

    private Activity mainActivity;
    private WebView webView;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private int width;
    private int height;
    private int densityDpi;
    private boolean isUpdated = false;
    private File lastIconFile = null;

    private VirtualDisplay virtualDisplay;
    private Presentation presentation;
    private KeyboardProxyView keyboardProxy;
    private ValueCallback<Uri[]> filePathCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    private int originalOrientation;
    private int originalSystemUiVisibility;

    public NfsWebview(final Context context, final int width, final int height) {
        this.width = width;
        this.height = height;
        this.mainActivity = (Activity) context;

        surfaceTexture = new SurfaceTexture(0);
        surfaceTexture.detachFromGLContext();
        surfaceTexture.setDefaultBufferSize(width, height);
        surface = new Surface(surfaceTexture);

        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture st) {
                synchronized (NfsWebview.this) {
                    isUpdated = true;
                }
                if (frameReadyListener != null) {
                    frameReadyListener.onFrameReady();
                }
            }
        });

        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        this.densityDpi = context.getResources().getDisplayMetrics().densityDpi;

        virtualDisplay = displayManager.createVirtualDisplay("NfsWebViewDisplay",
                width, height, this.densityDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        Activity activity = (Activity) context;
        presentation = new Presentation(activity, virtualDisplay.getDisplay()) {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                webView = new WebView(getContext());
                configureWebView();
                webView.setBackgroundColor(0xFFFFFFFF);
                setContentView(webView, new FrameLayout.LayoutParams(width, height));
            }
        };
        presentation.show();

        keyboardProxy = new KeyboardProxyView(context);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.addContentView(keyboardProxy, new ViewGroup.LayoutParams(1, 1));
            }
        });
    }

    public void attachGL(int textureId) {
        try {
            surfaceTexture.attachToGLContext(textureId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to attach GL: " + e.getMessage());
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new NfsWebViewClient());
        webView.setWebChromeClient(new NfsWebChromeClient());
        webView.setDownloadListener(new NfsDownloadListener());
        webView.addJavascriptInterface(new NfsJSBridge(), "NfsBridge");

        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult result = webView.getHitTestResult();

                if (result != null && result.getExtra() != null) {
                    int type = result.getType();
                    String extra = result.getExtra();

                    if (type == WebView.HitTestResult.IMAGE_TYPE ||
                            type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                        if (contextMenuListener != null) {
                            contextMenuListener.onContextMenuRequested(type, extra);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

    }

    public void loadUrl(final String url) {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.loadUrl(url);
                }
            });
        }
    }

    public String getCurrentUrl() {
        return webView != null ? webView.getUrl() : "";
    }

    public void reload() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.reload();
                }
            });
        }
    }

    public boolean canGoBack() {
        if (webView == null)
            return false;

        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(
                new java.util.concurrent.Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return webView.canGoBack();
                    }
                });

        webView.post(task);

        try {
            return task.get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to check canGoBack: " + e.getMessage());
            return false;
        }
    }

    public void goBack() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    }
                }
            });
        }
    }

    public boolean canGoForward() {
        if (webView == null)
            return false;

        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(
                new java.util.concurrent.Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return webView.canGoForward();
                    }
                });

        webView.post(task);

        try {
            return task.get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to check canGoForward: " + e.getMessage());
            return false;
        }
    }

    public void goForward() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    if (webView.canGoForward()) {
                        webView.goForward();
                    }
                }
            });
        }
    }

    private String escapeJSString(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r",
                "");
    }

    public void evaluateJavascript(final String script) {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(script, null);
                }
            });
        }
    }

    private void replaceTextAtomic(String text, int backspaces) {
        if (backspaces == 0 && (text == null || text.isEmpty()))
            return;

        String safeText = escapeJSString(text);
        StringBuilder js = new StringBuilder("javascript:(function(){");

        for (int i = 0; i < backspaces; i++) {
            js.append("document.execCommand('delete', false, null);");
        }

        if (text != null && !text.isEmpty()) {
            js.append("document.execCommand('insertText', false, '").append(safeText).append("');");
        }

        js.append("})();");
        evaluateJavascript(js.toString());
    }

    public boolean updateTexImage() {
        boolean updated = false;
        synchronized (this) {
            if (isUpdated) {
                surfaceTexture.updateTexImage();
                isUpdated = false;
                updated = true;
            }
        }
        return updated;
    }

    public void injectTouchEvent(int action, float x, float y) {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    if (action == MotionEvent.ACTION_DOWN) {
                        webView.requestFocus();
                    }
                    long time = SystemClock.uptimeMillis();
                    MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
                    webView.dispatchTouchEvent(event);
                    event.recycle();
                }
            });
        }
    }

    public void scrollBy(int x, int y) {
        if (webView != null)
            webView.scrollBy(x, y);
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        surfaceTexture.setDefaultBufferSize(width, height);
        if (virtualDisplay != null)
            virtualDisplay.resize(width, height, this.densityDpi);
        if (webView != null)
            webView.setLayoutParams(new FrameLayout.LayoutParams(width, height));
    }

    public void destroy() {
        if (presentation != null)
            presentation.dismiss();
        if (virtualDisplay != null)
            virtualDisplay.release();
        if (surface != null)
            surface.release();
        if (surfaceTexture != null)
            surfaceTexture.release();
        if (webView != null) {
            webView.clearHistory();
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
        }
    }

    public void pause() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.onPause();
                    webView.pauseTimers();
                }
            });
        }
    }

    public void resume() {
        if (webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.onResume();
                    webView.resumeTimers();
                }
            });
        }
    }

    public boolean isFullScreen() {
        return customView != null;
    }

    public void exitFullScreen() {
        if (customView != null && webView != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    WebChromeClient client = webView.getWebChromeClient();
                    if (client != null) {
                        client.onHideCustomView();
                    }
                }
            });
        }
    }

    private void dispatchBypassEvent(KeyEvent event) {
        if (webView != null) {
            int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
            KeyEvent bypassEvent = new KeyEvent(
                    event.getDownTime(), event.getEventTime(), event.getAction(), event.getKeyCode(),
                    event.getRepeatCount(), event.getMetaState(), KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags);
            webView.dispatchKeyEvent(bypassEvent);
        }
    }

    public void injectKey(int androidKeyCode, int action) {
        if (webView != null) {
            long time = SystemClock.uptimeMillis();
            int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
            int motionAction = action == 0 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            webView.dispatchKeyEvent(new KeyEvent(time, time, motionAction, androidKeyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        }
    }

    public void hideKeyboardNative() {
        Activity activity = (Activity) keyboardProxy.getContext();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(keyboardProxy.getWindowToken(), 0);
            }
        });
    }

    private class KeyboardProxyView extends View {
        public KeyboardProxyView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
            outAttrs.imeOptions = EditorInfo.IME_ACTION_SEARCH;

            return new BaseInputConnection(this, false) {
                private int composingLength = 0;

                @Override
                public boolean finishComposingText() {
                    composingLength = 0;
                    return super.finishComposingText();
                }

                @Override
                public boolean setComposingRegion(int start, int end) {
                    composingLength = Math.abs(end - start);
                    return super.setComposingRegion(start, end);
                }

                @Override
                public boolean performEditorAction(int editorAction) {
                    if (editorAction == EditorInfo.IME_ACTION_SEARCH || editorAction == EditorInfo.IME_ACTION_DONE ||
                            editorAction == EditorInfo.IME_ACTION_GO || editorAction == EditorInfo.IME_ACTION_SEND ||
                            editorAction == EditorInfo.IME_ACTION_UNSPECIFIED
                            || editorAction == EditorInfo.IME_ACTION_NEXT) {

                        injectKey(KeyEvent.KEYCODE_ENTER, 0);
                        injectKey(KeyEvent.KEYCODE_ENTER, 1);
                        hideKeyboardNative();
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean sendKeyEvent(KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                        if (composingLength > 0)
                            composingLength--;
                    }
                    dispatchBypassEvent(event);
                    return true;
                }

                @Override
                public boolean setComposingText(CharSequence text, int newCursorPosition) {
                    String newText = text.toString();
                    replaceTextAtomic(newText, composingLength);
                    composingLength = newText.length();
                    return true;
                }

                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    String newText = text.toString();
                    replaceTextAtomic(newText, composingLength);
                    composingLength = 0;
                    return true;
                }

                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    if (beforeLength > 0 && afterLength == 0) {
                        replaceTextAtomic("", beforeLength);
                        return true;
                    }
                    return super.deleteSurroundingText(beforeLength, afterLength);
                }
            };
        }
    }

    private class NfsWebViewClient extends WebViewClient {
        private boolean handleCustomUrl(String url) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false;
            }

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                mainActivity.startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "App not installed to handle URL: " + url);
                return true;
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (handleCustomUrl(url)) {
                return true;
            }
            return super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (handleCustomUrl(url)) {
                return true;
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(
                    "document.addEventListener('focusin', function(e) { " +
                            "  if(e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) { "
                            +
                            "    NfsBridge.requestKeyboard(); " +
                            "  } " +
                            "}); " +
                            "document.addEventListener('click', function(e) { " +
                            "  if(e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) { "
                            +
                            "    NfsBridge.requestKeyboard(); " +
                            "  } " +
                            "}); " +
                            "document.addEventListener('focusout', function(e) { " +
                            "  if(e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) { "
                            +
                            "    NfsBridge.hideKeyboard(); " +
                            "  } " +
                            "});",
                    null);
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (pageInfoListener != null) {
                pageInfoListener.onPageUrlChanged(url);
            }

            String domain = getDomainName(url);
            File cachedIcon = new File(mainActivity.getCacheDir(), "fav_" + domain + ".png");

            if (cachedIcon.exists()) {
                cachedIcon.setLastModified(System.currentTimeMillis());

                if (pageInfoListener != null) {
                    pageInfoListener.onPageIconChanged(cachedIcon.getAbsolutePath());
                }
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            if (pageInfoListener != null) {
                pageInfoListener.onPageUrlChanged(url);
            }
        }

    }

    private class NfsWebChromeClient extends WebChromeClient {

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                android.os.Message resultMsg) {
            WebView dummyWebView = new WebView(mainActivity);

            dummyWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String newUrl = request.getUrl().toString();

                    if (newTabListener != null) {
                        newTabListener.onNewTabRequested(newUrl);
                    }

                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (newTabListener != null) {
                        newTabListener.onNewTabRequested(url);
                    }
                    return true;
                }
            });

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(dummyWebView);
            resultMsg.sendToTarget();

            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request.grant(request.getResources());
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }

            Window window = mainActivity.getWindow();
            FrameLayout decorView = (FrameLayout) window.getDecorView();

            originalOrientation = mainActivity.getRequestedOrientation();
            originalSystemUiVisibility = decorView.getSystemUiVisibility();

            fullscreenContainer = new FrameLayout(mainActivity);
            fullscreenContainer.setBackgroundColor(0xFF000000);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                fullscreenContainer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            return android.view.WindowInsets.CONSUMED;
                        } else {
                            return insets.consumeSystemWindowInsets();
                        }
                    }
                });
            }

            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            decorView.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            customView = view;
            customViewCallback = callback;

            mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            if (fullScreenListener != null) {
                fullScreenListener.onFullScreenEnter();
            }
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) {
                return;
            }

            FrameLayout decorView = (FrameLayout) mainActivity.getWindow().getDecorView();

            decorView.removeView(fullscreenContainer);
            fullscreenContainer = null;
            customView = null;
            customViewCallback.onCustomViewHidden();

            mainActivity.setRequestedOrientation(originalOrientation);
            decorView.setSystemUiVisibility(originalSystemUiVisibility);

            if (fullScreenListener != null) {
                fullScreenListener.onFullScreenExit();
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (progressListener != null) {
                progressListener.onProgress(newProgress);
            }
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);

            if (icon != null && pageInfoListener != null) {
                try {
                    String domain = getDomainName(view.getUrl());
                    File iconFile = new File(mainActivity.getCacheDir(), "fav_" + domain + ".png");

                    java.io.FileOutputStream out = new java.io.FileOutputStream(iconFile);
                    icon.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    out.close();

                    pageInfoListener.onPageIconChanged(iconFile.getAbsolutePath());

                    performCacheCleanup();

                } catch (Exception e) {
                    Log.e(TAG, "Failed to save favicon: " + e.getMessage());
                }
            }
        }
    }

    public void executeDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        if (url.startsWith("data:")) {
            handleDataUriDownload(mainActivity, url, mimetype);
            return;
        }

        if (url.startsWith("blob:")) {
            String js = "javascript:(function() {" +
                    "fetch('" + url + "')" +
                    ".then(res => res.blob())" +
                    ".then(blob => {" +
                    "    var reader = new FileReader();" +
                    "    reader.readAsDataURL(blob);" +
                    "    reader.onloadend = function() {" +
                    "        NfsBridge.processBlobDownload(reader.result, '" + mimetype + "');" +
                    "    }" +
                    "});" +
                    "})();";
            evaluateJavascript(js);
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setTitle(fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) mainActivity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                long downloadId = dm.enqueue(request);
                trackDownloadProgress(dm, downloadId, fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Standard Download failed: " + e.getMessage());
        }
    }

    private class NfsDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
                long contentLength) {
            if (downloadRequestedListener != null) {
                downloadRequestedListener.onDownloadRequested(url, userAgent, contentDisposition, mimetype);
            }
        }
    }

    private void trackDownloadProgress(final DownloadManager dm, final long downloadId, final String fileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    android.database.Cursor cursor = dm.query(q);

                    if (cursor != null && cursor.moveToFirst()) {
                        @SuppressLint("Range")
                        int bytesDownloaded = cursor
                                .getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        @SuppressLint("Range")
                        int bytesTotal = cursor
                                .getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        @SuppressLint("Range")
                        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                        if (status == DownloadManager.STATUS_SUCCESSFUL
                                || status == DownloadManager.STATUS_FAILED) {
                            downloading = false;
                        }

                        if (bytesTotal > 0) {
                            final int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                            if (downloadProgressListener != null) {
                                downloadProgressListener.onDownloadProgress(fileName, progress);
                            }
                        }
                        cursor.close();
                    }

                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        downloading = false;
                    }
                }
            }
        }).start();
    }

    private void handleDataUriDownload(Context context, String url, String mimeType) {
        try {
            String base64Data = url.substring(url.indexOf(",") + 1);
            byte[] fileBytes;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                fileBytes = java.util.Base64.getDecoder().decode(base64Data);
            } else {
                fileBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            }

            String extension = mimeType.substring(mimeType.indexOf("/") + 1);
            if (extension.contains(";"))
                extension = extension.split(";")[0];

            String fileName = "Download_" + System.currentTimeMillis() + "." + extension;

            java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            java.io.File newFile = new java.io.File(downloadsDir, fileName);

            java.io.FileOutputStream os = new java.io.FileOutputStream(newFile);
            os.write(fileBytes);
            os.close();

            if (downloadProgressListener != null) {
                downloadProgressListener.onDownloadProgress(fileName, 100);
            }

        } catch (Exception e) {
            Log.e(TAG, "Data URI Download failed: " + e.getMessage());
        }
    }

    private class NfsJSBridge {
        @JavascriptInterface
        public void requestKeyboard() {
            Activity activity = (Activity) keyboardProxy.getContext();
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    keyboardProxy.requestFocus();
                    InputMethodManager imm = (InputMethodManager) activity
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.restartInput(keyboardProxy);
                    imm.showSoftInput(keyboardProxy, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }

        @JavascriptInterface
        public void hideKeyboard() {
            hideKeyboardNative();
        }

        @JavascriptInterface
        public void processBlobDownload(String base64Data, String mimeType) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleDataUriDownload(mainActivity, base64Data, mimeType);
                }
            });
        }

    }

    public interface OnFrameReadyListener {
        void onFrameReady();
    }

    private OnFrameReadyListener frameReadyListener;

    public void setOnFrameReadyListener(OnFrameReadyListener listener) {
        this.frameReadyListener = listener;
    }

    public interface OnProgressListener {
        void onProgress(int progress);
    }

    private OnProgressListener progressListener;

    public void setOnProgressListener(OnProgressListener listener) {
        this.progressListener = listener;
    }

    public interface OnDownloadRequestedListener {
        void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimetype);
    }

    private OnDownloadRequestedListener downloadRequestedListener;

    public void setOnDownloadRequestedListener(OnDownloadRequestedListener listener) {
        this.downloadRequestedListener = listener;
    }

    public interface OnDownloadProgressListener {
        void onDownloadProgress(String fileName, int progress);
    }

    private OnDownloadProgressListener downloadProgressListener;

    public void setOnDownloadProgressListener(OnDownloadProgressListener listener) {
        this.downloadProgressListener = listener;
    }

    public interface OnFullScreenListener {
        void onFullScreenEnter();

        void onFullScreenExit();
    }

    private OnFullScreenListener fullScreenListener;

    public void setOnFullScreenListener(OnFullScreenListener listener) {
        this.fullScreenListener = listener;
    }

    public interface OnContextMenuRequestedListener {
        void onContextMenuRequested(int hitType, String extra);
    }

    private OnContextMenuRequestedListener contextMenuListener;

    public void setOnContextMenuRequestedListener(OnContextMenuRequestedListener listener) {
        this.contextMenuListener = listener;
    }

    public interface OnPageInfoListener {
        void onPageUrlChanged(String url);

        void onPageIconChanged(String iconPath);
    }

    private OnPageInfoListener pageInfoListener;

    public void setOnPageInfoListener(OnPageInfoListener listener) {
        this.pageInfoListener = listener;
    }

    public interface OnNewTabRequestedListener {
        void onNewTabRequested(String url);
    }

    private OnNewTabRequestedListener newTabListener;

    public void setOnNewTabRequestedListener(OnNewTabRequestedListener listener) {
        this.newTabListener = listener;
    }

    private static final int MAX_CACHED_ICONS = 50;

    private String getDomainName(String url) {
        if (url == null)
            return "default";
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            return host != null ? host : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    private void performCacheCleanup() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File cacheDir = mainActivity.getCacheDir();
                    File[] files = cacheDir.listFiles(new java.io.FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".png") && name.startsWith("fav_");
                        }
                    });

                    if (files != null && files.length > MAX_CACHED_ICONS) {
                        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
                            public int compare(File f1, File f2) {
                                return Long.compare(f1.lastModified(), f2.lastModified());
                            }
                        });

                        int filesToDelete = files.length - MAX_CACHED_ICONS;
                        for (int i = 0; i < filesToDelete; i++) {
                            files[i].delete();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Cache cleanup failed: " + e.getMessage());
                }
            }
        }).start();
    }

}