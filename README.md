# nfswebview

`nfswebview` is a high-performance, hardware-accelerated, GPU-to-GPU Android WebView rendering layer specifically engineered for the Kivy framework. By bypassing standard off-screen bitmap blitting, it directly hooks the Android hardware compositor into Kivy’s OpenGL ES context via a specialized `GL_TEXTURE_EXTERNAL_OES` pipeline.

This library delivers native 60 FPS scrolling, full support for hardware-accelerated WebRTC (video/audio streams), HTML5 Geolocation, advanced input handling (touch event mapping), and complete compatibility up to Android 16 (API 36).

## Architecture & Pipeline Overview

Standard off-screen rendering techniques capture web contents via pixel arrays, causing massive CPU bottlenecks. `nfswebview` eliminates this overhead through direct GPU memory mapping:

1. **OpenGL OES Binding:** The Python wrapper initializes an external OpenGL texture pointer (`GL_TEXTURE_EXTERNAL_OES`) via Kivy's graphics engine.
2. **Surface Association:** This texture ID is passed through Pyjnius to instantiate a native Android `SurfaceTexture` and underlying hardware `Surface`.
3. **Offscreen Composite Routing:** The native Java controller (`NfsWebview`) forces the WebView's hardware canvas to draw explicitly to our target `Surface`.
4. **Custom Shading:** A specialized GLSL fragment shader samples the external OES surface texture directly into Kivy’s drawing canvas canvas matrix.
5. **Bidirectional Input Bridges:** Touch vectors and keycodes are captured inside Kivy, translated to native Android scales, and injected directly into the WebView's event dispatch loop.

## Quick Start & Usage

### 1. Initializing the UI Widget

Import the `NfsWebviewWidget` directly into your layout hierarchy just like any standard Kivy component.

```python
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from nfswebview import NfsWebviewWidget

class WebBrowserApp(App):
    def build(self):
        layout = BoxLayout(orientation='vertical')

        self.webview = NfsWebviewWidget(url="[https://github.com](https://github.com)")
        layout.add_widget(self.webview)

        return layout

if __name__ == '__main__':
    WebBrowserApp().run()

```

### 2. Executing JavaScript Code

You can inject synchronous or asynchronous JavaScript executions straight from your Python logic into the active web space:

```python
# Execute directly from Kivy thread (handled safely on the UI Thread under-the-hood)
self.webview.eval_js("document.body.style.backgroundColor = 'black';")
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
