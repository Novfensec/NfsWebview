from kivy.clock import Clock

from jnius import autoclass, PythonJavaClass, java_method

PythonActivity = autoclass("org.kivy.android.PythonActivity")
GLES11Ext = autoclass("android.opengl.GLES11Ext")
MotionEvent = autoclass("android.view.MotionEvent")
NativeWebView = autoclass("com.novfensec.embeddedwebview.NfsWebview")


class FrameReadyCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnFrameReadyListener"
    ]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self._trigger = Clock.create_trigger(lambda dt: callback(), 0)

    @java_method("()V")
    def onFrameReady(self):
        self._trigger()


class ProgressCallback(PythonJavaClass):
    __javainterfaces__ = ["com/novfensec/embeddedwebview/NfsWebview$OnProgressListener"]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self._trigger = Clock.create_trigger(
            lambda dt, cb=callback: cb(self._current_progress), 0
        )
        self._current_progress = 0

    @java_method("(I)V")
    def onProgress(self, progress):
        self._current_progress = progress
        self._trigger()


class DownloadProgressCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnDownloadProgressListener"
    ]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    @java_method("(Ljava/lang/String;I)V")
    def onDownloadProgress(self, file_name, progress):
        Clock.schedule_once(lambda dt: self.callback(file_name, progress), 0)


class FullScreenCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnFullScreenListener"
    ]
    __javacontext__ = "app"

    def __init__(self, enter_callback, exit_callback):
        super().__init__()
        self.enter_callback = enter_callback
        self.exit_callback = exit_callback

    @java_method("()V")
    def onFullScreenEnter(self):
        Clock.schedule_once(lambda dt: self.enter_callback(), 0)

    @java_method("()V")
    def onFullScreenExit(self):
        Clock.schedule_once(lambda dt: self.exit_callback(), 0)


class DownloadRequestCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnDownloadRequestedListener"
    ]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    @java_method(
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    )
    def onDownloadRequested(self, url, userAgent, contentDisposition, mimetype):
        Clock.schedule_once(
            lambda dt: self.callback(url, userAgent, contentDisposition, mimetype),
            0,
        )


class ContextMenuCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnContextMenuRequestedListener"
    ]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    @java_method("(ILjava/lang/String;)V")
    def onContextMenuRequested(self, hit_type, extra):
        Clock.schedule_once(lambda dt: self.callback(hit_type, extra), 0)


class PageInfoCallback(PythonJavaClass):
    __javainterfaces__ = ["com/novfensec/embeddedwebview/NfsWebview$OnPageInfoListener"]
    __javacontext__ = "app"

    def __init__(self, url_callback, icon_callback):
        super().__init__()
        self.url_callback = url_callback
        self.icon_callback = icon_callback

    @java_method("(Ljava/lang/String;)V")
    def onPageUrlChanged(self, url):
        Clock.schedule_once(lambda dt: self.url_callback(url), 0)

    @java_method("(Ljava/lang/String;)V")
    def onPageIconChanged(self, icon_path):
        Clock.schedule_once(lambda dt: self.icon_callback(icon_path), 0)


class NewTabCallback(PythonJavaClass):
    __javainterfaces__ = [
        "com/novfensec/embeddedwebview/NfsWebview$OnNewTabRequestedListener"
    ]
    __javacontext__ = "app"

    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    @java_method("(Ljava/lang/String;)V")
    def onNewTabRequested(self, url):
        Clock.schedule_once(lambda dt: self.callback(url), 0)
