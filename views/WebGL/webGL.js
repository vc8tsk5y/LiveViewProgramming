class WebGL {
    constructor(canvas) {
        this.canvas = canvas;
        if (!canvas) {
            console.error("Canvas element not found.");
        }

        this.gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
        if (!this.gl) {
            console.error("WebGL is not supported by your browser.")
        }
        this.reset();
    }
    reset() {
        resizeCanvasToDisplaySize(this.canvas);
        this.gl.viewport(0, 0, this.canvas.width, this.canvas.height); // NOTE: useless?

        this.gl.clearColor(0.0, 0.0, 0.0, 1.0);
        this.gl.clear(this.gl.COLOR_BUFFER_BIT);
    }
}

function resizeCanvasToDisplaySize(canvas) {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
}

function test() { //NOTE: there should only be one canvas anyway
    const gl = new WebGL(document.getElehtentById('WebGLCanvas1234'));
}

window.addEventListener('resize', () => {
    resizeCanvasToDisplaySize();
});
