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

        window.addEventListener('resize', () => {
            this.reset();
        });
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
