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
    private static final double RAY_STEP = 0.1; // How precisely to check along the ray

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
                RaycastResult result = raycastBlock();
                if (result == null)
                    return;
                System.out.println(result.x);
                System.out.println(result.y);
                System.out.println(result.z);
                System.out.println(result.distance);
                System.out.println(result.hitFace);
                System.out.println(result.blockType);
                switch (button) {
                    case 0: // Left click - Break block
                        setBlock(result.x, result.y, result.z, BlockType.AIR);
                        break;
                    case 2: // Right click - Place block
                        // Calculate the position of the new block based on the hit face
                        int newX = result.x;
                        int newY = result.y;
                        int newZ = result.z;

                        switch (result.hitFace) {
                            case NORTH:
                                newZ++;
                                break;
                            case SOUTH:
                                newZ--;
                                break;
                            case EAST:
                                newX++;
                                break;
                            case WEST:
                                newX--;
                                break;
                            case UP:
                                newY++;
                                break;
                            case DOWN:
                                newY--;
                                break;
                        }

                        // Check if the new position is empty and within bounds
                        if (getBlock(newX, newY, newZ) == BlockType.AIR) {
                            setBlock(newX, newY, newZ, BlockType.STONE); // You can change the block type
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

    public RaycastResult raycastBlock() {
        double[] startPos = cameraPos.clone();
        double[] rayDir = frontVector.clone();

        // Current position along the ray
        double[] currentPos = startPos.clone();

        // Previous position (to determine which face was hit)
        double[] prevPos = startPos.clone();

        for (double distance = 0; distance <= MAX_REACH; distance += RAY_STEP) {
            // Store previous position
            System.arraycopy(currentPos, 0, prevPos, 0, 3);

            // Move along ray
            currentPos[0] = startPos[0] + rayDir[0] * distance;
            currentPos[1] = startPos[1] + rayDir[1] * distance;
            currentPos[2] = startPos[2] + rayDir[2] * distance;

            // Convert to block coordinates
            int blockX = (int) Math.floor(currentPos[0]);
            int blockY = (int) Math.floor(currentPos[1]);
            int blockZ = (int) Math.floor(currentPos[2]);

            // Check if we hit a non-air block
            BlockType blockType = getBlock(blockX, blockY, blockZ);
            if (blockType != BlockType.AIR) {
                // Determine which face was hit by comparing previous position
                Direction hitFace = determineHitFace(prevPos, currentPos, blockX, blockY, blockZ);

                return new RaycastResult(blockX, blockY, blockZ, distance, hitFace, blockType);
            }
        }

        return null; // No block found within reach
    }

    private Direction determineHitFace(double[] prevPos, double[] currentPos, int blockX, int blockY, int blockZ) {
        // Calculate the exact point where the ray entered the block
        double dx = currentPos[0] - prevPos[0];
        double dy = currentPos[1] - prevPos[1];
        double dz = currentPos[2] - prevPos[2];

        // Find which component changed the most
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        // The face is determined by which axis had the largest change
        // and whether we were approaching from the positive or negative direction
        // NOTE: might need to reverse west and east etc
        if (absX >= absY && absX >= absZ) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        } else if (absY >= absX && absY >= absZ) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        } else {
            return dz > 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }
}

public class RaycastResult { // NOTE: why plubic
    public final int x, y, z; // Position of the block hit
    public final double distance; // Distance to the block
    public final Direction hitFace; // Which face was hit
    public final BlockType blockType; // Type of block that was hit

    public RaycastResult(int x, int y, int z, double distance, Direction hitFace, BlockType blockType) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = distance;
        this.hitFace = hitFace;
        this.blockType = blockType;
    }
}

public enum Direction {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;
}
