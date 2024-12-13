class WebGL implements Clerk {
    LiveView view;
    final int width, height;

    WebGL(LiveView view, int width, int height) {
        this.view = view;
        this.width = Math.max(1, Math.abs(width)); // width is at least of size 1
        this.height = Math.max(1, Math.abs(height)); // height is at least of size 1
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas' width='"
            + this.width + "' height='" + this.height + "'></canvas>");
        Clerk.script(view, "const gl = new WebGL(document.getElementById('WebGLCanvas'));");
    }

    WebGL(LiveView view) {
        this(view, 500, 500);
    }

    WebGL(int width, int height) {
        this(Clerk.view(), width, height);
    }

    WebGL() {
        this(Clerk.view());
    }

    WebGL test() {
        Clerk.call(view, "gl.test();");
        return this;
    }
}
