# nfswebview

`nfswebview` is a high-performance, hardware-accelerated, GPU-to-GPU Android WebView rendering layer specifically engineered for the Kivy framework. By bypassing standard off-screen bitmap blitting, it directly hooks the Android hardware compositor into Kivy’s OpenGL ES context via a specialized `GL_TEXTURE_EXTERNAL_OES` pipeline.

This library delivers native 60 FPS scrolling, full support for hardware-accelerated WebRTC (video/audio streams), HTML5 Geolocation, advanced input handling (touch event mapping), and complete compatibility up to Android 16 (API 36).

> [!INFO]
> [NfsBrowser](https://github.com/novfensec/NfsBrowser) is a full fledged open source browser application build using NfsWebview.

## Architecture & Pipeline Overview

Standard off-screen rendering techniques capture web contents via pixel arrays, causing massive CPU bottlenecks. `nfswebview` eliminates this overhead through direct GPU memory mapping:

- **OpenGL OES Binding:** The Python wrapper initializes an external OpenGL texture pointer (`GL_TEXTURE_EXTERNAL_OES`) via Kivy's graphics engine.
- **Surface Association:** This texture ID is passed through Pyjnius to instantiate a native Android `SurfaceTexture` and underlying hardware `Surface`.
- **Offscreen Composite Routing:** The native Java controller (`NfsWebview`) forces the WebView's hardware canvas to draw explicitly to our target `Surface`.
- **Custom Shading:** A specialized GLSL fragment shader samples the external OES surface texture directly into Kivy’s drawing canvas canvas matrix.
- **Bidirectional Input Bridges:** Touch vectors are captured inside Kivy, translated to native Android scales, and injected directly into the WebView's event dispatch loop.
- **Direct Keyboard Mapping:** Mapped to use android system keyboard directly on textinput focus.

## Installation

Add nfswebview as a dependency in following ways with different toolchains.

### If using the ksproject toolchain

```bash
uv add git+https://github.com/Novfensec/nfswbview@master
```

Since the project explicitly uses `ksp-builder` backend, all java files will be placed in a `.java` folder under site-packages and then parsed by ksproject in the resulting AGP for compilation.

### If using python-for-android toolchain + buildozer build system

- Copy the `java` folder as it is aside the buildozer.spec.
- Edit the `buildozer.spec` to have the dependency and include the java files.

```ini
requirements = python3, kivy, ...other..., nfswebview # or https://github.com/Novfensec/nfswebview/archive/master.zip
android.add_src = ./java
```

## Quick Start & Usage

Import the `NfsWebviewWidget` directly into your layout hierarchy just like any standard Kivy component.

```python
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from nfswebview import NfsWebviewWidget

class WebBrowserApp(App):
    def build(self):
        layout = BoxLayout(orientation='vertical')

        self.webview = NfsWebviewWidget(url="https://github.com")
        layout.add_widget(self.webview)

        return layout

if __name__ == '__main__':
    WebBrowserApp().run()
```


## License

This project is licensed under the MIT License - see the LICENSE file for details.
