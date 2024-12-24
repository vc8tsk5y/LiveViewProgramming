class WebGL {
    constructor(canvas) {
        this.setupGeometry();
        this.setupShaders();

        this.canvas = canvas;
        if (!canvas) {
            console.error("Canvas element not found.");
        }

        const gl = getContext(canvas);
        if (!gl) {
            console.error("Failed to get WebGL context");
            return;
        }
        this.gl = gl;

        const cubeVertices = createStaticVertexBuffer(this.gl, this.CUBE_VERTICES);
        const cubeIndices = createStaticIndexBuffer(this.gl, this.CUBE_INDICES);
        const tableVertices = createStaticVertexBuffer(this.gl, this.TABLE_VERTICES);
        const tableIndices = createStaticIndexBuffer(this.gl, this.TABLE_INDICES);

        const demoProgram = createProgram(this.gl, this.vertexShaderSourceCode, this.fragmentShaderSourceCode);

        if (!cubeVertices || !cubeIndices || !tableVertices || !tableIndices) {
            console.error('Failed to create geo: cube');
            return;
        }

        if (!demoProgram) {
            console.error('Failed to compile WebGL program');
            return;
        }

        const posAttrib = this.gl.getAttribLocation(demoProgram, 'vertexPosition');
        const colorAttrib = this.gl.getAttribLocation(demoProgram, 'vertexColor');

        const matWorldUniform = this.gl.getUniformLocation(demoProgram, 'matWorld');
        const matViewProjUniform = this.gl.getUniformLocation(demoProgram, 'matViewProj');

        if (posAttrib < 0 || colorAttrib < 0 || !matWorldUniform || !matViewProjUniform) {
            console.error('failed to get attribs/uniforms');
            return;
        }

        this.cubeVao = create3dPosColorInterleavedVao(this.gl, cubeVertices, cubeIndices, posAttrib, colorAttrib);
        this.tableVao = create3dPosColorInterleavedVao(this.gl, tableVertices, tableIndices, posAttrib, colorAttrib);

        if (!this.cubeVao || !this.tableVao) {
            console.error('failed to create Vaos');
            return;
        }

        this.UP_VEC = vec3.fromValues(0, 1, 0);
        this.shapes = [
            // new Shape(vec3.fromValues(0, 0, 0), 0.2, this.UP_VEC, 0, this.tableVao, this.TABLE_INDICES.length),   // Ground
            // new Shape(vec3.fromValues(0, 0.4, 0), 0.4, this.UP_VEC, 0, this.cubeVao, this.CUBE_INDICES.length), // Center
            // new Shape(vec3.fromValues(1, 0.05, 1), 0.05, this.UP_VEC, glMatrix.toRadian(20), this.cubeVao, this.CUBE_INDICES.length),
            // new Shape(vec3.fromValues(1, 0.1, -1), 0.1, this.UP_VEC, glMatrix.toRadian(40), this.cubeVao, this.CUBE_INDICES.length),
            // new Shape(vec3.fromValues(-1, 0.15, 1), 0.15, this.UP_VEC, glMatrix.toRadian(60), this.cubeVao, this.CUBE_INDICES.length),
            // new Shape(vec3.fromValues(-1, 0.2, -1), 0.2, this.UP_VEC, glMatrix.toRadian(80), this.cubeVao, this.CUBE_INDICES.length),
        ];

        const matView = mat4.create();
        const matProj = mat4.create();
        const matViewProj = mat4.create();

        let cameraAngle = 0;


        // Render
        let lastFrameTime = performance.now();
        this.frame = () => {
            const thisFrameTime = performance.now();
            const dt = (thisFrameTime - lastFrameTime) / 1000;
            lastFrameTime = thisFrameTime;

            // Update
            cameraAngle += dt * glMatrix.toRadian(10);

            const cameraX = 3 * Math.sin(cameraAngle);
            const cameraZ = 3 * Math.cos(cameraAngle);

            mat4.lookAt(
                matView,
                /* pos= */ vec3.fromValues(cameraX, 1, cameraZ),
                /* lookAt= */ vec3.fromValues(0, 0, 0),
                /* up= */ vec3.fromValues(0, 1, 0)
            );
            mat4.perspective(
                matProj,
                /* fovy= */ glMatrix.toRadian(80),
                /* aspectRatio= */ canvas.width / canvas.height,
                /* near, far= */ 0.1, 100.0
            );

            // in GLM:    matViewProj = matProj * matView
            mat4.multiply(matViewProj, matProj, matView);

            // Render
            this.reset();

            this.gl.clearColor(0.47, 0.65, 1.00 , 1);
            this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT);
            this.gl.enable(this.gl.DEPTH_TEST);
            this.gl.enable(this.gl.CULL_FACE);
            this.gl.cullFace(this.gl.BACK);
            this.gl.frontFace(this.gl.CCW);
            this.gl.viewport(0, 0, canvas.width, canvas.height);

            this.gl.useProgram(demoProgram);
            this.gl.uniformMatrix4fv(matViewProjUniform, false, matViewProj);

            this.shapes.forEach((shape) => shape.draw(this.gl, matWorldUniform));
            requestAnimationFrame(this.frame);
        }
    }

    start() {
        requestAnimationFrame(this.frame);
    }

    addBlock(x, y, z) {
        this.shapes.push(new Shape(
            vec3.fromValues(x, y, z),   // position
            1,                          // scale
            this.UP_VEC,                // rotation axis
            0,                          // rotation angle
            this.cubeVao,               // vertex array object
            this.CUBE_INDICES.length    //number of indices
        ));
    }

    removeBlock(x, y, z) {
        this.shapes = this.shapes.filter(shape => {
            return !(shape.pos[0] === x &&
                shape.pos[1] === y &&
                shape.pos[2] === z);
        });
    }

    reset() {
        // Set CSS size
        this.canvas.style.width = window.innerWidth + 'px';
        this.canvas.style.height = window.innerHeight + 'px';

        // Set buffer size accounting for high DPI displays
        this.canvas.width = window.innerWidth * devicePixelRatio;
        this.canvas.height = window.innerHeight * devicePixelRatio;

        // Update WebGL viewport to match
        this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    }

    setupShaders() {
        this.vertexShaderSourceCode = `#version 300 es
        precision mediump float;

        in vec3 vertexPosition;
        in vec3 vertexColor;

        out vec3 fragmentColor;

        uniform mat4 matWorld;
        uniform mat4 matViewProj;

        void main() {
          fragmentColor = vertexColor;

          gl_Position = matViewProj * matWorld * vec4(vertexPosition, 1.0);
        }`;

        this.fragmentShaderSourceCode = `#version 300 es
        precision mediump float;

        in vec3 fragmentColor;
        out vec4 outputColor;

        void main() {
          outputColor = vec4(fragmentColor, 1.0);
        }`;
    }

    setupGeometry() {
        this.CUBE_VERTICES = new Float32Array([
            // Front face
            -1.0, -1.0, 1.0, 1, 0, 0,
            1.0, -1.0, 1.0, 1, 0, 0,
            1.0, 1.0, 1.0, 1, 0, 0,
            -1.0, 1.0, 1.0, 1, 0, 0,

            // Back face
            -1.0, -1.0, -1.0, 1, 0, 0,
            -1.0, 1.0, -1.0, 1, 0, 0,
            1.0, 1.0, -1.0, 1, 0, 0,
            1.0, -1.0, -1.0, 1, 0, 0,

            // Top face
            -1.0, 1.0, -1.0, 0, 1, 0,
            -1.0, 1.0, 1.0, 0, 1, 0,
            1.0, 1.0, 1.0, 0, 1, 0,
            1.0, 1.0, -1.0, 0, 1, 0,

            // Bottom face
            -1.0, -1.0, -1.0, 0, 1, 0,
            1.0, -1.0, -1.0, 0, 1, 0,
            1.0, -1.0, 1.0, 0, 1, 0,
            -1.0, -1.0, 1.0, 0, 1, 0,

            // Right face
            1.0, -1.0, -1.0, 0, 0, 1,
            1.0, 1.0, -1.0, 0, 0, 1,
            1.0, 1.0, 1.0, 0, 0, 1,
            1.0, -1.0, 1.0, 0, 0, 1,

            // Left face
            -1.0, -1.0, -1.0, 0, 0, 1,
            -1.0, -1.0, 1.0, 0, 0, 1,
            -1.0, 1.0, 1.0, 0, 0, 1,
            -1.0, 1.0, -1.0, 0, 0, 1,
        ]);
        this.CUBE_INDICES = new Uint16Array([
            0, 1, 2,
            0, 2, 3, // front
            4, 5, 6,
            4, 6, 7, // back
            8, 9, 10,
            8, 10, 11, // top
            12, 13, 14,
            12, 14, 15, // bottom
            16, 17, 18,
            16, 18, 19, // right
            20, 21, 22,
            20, 22, 23, // left
        ]);

        this.TABLE_VERTICES = new Float32Array([
            // Top face
            -10.0, 0.0, -10.0, 0.2, 0.2, 0.2,
            -10.0, 0.0, 10.0, 0.2, 0.2, 0.2,
            10.0, 0.0, 10.0, 0.2, 0.2, 0.2,
            10.0, 0.0, -10.0, 0.2, 0.2, 0.2,
        ]);
        this.TABLE_INDICES = new Uint16Array([
            0, 1, 2,
            0, 2, 3, // top
        ]);
    }
}


class Shape {
    constructor(pos, scale, rotationAxis, rotationAngle, vao, numIndices) {
        this.matWorld = mat4.create();
        this.scaleVec = vec3.create();
        this.rotation = quat.create();
        this.pos = pos;
        this.scale = scale;
        this.rotationAxis = rotationAxis;
        this.rotationAngle = rotationAngle;
        this.vao = vao;
        this.numIndices = numIndices;
    }

    draw(gl, matWorldUniform) {
        quat.setAxisAngle(this.rotation, this.rotationAxis, this.rotationAngle);
        vec3.set(this.scaleVec, this.scale, this.scale, this.scale);

        mat4.fromRotationTranslationScale(
            this.matWorld,
            /* rotation= */ this.rotation,
            /* position= */ this.pos,
            /* scale= */ this.scaleVec
        );

        gl.uniformMatrix4fv(matWorldUniform, false, this.matWorld);
        gl.bindVertexArray(this.vao);
        gl.drawElements(gl.TRIANGLES, this.numIndices, gl.UNSIGNED_SHORT, 0);
        gl.bindVertexArray(null);
    }
}
