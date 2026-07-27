from kivy.uix.widget import Widget
from kivy.graphics import Fbo, Callback, Rectangle, Color
from kivy.graphics.texture import Texture
from kivy.utils import platform

from .shaders import OES_FRAGMENT_SHADER

if platform == "android":
    from .listeners import *
else:
    run_on_ui_thread = lambda func: func
    GLES11Ext = type("MockGLES11Ext", (), {"GL_TEXTURE_EXTERNAL_OES": 36197})()
    NativeWebView = None


class NfsWebviewWidget(Widget):
    def __init__(self, url="https://google.com", **kwargs):
        self.register_event_type("on_progress")
        self.register_event_type("on_download_progress")
        self.register_event_type("on_fullscreen_enter")
        self.register_event_type("on_fullscreen_exit")
        self.register_event_type("on_download_request")
        self.register_event_type("on_context_menu")
        self.register_event_type("on_url_changed")
        self.register_event_type("on_icon_changed")
        self.register_event_type("on_new_tab_request")
        super().__init__(**kwargs)
        self.home_url = url
        self.url = url
        self.native_webview = None
        self._gl_attached = False

        self.oes_texture = Texture(
            width=800,
            height=800,
            target=GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            colorfmt="rgba",
        )
        self.fbo = Fbo(size=(800, 800))

        self.canvas.add(self.fbo)

        if platform == "android":
            self.fbo.shader.fs = OES_FRAGMENT_SHADER

        with self.fbo:
            Color(1, 1, 1, 1)
            self.oes_binder = Callback(lambda instr: self.oes_texture.bind())
            self.fbo_rect = Rectangle(size=self.fbo.size)

        self.fbo["oes_sampler"] = 1

        with self.canvas:
            Color(1, 1, 1, 1)
            self.widget_rect = Rectangle(
                size=self.size, pos=self.pos, texture=self.fbo.texture
            )

        if platform == "android":
            self.create_java_webview()

        self.bind(size=self.on_size, pos=self.on_pos)

    @run_on_ui_thread
    def create_java_webview(self):
        context = PythonActivity.mActivity
        self.native_webview = NativeWebView(context, int(self.width), int(self.height))
        self._java_frame_listener = FrameReadyCallback(self.update_gl_surface)
        self.native_webview.setOnFrameReadyListener(self._java_frame_listener)
        self._java_progress_listener = ProgressCallback(self._dispatch_progress)
        self.native_webview.setOnProgressListener(self._java_progress_listener)
        self._java_download_listener = DownloadProgressCallback(
            self._dispatch_download_progress
        )
        self.native_webview.setOnDownloadProgressListener(self._java_download_listener)
        self._fs_listener = FullScreenCallback(
            self._dispatch_fs_enter, self._dispatch_fs_exit
        )
        self.native_webview.setOnFullScreenListener(self._fs_listener)
        self._java_request_listener = DownloadRequestCallback(
            self._dispatch_download_request
        )
        self.native_webview.setOnDownloadRequestedListener(self._java_request_listener)
        self._java_context_listener = ContextMenuCallback(self._dispatch_context_menu)
        self.native_webview.setOnContextMenuRequestedListener(
            self._java_context_listener
        )
        self._java_page_info_listener = PageInfoCallback(
            self._dispatch_url_changed, self._dispatch_icon_changed
        )
        self.native_webview.setOnPageInfoListener(self._java_page_info_listener)
        self._java_new_tab_listener = NewTabCallback(self._dispatch_new_tab)
        self.native_webview.setOnNewTabRequestedListener(self._java_new_tab_listener)
        self.native_webview.loadUrl(self.url)

    def update_gl_surface(self, dt=None):
        if not self.native_webview:
            return

        if not self._gl_attached:
            try:
                self.native_webview.attachGL(self.oes_texture.id)
                self._gl_attached = True
            except Exception:
                return

        if self.native_webview.updateTexImage():
            self.fbo.ask_update()
            self.canvas.ask_update()

    def on_size(self, instance, value):
        w, h = int(value[0]), int(value[1])
        if w == 0 or h == 0:
            return
        if platform == "android":
            self.fbo.size = (w, h)
            if hasattr(self, "fbo_rect"):
                self.fbo_rect.size = (w, h)
            if hasattr(self, "widget_rect"):
                self.widget_rect.size = value
            if self.native_webview:
                self.resize_native_view(w, h)

    def on_pos(self, instance, value):
        if hasattr(self, "widget_rect"):
            self.widget_rect.pos = value

    def on_touch_down(self, touch):
        if not self.collide_point(*touch.pos):
            return super().on_touch_down(touch)
        touch.grab(self)
        if platform == "android":
            self.dispatch_native_touch(
                MotionEvent.ACTION_DOWN,
                touch.x - self.x,
                self.height - (touch.y - self.y),
            )
        return True

    def on_touch_move(self, touch):
        if touch.grab_current is self:
            if platform == "android":
                self.dispatch_native_touch(
                    MotionEvent.ACTION_MOVE,
                    touch.x - self.x,
                    self.height - (touch.y - self.y),
                )
            return True
        return super().on_touch_move(touch)

    def on_touch_up(self, touch):
        if touch.grab_current is self:
            touch.ungrab(self)
            if platform == "android":
                self.dispatch_native_touch(
                    MotionEvent.ACTION_UP,
                    touch.x - self.x,
                    self.height - (touch.y - self.y),
                )
            return True
        return super().on_touch_up(touch)

    @run_on_ui_thread
    def resize_native_view(self, w, h):
        self.native_webview.resize(w, h)

    def dispatch_native_touch(self, action, x, y):
        if self.native_webview:
            self.native_webview.injectTouchEvent(action, float(x), float(y))

    def eval_js(self, script):
        if self.native_webview and platform == "android":
            self.native_webview.evaluateJavascript(script)

    def load_url(self, url):
        self.url = url
        if self.native_webview and platform == "android":
            self.native_webview.loadUrl(url)

    def _dispatch_url_changed(self, url):
        self.url = url
        self.dispatch("on_url_changed", url)

    def _dispatch_icon_changed(self, icon_path):
        self.dispatch("on_icon_changed", icon_path)

    def on_url_changed(self, url):
        pass

    def on_icon_changed(self, icon_path):
        pass

    def get_current_url(self):
        """Synchronously ask Java for the current URL."""
        if self.native_webview and platform == "android":
            return self.native_webview.getCurrentUrl()
        return self.url

    def reload(self):
        if self.native_webview and platform == "android":
            self.native_webview.reload()

    def go_home(self):
        if hasattr(self, "home_url"):
            self.load_url(self.home_url)

    def can_go_back(self):
        if self.native_webview and platform == "android":
            return self.native_webview.canGoBack()
        return False

    def go_back(self):
        if self.native_webview and platform == "android":
            self.native_webview.goBack()

    def can_go_forward(self):
        if self.native_webview and platform == "android":
            return self.native_webview.canGoForward()
        return False

    def go_forward(self):
        if self.native_webview and platform == "android":
            self.native_webview.goForward()

    def is_full_screen(self):
        if self.native_webview and platform == "android":
            return self.native_webview.isFullScreen()
        return False

    def exit_full_screen(self):
        if self.native_webview and platform == "android":
            self.native_webview.exitFullScreen()

    def pause_webview(self):
        if self.native_webview and platform == "android":
            self.native_webview.pause()

    def resume_webview(self):
        if self.native_webview and platform == "android":
            self.native_webview.resume()

    def destroy_webview(self):
        if self.native_webview and platform == "android":
            self.native_webview.destroy()

    def _dispatch_progress(self, progress):
        self.dispatch("on_progress", progress)

    def on_progress(self, progress):
        pass

    def _dispatch_download_progress(self, file_name, progress):
        self.dispatch("on_download_progress", file_name, progress)

    def on_download_progress(self, file_name, progress):
        pass

    def _dispatch_download_request(self, url, userAgent, contentDisposition, mimetype):
        self.dispatch(
            "on_download_request", url, userAgent, contentDisposition, mimetype
        )

    def on_download_request(self, url, userAgent, contentDisposition, mimetype):
        pass

    def proceed_with_download(self, url, userAgent, contentDisposition, mimetype):
        if self.native_webview and platform == "android":
            self.native_webview.executeDownload(
                url, userAgent, contentDisposition, mimetype
            )

    def _dispatch_fs_enter(self):
        self.dispatch("on_fullscreen_enter")

    def _dispatch_fs_exit(self):
        self.dispatch("on_fullscreen_exit")

    def on_fullscreen_enter(self):
        pass

    def on_fullscreen_exit(self):
        pass

    def _dispatch_context_menu(self, hit_type, extra):
        self.dispatch("on_context_menu", hit_type, extra)

    def on_context_menu(self, hit_type, extra):
        pass

    def _dispatch_new_tab(self, url):
        self.dispatch("on_new_tab_request", url)

    def on_new_tab_request(self, url):
        pass
