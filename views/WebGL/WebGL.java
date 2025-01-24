import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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
    private static final int MAX_HEIGHT = 256;
    private static final int RENDER_DISTANCE = 4; // Number of chunks to render in each direction
    public Map<Long, Chunk> chunks; // private
    private long currentChunkHash;

    public WebGL(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        initializeWebGL();
        handleMouseMove();
        handleClickEvent();
        handleTexturesLoad();
        chunks = new ConcurrentHashMap<>();
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

    public void handleTexturesLoad() {
        view.createResponseContext("/texturesload", (data) -> {
            handleChunkRendering();
            setBlock(0, 1, 0, BlockType.DIRT);
            setBlock(0, 2, 0, BlockType.STONE);
        });
    }

    public void handleMouseMove() {
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

    public void handleClickEvent() {
        view.createResponseContext("/keyevent", (data) -> {
            if (data.contains("mouseDown")) {
                // Parse the incoming JSON data
                // remove all non-numeric characters
                handleMouseClick(Integer.parseInt(data.replaceAll("[^0-9]", "")));
            } else if (data.contains("keys")) {
                // Parse the incoming JSON data
                // Extract the part between the square brackets
                String parts = data.substring(data.indexOf("[") + 1, data.indexOf("]"));

                // Split the string by commas, removing the quotes
                handleKeyBoard(parts.replace("\"", "").split(","));
            }
        });
    }

    private void handleMouseClick(int button) {
        switch (button) {
            case 0: // Left click - Break block
                int[] targetBlock = raycastBlock(false);
                if (targetBlock == null)
                    break;

                setBlock(targetBlock[0], targetBlock[1], targetBlock[2], BlockType.AIR);
                break;
            case 2: // Right click - Place block
                int[] adjacentBlock = raycastBlock(true);
                if (adjacentBlock == null
                        || getBlock(adjacentBlock[0], adjacentBlock[1], adjacentBlock[2]) != BlockType.AIR)
                    break;

                setBlock(adjacentBlock[0], adjacentBlock[1], adjacentBlock[2], BlockType.STONE);
                break;
        }
    }

    private void handleKeyBoard(String[] keys) {
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
        if (currentChunkHash != playerChunk()) {
            handleChunkRendering();
        }
    }

    public void updateCamera() {
        // rate limiter
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTimestamp < UPDATE_INTERVAL_MS) {
            return; // Skip this update as it's too soon
        }
        lastUpdateTimestamp = currentTime;

        // updateCamera
        Clerk.call(view, "gl" + ID + ".updateCamera(" + cameraPos[0] + "," + cameraPos[1] + "," + cameraPos[2] + ","
                + yaw + "," + pitch + ");");
    }

    private void handleChunkRendering() {
        // Update current chunk based on new camera position
        currentChunkHash = playerChunk();

        // Load/unload chunks around player
        int[] currentChunkCoords = hashToChunkCoord(currentChunkHash);

        Set<Long> requiredChunks = new HashSet<>();

        // Generate all chunk hashes within render distance
        for (int x = currentChunkCoords[0] - RENDER_DISTANCE; x <= currentChunkCoords[0] + RENDER_DISTANCE; x++) {
            for (int z = currentChunkCoords[1] - RENDER_DISTANCE; z <= currentChunkCoords[1] + RENDER_DISTANCE; z++) {
                requiredChunks.add(getChunkHash(x * CHUNK_SIZE, z * CHUNK_SIZE));
            }
        }

        // Load required chunks
        for (Long hash : requiredChunks) {
            chunks.computeIfAbsent(hash, k -> new Chunk(k));
            chunks.get(hash).load();
        }

        // Unload chunks outside render distance
        Set<Long> chunksToUnload = new HashSet<>(chunks.keySet());
        chunksToUnload.removeAll(requiredChunks);

        for (Long hash : chunksToUnload) {
            chunks.get(hash).unload();
        }
    }

    public void setBlock(int x, int y, int z, BlockType blockType) {
        // prevent blocks placing out of bounds
        if (y < 0 || y >= MAX_HEIGHT) {
            System.out.println("Block height must be between 0 and " + MAX_HEIGHT + ", got: " + y);
            return;
        }

        // prevent blocks placing on already existing ones
        if (getBlock(x, y, z) != BlockType.AIR && blockType != BlockType.AIR) {
            System.out.println("Block already exists at position: (" + x + ", " + y + ", " + z + ")");
            return;
        }

        Chunk chunk;
        // set block in java script(webGL)
        if (blockType == BlockType.AIR) {
            chunk = chunks.get(getChunkHash(x, z));
            if (chunk == null)
                return;
            Clerk.call(view, "gl" + ID + ".removeBlock(" + x + "," + y + "," + z + ");");
        } else {
            chunk = chunks.computeIfAbsent(getChunkHash(x, z), k -> new Chunk(getChunkHash(x, z)));
            Clerk.call(view, "gl" + ID + ".addBlock(" + x + "," + y + "," + z + "," + blockType.getId() + ");");
        }

        // set block in java(chunk)
        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);
        chunk.setBlock(localX, y, localZ, blockType);
    }

    // return the chunk the player is in
    public long playerChunk() {
        return getChunkHash((int) cameraPos[0], (int) cameraPos[2]);
    }

    // raycasting using Amanatides-Woo algorithm
    public int[] raycastBlock(boolean returnAdjacent) {
        // Starting position and direction
        double[] origin = cameraPos.clone();
        double[] direction = frontVector.clone();

        // Current voxel coordinates
        int[] currentVoxel = {
                (int) Math.floor(origin[0]),
                (int) Math.floor(origin[1]),
                (int) Math.floor(origin[2])
        };

        // Check initial voxel
        if (getBlock(currentVoxel[0], currentVoxel[1], currentVoxel[2]) != BlockType.AIR) {
            return returnAdjacent ? null : currentVoxel.clone();
        }

        // Step directions
        int[] step = new int[3];
        double[] tMax = new double[3];
        double[] tDelta = new double[3];
        final double epsilon = 1e-8;

        // Initialize algorithm parameters
        for (int i = 0; i < 3; i++) {
            if (Math.abs(direction[i]) < epsilon) {
                // Parallel to axis, handle with large values
                step[i] = 0;
                tMax[i] = Double.POSITIVE_INFINITY;
                tDelta[i] = 0;
            } else {
                step[i] = direction[i] > 0 ? 1 : -1;
                double voxelBoundary = currentVoxel[i] + (direction[i] > 0 ? 1 : 0);
                tMax[i] = (voxelBoundary - origin[i]) / direction[i];
                tDelta[i] = step[i] / direction[i];
            }
        }

        // Previous voxel for adjacent checks
        int[] prevVoxel = currentVoxel.clone();
        double totalDistance = 0.0;

        // Traverse the voxel grid
        while (totalDistance < MAX_REACH) {
            // Find axis with smallest tMax
            int axis = 0;
            if (tMax[0] > tMax[1])
                axis = 1;
            if (tMax[axis] > tMax[2])
                axis = 2;

            // Save previous voxel before moving
            prevVoxel = currentVoxel.clone();

            // Move to next voxel
            currentVoxel[axis] += step[axis];
            totalDistance = tMax[axis];
            tMax[axis] += tDelta[axis];

            // Check if new voxel contains a block
            if (getBlock(currentVoxel[0], currentVoxel[1], currentVoxel[2]) != BlockType.AIR) {
                return returnAdjacent ? prevVoxel : currentVoxel.clone();
            }
        }

        // No block found within range
        System.out.println("no block in range");
        return null;
    }

    public int[] DDAraycastBlock(boolean returnAdjacent) {
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

    public BlockType getBlock(int x, int y, int z) {
        Chunk chunk = chunks.get(getChunkHash(x, z));

        if (chunk == null)
            return BlockType.AIR;

        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);

        return chunk.getBlock(localX, y, localZ);
    }

    class Chunk {
        public BlockType[][][] blocks; // private
        public long hash; // private

        public Chunk(long hash) {
            this.hash = hash;

            this.blocks = new BlockType[CHUNK_SIZE][MAX_HEIGHT][CHUNK_SIZE];

            // Initialize the chunk with default blocks (e.g., AIR)
            // NOTE: this spawns invisible blocks at the bottom of the world
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (j == 0) {
                            blocks[i][j][k] = BlockType.GRASS;
                        } else
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

        public int[] getBlockCoord(int x, int y, int z) {
            int[] coord = hashToChunkCoord(hash);
            System.out.println("Chunk: (" + coord[0] + ", " + coord[1] + ")");
            return new int[] { x, y, z };
        }

        private void validateChunkCoordinates(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= MAX_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
                throw new IllegalArgumentException(
                        String.format("Chunk coordinates must be between 0, 0, 0 and %d, %d, %d, got: (%d, %d, %d)",
                                CHUNK_SIZE - 1, MAX_HEIGHT - 1, CHUNK_SIZE - 1, x, y, z));
            }
        }

        public void load() {
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (blocks[i][j][k] != BlockType.AIR) {
                            int[] chunkCoords = WebGL.hashToChunkCoord(hash);
                            int x = chunkCoords[0] * CHUNK_SIZE + i;
                            int z = chunkCoords[1] * CHUNK_SIZE + k;
                            Clerk.call(view, "gl" + ID + ".addBlock(" + x + "," + j + "," + z + ","
                                    + blocks[i][j][k].getId() + ");");
                        }
                    }
                }
            }
        }

        public void unload() {
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (blocks[i][j][k] != BlockType.AIR) {
                            int[] chunkCoords = WebGL.hashToChunkCoord(hash);
                            int x = chunkCoords[0] * CHUNK_SIZE + i;
                            int z = chunkCoords[1] * CHUNK_SIZE + k;
                            Clerk.call(view, "gl" + ID + ".removeBlock(" + x + "," + j + "," + z + ");");
                        }
                    }
                }
            }
        }

        // print pos of every non air block in chunk
        // debug
        public void prnt() {
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (blocks[i][j][k] != BlockType.AIR) {
                            System.out.println("Block at: (" + i + ", " + j + ", " + k + ")");
                        }
                    }
                }
            }
        }
    }

    // hash utility
    public static long getChunkHash(int x, int z) {
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int[] hashToChunkCoord(long hash) {
        int chunkX = (int) (hash >> 32);
        int chunkZ = (int) hash;
        return new int[] { chunkX, chunkZ };
    }
}
