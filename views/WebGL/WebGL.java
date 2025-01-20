import java.util.HashMap;
import java.util.Map;

class WebGL implements Clerk {
    // LiveView
    final String ID;
    LiveView view;

    // Player movement state
    private double[] cameraPos = { 0, 0, 0 }; // x, y, z
    private double[] frontVector = { 1, 0, 0 }; // Default looking along x-axis
    private double yaw = 0; // Horizontal rotation (left/right)
    private double pitch = 0; // Vertical rotation (up/down)
    private static final double MOUSE_SENSITIVITY = 0.4;
    private static final double MOVEMENT_SPEED = 0.5;
    private static final double MAX_REACH = 5.0; // Maximum distance player can reach
    private static final long UPDATE_INTERVAL_MS = 16; // Limit updates to ~60fps (16ms interval)
    private long lastUpdateTimestamp = 0;

    // World
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_HEIGHT = 128;
    private Map<Long, Chunk> chunks;

    public WebGL(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        initializeWebGL();
        handleMouseEvent();
        handleKeyEvent();
        this.chunks = new HashMap<>();

        setBlock(0, 1, 0, BlockType.GRASS);
        setBlock(15, 1, 0, BlockType.STONE);
        setBlock(0, 1, 16, BlockType.STONE);
        setBlock(0, 2, 16, BlockType.STONE);
        setBlock(-17, 1, 0, BlockType.STONE);
        setBlock(-17, 1, 1, BlockType.STONE);
        setBlock(0, 127, 0, BlockType.STONE);
    }

    public WebGL() {
        this(Clerk.view());
    }

    private void initializeWebGL() {
        Clerk.load(view, "views/WebGL/handleMnKEvent.js");
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas" + ID + "'></canvas>");
        Clerk.script(view, "const gl" + ID + " = new WebGL(document.getElementById('WebGLCanvas" + ID + "'));");
    }

    public void handleKeyEvent() {
        view.createResponseContext("/keyevent", (data) -> {
            if (data.contains("mouseDown")) {
                int button = Integer.parseInt(data.replaceAll("[^0-9]", ""));
                switch (button) {
                    case 0: // Left click - Break block
                        int[] targetBlock = raycastBlock(false);
                        if (targetBlock == null)
                            break;

                        setBlock(targetBlock[0], targetBlock[1], targetBlock[2], BlockType.AIR);
                        break;
                    case 2: // Right click - Place block
                        int[] adjacentBlock = raycastBlock(true);
                        if (adjacentBlock == null || getBlock(adjacentBlock[0], adjacentBlock[1], adjacentBlock[2]) != BlockType.AIR)
                            break;

                        setBlock(adjacentBlock[0], adjacentBlock[1], adjacentBlock[2], BlockType.STONE); // TODO: place current selected block
                        break;
                }
            } else if (data.contains("keys")) {
                // Parse the incoming JSON data
                // Extract the part between the square brackets
                String parts = data.substring(data.indexOf("[") + 1, data.indexOf("]"));

                // Split the string by commas, removing the quotes
                String[] keys = parts.replace("\"", "").split(",");

                // Calculate right vector
                double[] worldUp = { 0, 1, 0 };
                double[] rightVector = VectorUtils.crossProduct(frontVector, worldUp);

                // Handle movement
                for (String key : keys) {
                    double[] movementVec = new double[3];
                    switch (key.toLowerCase()) {
                        case "w": // Forward
                            movementVec[0] += frontVector[0] * MOVEMENT_SPEED;
                            movementVec[2] += frontVector[2] * MOVEMENT_SPEED;
                            movementVec = VectorUtils.vecMultiplication(VectorUtils.normalize(movementVec),
                                    MOVEMENT_SPEED);
                            cameraPos = VectorUtils.vecAddition(cameraPos, movementVec);
                            break;
                        case "r": // Backward
                            movementVec[0] -= frontVector[0] * MOVEMENT_SPEED;
                            movementVec[2] -= frontVector[2] * MOVEMENT_SPEED;
                            movementVec = VectorUtils.vecMultiplication(VectorUtils.normalize(movementVec),
                                    MOVEMENT_SPEED);
                            cameraPos = VectorUtils.vecAddition(cameraPos, movementVec);
                            break;
                        case "a": // Strafe left
                            movementVec[0] -= rightVector[0] * MOVEMENT_SPEED;
                            movementVec[2] -= rightVector[2] * MOVEMENT_SPEED;
                            movementVec = VectorUtils.vecMultiplication(VectorUtils.normalize(movementVec),
                                    MOVEMENT_SPEED);
                            cameraPos = VectorUtils.vecAddition(cameraPos, movementVec);
                            break;
                        case "s": // Strafe right
                            movementVec[0] += rightVector[0] * MOVEMENT_SPEED;
                            movementVec[2] += rightVector[2] * MOVEMENT_SPEED;
                            movementVec = VectorUtils.vecMultiplication(VectorUtils.normalize(movementVec),
                                    MOVEMENT_SPEED);
                            cameraPos = VectorUtils.vecAddition(cameraPos, movementVec);
                            break;
                        case " ": // Space bar
                            cameraPos[1] += MOVEMENT_SPEED;
                            break;
                        case "c":
                            cameraPos[1] -= MOVEMENT_SPEED;
                            break;
                    }
                }
                updateCamera();
            }
        });
    }

