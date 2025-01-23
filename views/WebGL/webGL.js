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
            front: [0, 0, 1],
            up: [0, 1, 0],
            right: [-1, 0, 0]
        };
        this.shapes = [];
        this.textures = new Map(); // store the textures here
        this.setupShaders();
        this.setupGeometry();
        this.initializeBuffersAndProgram();
        this.UP_VEC = [0, 1, 0];
        this.initializeMatrices();
        this.initializeTextures();

        this.resize();
        window.addEventListener('resize', this.resize.bind(this));

        this.frame = () => {
            this.render();
            requestAnimationFrame(this.frame);
        }
        // start the render loop
        requestAnimationFrame(this.frame);
    }

    initializeBuffersAndProgram() {
        this.cubeVertices = createStaticVertexBuffer(this.gl, this.CUBE_VERTICES);
        this.cubeIndices = createStaticIndexBuffer(this.gl, this.CUBE_INDICES);
        if (!this.cubeVertices || !this.cubeIndices) {
            throw new Error('Failed to create vertex or index buffers');
        }

        this.crosshairBuffer = createStaticVertexBuffer(this.gl, this.CROSSHAIR_VERTICES);
        if (!this.crosshairBuffer) {
            throw new Error('Failed to create crosshair buffer');
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
            texCoord: gl.getAttribLocation(this.program, 'texCoord'),
        };

        this.uniforms = {
            matWorld: gl.getUniformLocation(this.program, 'matWorld'),
            matViewProj: gl.getUniformLocation(this.program, 'matViewProj'),
            textureSampler: gl.getUniformLocation(this.program, 'textureSampler'),
            useTexture: gl.getUniformLocation(this.program, 'useTexture'),
        };
    }

    setupVAO() {
        const gl = this.gl;
        const stride = 8 * Float32Array.BYTES_PER_ELEMENT; // 3 pos + 3 color + 2 texture
        this.cubeVao = createInterleavedVao(gl, this.cubeVertices, this.cubeIndices, [
            { location: this.attributes.position, size: 3, type: gl.FLOAT, normalized: false, stride, offset: 0 },
            { location: this.attributes.color, size: 3, type: gl.FLOAT, normalized: false, stride, offset: 3 * Float32Array.BYTES_PER_ELEMENT },
            { location: this.attributes.texCoord, size: 2, type: gl.FLOAT, normalized: false, stride, offset: 6 * Float32Array.BYTES_PER_ELEMENT },
        ]);

        if (!this.cubeVao) {
            throw new Error('Failed to create VAO');
        }
    }

    async loadTexture(name, imageUrl) {
        const gl = this.gl;

        // Create and bind texture
        const texture = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, texture);

        // Load temporary single pixel while image loads
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE,
            new Uint8Array([255, 255, 255, 255]));

        // Load the image
        const image = new Image();
        image.src = imageUrl;

        return new Promise((resolve, reject) => {
            image.onload = () => {
                gl.bindTexture(gl.TEXTURE_2D, texture);
                gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);

                if (isPowerOf2(image.width) && isPowerOf2(image.height)) {
                    // For power of 2 textures
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
                } else {
                    // For non-power of 2 textures
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
                    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
                }

                this.textures.set(name, texture);
                resolve(texture);
            };
            image.onerror = reject;
        });
    }

    async initializeTextures() {
        try {
            await Promise.all([
                this.loadTexture(1, '../textures/stone.png'),
                this.loadTexture(2, '../textures/grass.png'),
                this.loadTexture(3, '../textures/dirt.png'),
            ]);


            fetch('http://localhost:' + window.location.port + '/texturesload', {
                method: 'POST',
                body: JSON.stringify("Loaded textures"),
            })

            return true;
        } catch (error) {
            console.error('Failed to load textures:', error);
            return false;
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
        in vec2 texCoord;

        out vec3 fragmentColor;
        out vec2 fragmentTexCoord;

        uniform mat4 matWorld;
        uniform mat4 matViewProj;

        void main() {
            fragmentColor = vertexColor;
            fragmentTexCoord = texCoord;

            gl_Position = matViewProj * matWorld * vec4(vertexPosition, 1.0);
        }`;

        this.fragmentShaderSourceCode = `#version 300 es
        precision mediump float;

        in vec3 fragmentColor;
        in vec2 fragmentTexCoord;

        uniform sampler2D textureSampler;
        uniform bool useTexture;

        out vec4 outputColor;

        void main() {
            if (useTexture) {
                outputColor = texture(textureSampler, fragmentTexCoord) * vec4(fragmentColor, 1.0);
            } else {
                outputColor = vec4(fragmentColor, 1.0);
            }
        }`;
    }

    setupGeometry() {
        this.CUBE_VERTICES = new Float32Array([
            // Format: x, y, z, r, g, b, u, v
            // Front face
            0.0, 0.0, 1.0, 1, 1, 1, 0.0, 1.0,
            1.0, 0.0, 1.0, 1, 1, 1, 1.0, 1.0,
            1.0, 1.0, 1.0, 1, 1, 1, 1.0, 0.0,
            0.0, 1.0, 1.0, 1, 1, 1, 0.0, 0.0,

            // Back face
            0.0, 0.0, 0.0, 1, 1, 1, 1.0, 1.0,
            0.0, 1.0, 0.0, 1, 1, 1, 1.0, 0.0,
            1.0, 1.0, 0.0, 1, 1, 1, 0.0, 0.0,
            1.0, 0.0, 0.0, 1, 1, 1, 0.0, 1.0,

            // Top face
            0.0, 1.0, 0.0, 1, 1, 1, 0.0, 1.0,
            0.0, 1.0, 1.0, 1, 1, 1, 0.0, 0.0,
            1.0, 1.0, 1.0, 1, 1, 1, 1.0, 0.0,
            1.0, 1.0, 0.0, 1, 1, 1, 1.0, 1.0,

            // Bottom face
            0.0, 0.0, 0.0, 1, 1, 1, 0.0, 0.0,
            1.0, 0.0, 0.0, 1, 1, 1, 1.0, 0.0,
            1.0, 0.0, 1.0, 1, 1, 1, 1.0, 1.0,
            0.0, 0.0, 1.0, 1, 1, 1, 0.0, 1.0,

            // Right face
            1.0, 0.0, 0.0, 1, 1, 1, 0.0, 1.0,
            1.0, 1.0, 0.0, 1, 1, 1, 0.0, 0.0,
            1.0, 1.0, 1.0, 1, 1, 1, 1.0, 0.0,
            1.0, 0.0, 1.0, 1, 1, 1, 1.0, 1.0,

            // Left face
            0.0, 0.0, 0.0, 1, 1, 1, 1.0, 1.0,
            0.0, 0.0, 1.0, 1, 1, 1, 0.0, 1.0,
            0.0, 1.0, 1.0, 1, 1, 1, 0.0, 0.0,
            0.0, 1.0, 0.0, 1, 1, 1, 1.0, 0.0,
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

        // Crosshair geometry (lines in NDC space)
        this.CROSSHAIR_VERTICES = new Float32Array([
            // Horizontal line
            -0.02, 0.0, 0.0, // Left point
            0.02, 0.0, 0.0, // Right point
            // Vertical line
            0.0, -0.02, 0.0, // Bottom point
            0.0, 0.02, 0.0, // Top point
        ]);
    }

    drawCrosshair() {
        const gl = this.gl;

        gl.useProgram(this.program);

        // Bind the crosshair buffer
        gl.bindBuffer(gl.ARRAY_BUFFER, this.crosshairBuffer);

        // Enable position attribute (assuming no color for simplicity)
        gl.vertexAttribPointer(this.attributes.position, 3, gl.FLOAT, false, 0, 0);
        gl.enableVertexAttribArray(this.attributes.position);

        // Identity matrices for world, view, and projection
        const matIdentity = mat4.create();
        gl.uniformMatrix4fv(this.uniforms.matWorld, false, matIdentity);
        gl.uniformMatrix4fv(this.uniforms.matViewProj, false, matIdentity);

        // Draw the crosshair lines
        gl.drawArrays(gl.LINES, 0, 4);

        // Cleanup
        gl.disableVertexAttribArray(this.attributes.position);
        gl.bindBuffer(gl.ARRAY_BUFFER, null);
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

        this.shapes.forEach(shape => shape.draw(gl, this.uniforms.matWorld, this.uniforms));

        // Draw the crosshair (after other objects)
        this.drawCrosshair();
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
            this.gl,                        // Added gl parameter
            [x, y, z],                      // position
            1.0,                            // scale
            this.UP_VEC,                    // rotation axis
            0,                              // rotation angle
            this.cubeVao,                   // vertex array object
            this.CUBE_INDICES.length,       //number of indices
            this.textures.get(blockType)    // texture
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
    constructor(gl, pos, scale, rotationAxis, rotationAngle, vao, numIndices, texture) {
        this.gl = gl;
        this.matWorld = mat4.create();
        this.scaleVec = vec3.create();
        this.rotation = quat.create();
        this.pos = pos;
        this.scale = scale;
        this.rotationAxis = rotationAxis;
        this.rotationAngle = rotationAngle;
        this.vao = vao;
        this.numIndices = numIndices;
        this.texture = texture;
    }

    draw(gl, matWorldUniform, uniforms) {
        quat.setAxisAngle(this.rotation, this.rotationAxis, this.rotationAngle);
        vec3.set(this.scaleVec, this.scale, this.scale, this.scale);

        mat4.fromRotationTranslationScale(
            this.matWorld,
            this.rotation,
            this.pos,
            this.scaleVec
        );

        gl.uniformMatrix4fv(matWorldUniform, false, this.matWorld);

        if (this.texture) {
            gl.activeTexture(gl.TEXTURE0);
            gl.bindTexture(gl.TEXTURE_2D, this.texture);
            gl.uniform1i(uniforms.textureSampler, 0);
            gl.uniform1i(uniforms.useTexture, 1);
        } else {
            gl.uniform1i(uniforms.useTexture, 0);
        }

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

function isPowerOf2(value) {
    return (value & (value - 1)) === 0;
}
