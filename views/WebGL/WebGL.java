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
        setBlock(0, 0, 0, BlockType.STONE);
        setBlock(15, 0, 0, BlockType.STONE);
        setBlock(0, 0, 16, BlockType.STONE);
        setBlock(0, 1, 16, BlockType.STONE);
        setBlock(-17, 0, 0, BlockType.STONE);
        setBlock(-17, 0, 1, BlockType.STONE);
    }

    // NOTE: HELPPPPPP:P fix when on better hardware?
    public void restarteventlistener() {
        view.closeResponseContext("/mnkevent");
    }

    public void restartMouseMoveListener() {
        Clerk.call(view, "mnKEvent.restartMouseMoveListener();");
    }

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
                    case 0:
                        // Break block the player is looking at
                        int[] block = getLookingAt(10.0); // 10 units range
                        if (block != null) {
                            setBlock(block[0], block[1], block[2], BlockType.AIR); // Remove the block
                        }
                        break;
                    case 2:
                        // Place block in front of the block the player is looking at
                        int[] placeBlock = getLookingAt(10.0);
                        if (placeBlock != null) {
                            // NOTE: what blockside am i looking at
                            // Determine position to place the block
                            // int placeX = targetBlock.x + (int) Math.signum(frontVector[0]);
                            // int placeY = targetBlock.y + (int) Math.signum(frontVector[1]);
                            // int placeZ = targetBlock.z + (int) Math.signum(frontVector[2]);
                            // setBlock(placeX, placeY, placeZ, BlockType.STONE); // NOTE: Example: Place a stone block
                        }
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

    private int[] getLookingAt(double maxDistance) {
        // Starting position (player's camera position)
        double x = cameraPos[0];
        double y = cameraPos[1];
        double z = cameraPos[2];

        // Direction (normalized front vector)
        double dx = frontVector[0];
        double dy = frontVector[1];
        double dz = frontVector[2];

        // Current voxel (rounded to nearest block)
        int currentX = (int) Math.floor(x);
        int currentY = (int) Math.floor(y);
        int currentZ = (int) Math.floor(z);

        // Step direction (+1 or -1)
        int stepX = (int) Math.signum(dx);
        int stepY = (int) Math.signum(dy);
        int stepZ = (int) Math.signum(dz);

        // Compute distances to the next voxel boundary
        double tMaxX = intBound(x, dx);
        double tMaxY = intBound(y, dy);
        double tMaxZ = intBound(z, dz);

        // Compute how far to step in each direction
        double tDeltaX = Math.abs(1 / dx);
        double tDeltaY = Math.abs(1 / dy);
        double tDeltaZ = Math.abs(1 / dz);

        // Iterate through the grid
        // Adjust step size as needed Check if the current voxel contains a block
        for (int i = 0; i < maxDistance / 0.1; i++) {
            if (getBlock(currentX, currentY, currentZ) != BlockType.AIR) {
                return new int[] { currentX, currentY, currentZ };
            }

            // Step to the next voxel
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                currentX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                currentY += stepY;
                tMaxY += tDeltaY;
            } else {
                currentZ += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        // Return null if no block is found within range
        return null;
    }

    // Helper method for getLookingAt: Calculate distance to next voxel boundary
    private double intBound(double s, double ds) {
        if (ds == 0)
            return Double.POSITIVE_INFINITY; // No movement in this axis
        if (ds > 0)
            return (Math.ceil(s) - s) / ds; // Moving positive
        return (s - Math.floor(s)) / -ds; // Moving negative
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

    private long getChunkHash(int x, int z) { // NOTE: what if numbers are extremely large/small
        int chunkX = x / CHUNK_SIZE;
        int chunkZ = z / CHUNK_SIZE;
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    class Chunk { // NOTE: mussnt be subclass
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
}

public enum BlockType {
    AIR(0),
    STONE(1),
    GRASS(2),
    DIRT(3);

    private final int id;

    BlockType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static BlockType fromId(int id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("No BlockType with id: " + id);
    }
}

public static class BlockInfo { // NOTE: static?
    public final int x, y, z;
    public final BlockType blockType;

    public BlockInfo(int x, int y, int z, BlockType blockType) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
    }
}

public static class VectorUtils {
    public static double[] normalize(double[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }

        // Calculate magnitude (length) of vector
        double magnitude = 0.0;
        for (double component : vector) {
            magnitude += component * component;
        }
        magnitude = Math.sqrt(magnitude);

        // Check if vector is already normalized
        if (magnitude == 1) {
            return vector;
        }

        // Check if vector is a zero vector
        if (magnitude == 0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector");
        }

        // Create normalized vector
        double[] normalized = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / magnitude;
        }

        return normalized;
    }

    public static double[] crossProduct(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    public static double[] vecAddition(double[] a, double[] b) {
        return new double[] {
                a[0] + b[0],
                a[1] + b[1],
                a[2] + b[2]
        };
    }

    public static double[] vecMultiplication(double[] a, double k) {
        return new double[] {
                a[0] * k,
                a[1] * k,
                a[2] * k
        };
    }
}
