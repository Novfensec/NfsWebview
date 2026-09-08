OES_FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
$HEADER$
uniform samplerExternalOES oes_sampler;

void main(void) {
    gl_FragColor = texture2D(oes_sampler, tex_coord0);
}
"""
