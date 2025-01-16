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
    private static final double MOUSE_SENSITIVITY = 0.2;
    private static final double MOVEMENT_SPEED = 0.5;
    private static final double MAX_REACH = 5.0; // Maximum distance player can reach

    // World
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_HEIGHT = 128;
    private Map<Long, Chunk> chunks;

    public WebGL(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        this.chunks = new HashMap<>();
        initializeWebGL();
        handleMnKEvent();
    }

    public WebGL() {
        this(Clerk.view());
    }

    private void initializeWebGL() {
        Clerk.load(view, "views/WebGL/handleMnKEvent.js");
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas" + ID + "'></canvas>");
        Clerk.script(view, "const gl" + ID + " = new WebGL(document.getElementById('WebGLCanvas" + ID + "'));");
        setBlock(0, 1, 0, BlockType.GRASS);
        setBlock(15, 1, 0, BlockType.STONE);
        setBlock(0, 1, 16, BlockType.STONE);
        setBlock(0, 2, 16, BlockType.STONE);
        setBlock(-17, 1, 0, BlockType.STONE);
        setBlock(-17, 1, 1, BlockType.STONE);
    }

    // maybe to much data is sent to quick
    public void handleMnKEvent() {
        view.createResponseContext("/mnkevent", (data) -> {
            if (data.contains("mouseMove")) {
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
            } else if (data.contains("mouseDown")) {
                int button = Integer.parseInt(data.replaceAll("[^0-9]", ""));
                switch (button) {
                    case 0: // Left click - Break block
                        int[] destroy = raycastBlock(false);
                        setBlock(destroy[0], destroy[1], destroy[2], BlockType.AIR);
                        break;
                    case 2: // Right click - Place block

                        // Check if the new position is empty and within bounds
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

    public void updateCamera() {
        Clerk.call(view, String.format("gl%s.updateCamera(%f, %f, %f, %f, %f);",
                ID, cameraPos[0], cameraPos[1], cameraPos[2], yaw, pitch));
    }

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
                        // NOTE: chunk blocks are not updated in js
                        blocks[i][j][k] = (j == 0) ? BlockType.STONE : BlockType.AIR;
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

    /**
     * @param hitFace retun position of adjacent block instead
     * @return Block position
     */
    public int[] raycastBlock(boolean hitFace) {
        double[] ray = cameraPos.clone();
        double[] rayDir = frontVector.clone();

        // Length of ray from current position to next x, y, or z side
        double[] deltaDist = new double[3];
        deltaDist[0] = Math.abs(rayDir[0]) < 0.0001 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDir[0]);
        deltaDist[1] = Math.abs(rayDir[1]) < 0.0001 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDir[1]);
        deltaDist[2] = Math.abs(rayDir[2]) < 0.0001 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / rayDir[2]);

        // What direction to step in x,y,z (either +1 or -1)
        int[] step = new int[3];
        step[0] = rayDir[0] < 0 ? -1 : 1;
        step[1] = rayDir[1] < 0 ? -1 : 1;
        step[2] = rayDir[2] < 0 ? -1 : 1;

        // Current block position
        int[] map = new int[3];
        map[0] = (int) Math.floor(ray[0]);
        map[1] = (int) Math.floor(ray[1]);
        map[2] = (int) Math.floor(ray[2]);

        // Length of ray from start to current x, y, or z-side
        double[] sideDist = new double[3];
        sideDist[0] = (step[0] < 0 ? ray[0] - map[0] : + 1.0 - ray[0]) * deltaDist[0];
        sideDist[1] = (step[1] < 0 ? ray[1] - map[1] : + 1.0 - ray[1]) * deltaDist[1];
        sideDist[2] = (step[2] < 0 ? ray[2] - map[2] : + 1.0 - ray[2]) * deltaDist[2];

        // Distance traveled along the ray
        double totalDistance = 0.0;

        // Perform DDAe
        while (totalDistance < MAX_REACH) {
            // Jump to next map square in x, y, or z direction
            if (sideDist[0] < sideDist[1] && sideDist[0] < sideDist[2]) {
                sideDist[0] += deltaDist[0];
                map[0] += step[0];
                totalDistance = sideDist[0];
            } else if (sideDist[1] < sideDist[2]) {
                sideDist[1] += deltaDist[1];
                map[1] += step[1];
                totalDistance = sideDist[1];
            } else {
                sideDist[2] += deltaDist[2];
                map[2] += step[2];
                totalDistance = sideDist[2];
            }

            // debug
            System.out.println("--------------------------");
            for (int i = 0; i <= 2; i++) {
                System.out.println("ray" + ray[i]);
                System.out.println("rayDir" + rayDir[i]);
                System.out.println("step" + step[i]);
                System.out.println("map" + map[i]);
                System.out.println("sideDist" + sideDist[i]);
            }
            System.out.println("totalDistance" + totalDistance);

            // check if non air block is hit
            if (getBlock(map[0], map[1], map[2]) != BlockType.AIR) {
                return map;
            }
        }
        // No block found within range
        return null;
    }
}