    public void handleMouseEvent() {
        view.createResponseContext("/mouseevent", (data) -> {
            // Parse the incoming JSON data
            String[] parts = data.replaceAll("[^0-9.,-]", "").split(",");

            double mouseX = Double.parseDouble(parts[0]);
            double mouseY = Double.parseDouble(parts[1]);

            // Update yaw and pitch
            yaw -= mouseX * MOUSE_SENSITIVITY;
            pitch -= mouseY * MOUSE_SENSITIVITY;

            // Clamp pitch to prevent flipping
            pitch = Math.max(-89, Math.min(89, pitch));

            // Normalize yaw to 0-360 range
            yaw = (yaw % 360 + 360) % 360;

            // Calculate front Vector
            double radYaw = Math.toRadians(yaw);
            double radPitch = Math.toRadians(pitch);
            frontVector[0] = Math.cos(radPitch) * Math.sin(radYaw);
            frontVector[1] = Math.sin(radPitch);
            frontVector[2] = Math.cos(radPitch) * Math.cos(radYaw);
            frontVector = VectorUtils.normalize(frontVector);

            updateCamera();
        });
    }

    public void updateCamera() {
        // rate limiter
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTimestamp < UPDATE_INTERVAL_MS) {
            return; // Skip this update as it's too soon
        }
        lastUpdateTimestamp = currentTime;

        // updateCamera
        Clerk.call(view, String.format("gl%s.updateCamera(%f, %f, %f, %f, %f);",
                ID, cameraPos[0], cameraPos[1], cameraPos[2], yaw, pitch));
    }

    // TODO: what if max height
    public void setBlock(int x, int y, int z, BlockType blockType) {
        Chunk chunk;
        // set block in java script(webGL)
        if (blockType == BlockType.AIR) {
            chunk = chunks.get(getChunkHash(x, z));
            if (chunk == null)
                return;
            Clerk.call(view, "gl" + ID + ".removeBlock(" + x + "," + y + "," + z + ");");
        } else {
            chunk = chunks.computeIfAbsent(getChunkHash(x, z), k -> new Chunk(x, z));
            Clerk.call(view, "gl" + ID + ".addBlock(" + x + "," + y + "," + z + "," + blockType.getId() + ");");
        }

        // set block in java(chunk)
        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localY = Math.floorMod(y, MAX_HEIGHT);
        int localZ = Math.floorMod(z, CHUNK_SIZE);
        chunk.setBlock(localX, localY, localZ, blockType);
    }

    public BlockType getBlock(int x, int y, int z) {
        Chunk chunk = chunks.get(getChunkHash(x, z));

        if (chunk == null)
            return BlockType.AIR;

        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localY = Math.floorMod(y, MAX_HEIGHT);
        int localZ = Math.floorMod(z, CHUNK_SIZE);

        return chunk.getBlock(localX, localY, localZ);
    }

    // NOTE: what if numbers are extremely large/small
    // is this right pos for this method
    private long getChunkHash(int x, int z) {
        int chunkX = x / CHUNK_SIZE;
        int chunkZ = z / CHUNK_SIZE;
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    class Chunk {
        private BlockType[][][] blocks;

        public Chunk(int x, int z) {
            this.blocks = new BlockType[CHUNK_SIZE][MAX_HEIGHT][CHUNK_SIZE];

            // Initialize the chunk with default blocks (e.g., AIR)
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        blocks[i][j][k] = BlockType.AIR;
                    }
                }
            }
        }

        public BlockType getBlock(int x, int y, int z) {
            validateChunkCoordinates(x, y, z);
            return blocks[x][y][z];
        }

        public void setBlock(int x, int y, int z, BlockType blockType) {
            validateChunkCoordinates(x, y, z);
            blocks[x][y][z] = blockType;
        }

        private void validateChunkCoordinates(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= MAX_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
                throw new IllegalArgumentException(
                        String.format("Chunk coordinates must be between 0, 0, 0 and %d, %d, %d, got: (%d, %d, %d)",
                                CHUNK_SIZE - 1, MAX_HEIGHT - 1, CHUNK_SIZE - 1, x, y, z));
            }
        }
    }

    public int[] raycastBlock(boolean returnAdjacent) {
        // Starting position and direction
        double[] ray = cameraPos.clone();
        double[] rayDir = frontVector.clone();

        // Current block position
        int[] map = new int[3];
        for (int i = 0; i < 3; i++) {
            map[i] = (int) Math.floor(ray[i]);
        }

        // Store the previous block position
        int[] prevMap = map.clone();

        // Length of ray from current position to next x, y, or z side
        double[] deltaDist = new double[3];
        for (int i = 0; i < 3; i++) {
            deltaDist[i] = Math.abs(rayDir[i]) < 0.0001 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDir[i]);
        }

        // What direction to step in x,y,z (either +1 or -1)
        int[] step = new int[3];
        // Length of ray from start to current x, y, or z-side
        double[] sideDist = new double[3];
        for (int i = 0; i < 3; i++) {
            if (rayDir[i] < 0) {
                step[i] = -1;
                sideDist[i] = (ray[i] - map[i]) * deltaDist[i];
            } else {
                step[i] = 1;
                sideDist[i] = (map[i] + 1.0 - ray[i]) * deltaDist[i];
            }
        }

        // Distance traveled along the ray
        double totalDistance = 0.0;

        // Perform DDA
        while (totalDistance < MAX_REACH) {
            // Find axis with minimum side distance
            int axis = 0;
            if (sideDist[1] < sideDist[0])
                axis = 1;
            if (sideDist[2] < sideDist[axis])
                axis = 2;

            // update total distance traveled
            totalDistance = sideDist[axis];

            // save previous map
            // if (returnAdjacent) {
            prevMap = map.clone();
            // }

            // Move to the next block in the shortest axis
            map[axis] += step[axis];

            // Update the sideDist for the axis we moved in
            sideDist[axis] += deltaDist[axis];

            // check if non air block is hit
            if (getBlock(map[0], map[1], map[2]) != BlockType.AIR) {
                return returnAdjacent ? prevMap : map;
            }
        }
        // No block found within range
        System.out.println("no block in range");
        return null;
    }
}
