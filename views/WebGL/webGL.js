class WebGL {
    constructor(canvas) {
        this.canvas = canvas;
        if (!canvas) {
            throw new Error("Canvas element not provided");
        }

        this.gl = getContext(canvas);
        if (!this.gl) {
            throw new Error("Failed to initialize WebGL context");
        }

        this.camera = {
            position: [0, 0, 0],
            front: [0, 0, 1],   // Looking direction
            up: [0, 1, 0],
            right: [-1, 0, 0]
        };
        this.isRunning = false;
        this.shapes = [];
        this.setupShaders();
        this.setupGeometry();
        this.initializeBuffersAndProgram();
        this.UP_VEC = [0, 1, 0];
        this.initializeMatrices();

        this.resize();
        window.addEventListener('resize', this.resize.bind(this));

        let lastFrameTime = performance.now();
        this.frame = () => {
            if (!this.isRunning) return;
            const thisFrameTime = performance.now();
            const dt = (thisFrameTime - lastFrameTime) / 1000;
            // const fps = 1000 / (thisFrameTime - lastFrameTime);
            // console.log(fps);
            lastFrameTime = thisFrameTime;

            this.render();

            requestAnimationFrame(this.frame);
        }
    }

    initializeBuffersAndProgram() {
        this.cubeVertices = createStaticVertexBuffer(this.gl, this.CUBE_VERTICES);
        this.cubeIndices = createStaticIndexBuffer(this.gl, this.CUBE_INDICES);
        if (!this.cubeVertices || !this.cubeIndices) {
            throw new Error('Failed to create vertex or index buffers');
        }

        this.program = createProgram(this.gl, this.vertexShaderSourceCode, this.fragmentShaderSourceCode);
        if (!this.program) {
            throw new Error('Failed to create WebGL program');
        }
        this.setupAttributesAndUniforms();
        this.setupVAO();
    }

    setupAttributesAndUniforms() {
        const gl = this.gl;
        this.attributes = {
            position: gl.getAttribLocation(this.program, 'vertexPosition'),
            color: gl.getAttribLocation(this.program, 'vertexColor'),
        };

        this.uniforms = {
            matWorld: gl.getUniformLocation(this.program, 'matWorld'),
            matViewProj: gl.getUniformLocation(this.program, 'matViewProj'),
        };

        if (this.attributes.position === -1 || this.attributes.color === -1 || !this.uniforms.matWorld || !this.uniforms.matViewProj) {
            throw new Error('Failed to retrieve shader attributes or uniforms');
        }
    }

    setupVAO() {
        const gl = this.gl;
        const stride = 6 * Float32Array.BYTES_PER_ELEMENT;
        this.cubeVao = createInterleavedVao(gl, this.cubeVertices, this.cubeIndices, [ // NOTE: maybe should not use cube stuff?
            { location: this.attributes.position, size: 3, type: gl.FLOAT, normalized: false, stride, offset: 0 },
            { location: this.attributes.color, size: 3, type: gl.FLOAT, normalized: false, stride, offset: 3 * Float32Array.BYTES_PER_ELEMENT },
        ]);

        if (!this.cubeVao) {
            throw new Error('Failed to create VAO');
        }
    }

    initializeMatrices() {
        this.matView = mat4.create();
        this.matProj = mat4.create();
        this.matViewProj = mat4.create();
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
            // Cube vertices with colors (position.x, position.y, position.z, color.r, color.g, color.b)
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
    }

    resize() {
        // Set CSS size
        this.canvas.style.width = window.innerWidth + 'px';
        this.canvas.style.height = window.innerHeight + 'px';

        // Set buffer size accounting for high DPI displays
        this.canvas.width = window.innerWidth * devicePixelRatio;
        this.canvas.height = window.innerHeight * devicePixelRatio;

        // Update WebGL viewport to match
        this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    }

    render() {
        const gl = this.gl;

        mat4.perspective(
            this.matProj,
            /* fovy= */ glMatrix.toRadian(90),
            /* aspectRatio= */ this.canvas.width / this.canvas.height,
            /* near, far= */ 0.1, 100.0
        );

        // Calculate lookAt point by adding front vector to position
        const lookAtPoint = vec3.create();
        vec3.add(lookAtPoint, this.camera.position, this.camera.front);

        mat4.lookAt(
            this.matView,
            /* pos= */ this.camera.position,
            /* lookAt= */ lookAtPoint,
            /* up= */ this.camera.up
        );

        mat4.multiply(this.matViewProj, this.matProj, this.matView);

        gl.clearColor(0.47, 0.65, 1.0, 1.0);
        gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
        gl.enable(gl.DEPTH_TEST);
        gl.enable(gl.CULL_FACE);
        gl.cullFace(gl.BACK);
        gl.frontFace(gl.CCW);
        gl.viewport(0, 0, this.canvas.width, this.canvas.height);

        gl.useProgram(this.program);

        gl.uniformMatrix4fv(this.uniforms.matViewProj, false, this.matViewProj);

        this.shapes.forEach(shape => shape.draw(gl, this.uniforms.matWorld));
    }

    start() {
        if (!this.isRunning) {
            this.isRunning = true;
            this.lastFrameTime = performance.now();
            requestAnimationFrame(this.frame);
        }
    }

    stop() {
        this.isRunning = false;
    }

    updateCamera(x, y, z, yawDegrees, pitchDegrees) {
        // Update position
        this.camera.position = [x, y, z];

        // Convert degrees to radians
        const yaw = glMatrix.toRadian(yawDegrees);
        const pitch = glMatrix.toRadian(pitchDegrees);

        // Calculate new front vector
        this.camera.front = [
            Math.cos(pitch) * Math.sin(yaw),
            Math.sin(pitch),
            Math.cos(pitch) * Math.cos(yaw)
        ];
        vec3.normalize(this.camera.front, this.camera.front);

        // Calculate right and up vectors
        vec3.cross(this.camera.right, this.camera.front, this.UP_VEC);
        vec3.normalize(this.camera.right, this.camera.right);

        vec3.cross(this.camera.up, this.camera.right, this.camera.front);
        vec3.normalize(this.camera.up, this.camera.up);
    }

    addBlock(x, y, z, blockType) {
        this.shapes.push(new Shape(
            [x, y, z],                  // position
            0.5,                        // scale
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

// Utility functions
function createBuffer(gl, type, data, usage) {
    const buffer = gl.createBuffer();
    if (!buffer) {
        console.error('Failed to create buffer');
        return null;
    }

    gl.bindBuffer(type, buffer);
    gl.bufferData(type, data, usage);
    gl.bindBuffer(type, null);

    return buffer;
}

function createStaticVertexBuffer(gl, data) {
    return createBuffer(gl, gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
}

function createStaticIndexBuffer(gl, data) {
    return createBuffer(gl, gl.ELEMENT_ARRAY_BUFFER, data, gl.STATIC_DRAW);
}

function createProgram(gl, vertexShaderSource, fragmentShaderSource) {
    const vertexShader = gl.createShader(gl.VERTEX_SHADER);
    const fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
    const program = gl.createProgram();

    if (!vertexShader || !fragmentShader || !program) {
        console.error('Failed to compile vertex shader: ', gl.getShaderInfoLog(vertexShader));
        return null;
    }

    gl.shaderSource(vertexShader, vertexShaderSource);
    gl.compileShader(vertexShader);
    if (!gl.getShaderParameter(vertexShader, gl.COMPILE_STATUS)) {
        const errorMessage = gl.getShaderInfoLog(vertexShader);
        console.error('Failed to compile vertex shader');
        return null;
    }

    gl.shaderSource(fragmentShader, fragmentShaderSource);
    gl.compileShader(fragmentShader);
    if (!gl.getShaderParameter(fragmentShader, gl.COMPILE_STATUS)) {
        const errorMessage = gl.getShaderInfoLog(fragmentShader);
        console.error('Failed to compile fragment shader');
        return null;
    }

    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        const errorMessage = gl.getProgramInfoLog(program);
        console.error('Failed to link GPU program');
        return null;
    }

    return program;
}

function getContext(canvas) {
    return canvas.getContext('webgl2') || canvas.getContext('webgl');
}

function createInterleavedVao(gl, vertexBuffer, indexBuffer, attributes) {
    const vao = gl.createVertexArray();
    if (!vao) {
        console.error('Failed to create VAO');
        return null;
    }

    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer);

    attributes.forEach(attr => {
        gl.enableVertexAttribArray(attr.location);
        gl.vertexAttribPointer(attr.location, attr.size, attr.type, attr.normalized, attr.stride, attr.offset);
    });

    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
    gl.bindVertexArray(null);

    return vao;
}

function normalizeAngle(angle) {
    // Normalize angle to be between 0 and 360 degrees
    return angle % 360;
}

function clampPitch(pitch) {
    // Clamp pitch to prevent camera flipping
    return Math.max(-89, Math.min(89, pitch));
}
