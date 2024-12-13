class WebGL implements Clerk {
    LiveView view;

    WebGL(LiveView view) {
        this.view = view;
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas'></canvas>");
        Clerk.script(view, "const gl = new WebGL(document.getElementById('WebGLCanvas'));");
    }

    WebGL() {
        this(Clerk.view());
    }

    WebGL test() {
        Clerk.call(view, "gl.test();");
        return this;
    }
}
