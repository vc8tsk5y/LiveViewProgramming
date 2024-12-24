class WebGL implements Clerk {
    final String ID;
    LiveView view;

    WebGL(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        Clerk.load(view, "views/WebGL/gl-util.js");
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas" + ID + "'></canvas>");
        Clerk.script(view, "const gl" + ID + " = new WebGL(document.getElementById('WebGLCanvas" + ID + "'));");
        addBlock(0, 0, 0);
        Clerk.call(view, "gl" + ID + ".start();");
    }

    WebGL() {
        this(Clerk.view());
    }

    WebGL addBlock(int x, int y, int z) {
        String pos = "" + x + "," + y + "," + z;
        Clerk.call(view, "gl" + ID + ".addBlock(" + pos +");");
        return this;
    }

    WebGL removeBlock(int x, int y, int z) {
        Clerk.call(view, "gl" + ID + ".removeBlock(" + x + "," + y + "," + z +");");
        return this;
    }
}
