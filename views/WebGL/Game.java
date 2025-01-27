import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

class Game implements Clerk {
    // LiveView
    final String ID;
    LiveView view;

    // Player movement state
    private double[] cameraPos = { 0, 10, 0 }; // x, y, z
    private double[] frontVector = { 1, 0, 0 }; // Default looking along x-axis
    private double yaw = 0; // Horizontal rotation (left/right)
    private double pitch = 0; // Vertical rotation (up/down)
    private static BlockType selectedItem = BlockType.DIRT;
    private static final double MOUSE_SENSITIVITY = 0.4;
    private static final double MOVEMENT_SPEED = 0.5;
    private static final double MAX_REACH = 5.0; // Maximum distance player can reach

    // rate limiter
    private static final long UPDATE_INTERVAL_MS = 16; // Limit updates to ~60fps (16ms interval)
    private long lastUpdateTimestamp = 0;

    // World
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_HEIGHT = 64;
    private static final int RENDER_DISTANCE = 2; // Number of chunks to render in each direction
    private long currentChunkHash;
    public Map<Long, Chunk> chunks; // private
    private Set<Long> loadedChunks = new HashSet<>();

    // Random world generation
    private final Noise terrainNoise = new Noise(12345);

    public Game(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        chunks = new ConcurrentHashMap<>();
        initializeWebGL();
        handleTexturesLoad();
        handleMouseMove();
        handleClickEvent();
    }

    public Game() {
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
        });
    }

    public void updateCamera() {
        // rate limiter
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTimestamp < UPDATE_INTERVAL_MS) {
            return;
        }
        lastUpdateTimestamp = currentTime;

        // updateCamera
        Clerk.call(view, "gl" + ID + ".updateCamera(" + cameraPos[0] + "," + cameraPos[1] + "," + cameraPos[2] + ","
                + yaw + "," + pitch + ");");
    }

    public void tp(double x, double y, double z) {
        cameraPos[0] = x;
        cameraPos[1] = y;
        cameraPos[2] = z;
        updateCamera();
    }

    public BlockType getBlock(int x, int y, int z) {
        Chunk chunk = chunks.get(getChunkHash(x, z));

        if (chunk == null || y < 0 || y >= MAX_HEIGHT)
            return null;

        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);

        return chunk.getBlock(localX, y, localZ);
    }

    public void setBlock(int x, int y, int z, BlockType blockType) {
        // prevent blocks placing out of bounds
        if (y < 0 || y >= MAX_HEIGHT) {
            System.out.println("Block height must be between 0 and " + MAX_HEIGHT + ", got: " + y);
            return;
        }

        // set block in java(chunk)
        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);

        Chunk chunk = chunks.computeIfAbsent(getChunkHash(x, z), k -> new Chunk(getChunkHash(x, z)));
        chunk.setBlock(localX, y, localZ, blockType);

        areaReload(x, y, z, 1);
    }

    // set block at camera position
    public void setBlock(BlockType blockType) {
        setBlock((int) cameraPos[0], (int) cameraPos[1], (int) cameraPos[2], blockType);
    }

    // Hash utility
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

    // Return the chunk hash the player is in
    public long playerChunk() {
        return getChunkHash((int) cameraPos[0], (int) cameraPos[2]);
    }

    // could extend if i add transparent blocks
    // check if specifyed block should be visible for performance reasons
    public boolean isVisible(int x, int y, int z) {
        boolean top = getBlock(x, y + 1, z) == BlockType.AIR;
        boolean btm = getBlock(x, y - 1, z) == BlockType.AIR;
        boolean rgt = chunks.get(getChunkHash(x + 1, z)) == null || getBlock(x + 1, y, z) == BlockType.AIR;
        boolean lft = chunks.get(getChunkHash(x - 1, z)) == null || getBlock(x - 1, y, z) == BlockType.AIR;
        boolean frt = chunks.get(getChunkHash(x, z + 1)) == null || getBlock(x, y, z + 1) == BlockType.AIR;
        boolean bck = chunks.get(getChunkHash(x, z - 1)) == null || getBlock(x, y, z - 1) == BlockType.AIR;
        return top || btm || rgt || lft || frt || bck;
    }

    public void areaReload(int x, int y, int z, int range) {
        // if chunk is not loaded area should not reload
        if (!loadedChunks.contains(getChunkHash(x, z)))
            return;
        String unloadCall = "gl" + ID + ".removeBlocksInArea(" + (x - range) + "," + (x + range) + "," + (y - range)
                + "," + (y + range) + "," + (z - range) + "," + (z + range) + ");";
        StringBuilder loadCall = new StringBuilder();
        for (int i = (x - range); i <= (x + range); i++) {
            for (int j = (y - range); j <= (y + range); j++) {
                for (int k = (z - range); k <= (z + range); k++) {
                    if (getBlock(i, j, k) != BlockType.AIR && isVisible(i, j, k)) {
                        loadCall.append("gl").append(ID).append(".addBlock(")
                                .append(i).append(",").append(j).append(",").append(k)
                                .append(",").append(getBlock(i, j, k).getId()).append(");");
                    }
                }
            }
        }
        Clerk.call(view, unloadCall.toString());
        Clerk.call(view, loadCall.toString());
    }

    public void handleChunkRendering() {
        // Update current chunk based on new camera position
        currentChunkHash = playerChunk();

        // Load/unload chunks around player
        int currentChunkX = hashToChunkCoord(currentChunkHash)[0];
        int currentChunkZ = hashToChunkCoord(currentChunkHash)[1];

        Set<Long> requiredChunks = new HashSet<>();

        // Generate required chunks within render distance
        for (int x = currentChunkX - RENDER_DISTANCE; x <= currentChunkX + RENDER_DISTANCE; x++) {
            for (int z = currentChunkZ - RENDER_DISTANCE; z <= currentChunkZ + RENDER_DISTANCE; z++) {
                requiredChunks.add(getChunkHash(x * CHUNK_SIZE, z * CHUNK_SIZE));
            }
        }

        // only load chunks that are not already loaded
        Set<Long> loadChunks = new HashSet<>(requiredChunks);
        loadChunks.removeAll(loadedChunks);

        // Load required chunks
        for (Long hash : loadChunks) {
            chunks.computeIfAbsent(hash, k -> new Chunk(k));
            chunks.get(hash).load();
            loadedChunks.add(hash);

            // Reload edges of adjacent loaded chunks
            int newX = hashToChunkCoord(hash)[0];
            int newZ = hashToChunkCoord(hash)[1];

            long eastHash = getChunkHash((newX + 1) * CHUNK_SIZE, newZ * CHUNK_SIZE);
            long westHash = getChunkHash((newX - 1) * CHUNK_SIZE, newZ * CHUNK_SIZE);
            long southHash = getChunkHash(newX * CHUNK_SIZE, (newZ + 1) * CHUNK_SIZE);
            long northHash = getChunkHash(newX * CHUNK_SIZE, (newZ - 1) * CHUNK_SIZE);

            for (long adjHash : new long[] { eastHash, westHash, southHash, northHash }) {
                if (loadedChunks.contains(adjHash)) {
                    int adjX = hashToChunkCoord(adjHash)[0];
                    int adjZ = hashToChunkCoord(adjHash)[1];
                    String direction = "";
                    if (adjX == newX + 1)
                        direction = "WEST";
                    else if (adjX == newX - 1)
                        direction = "EAST";
                    else if (adjZ == newZ + 1)
                        direction = "NORTH";
                    else if (adjZ == newZ - 1)
                        direction = "SOUTH";
                    if (!direction.isEmpty())
                        reloadChunkEdge(adjHash, direction);
                }
            }
        }

        // only unload chunks that are not in the new load set
        Set<Long> unLoadChunks = new HashSet<>(loadedChunks);
        unLoadChunks.removeAll(requiredChunks);

        for (Long hash : unLoadChunks) {
            chunks.get(hash).unload();
            loadedChunks.remove(hash);
        }
    }

    private void reloadChunkEdge(long chunkHash, String direction) {
        Chunk chunk = chunks.get(chunkHash);
        if (chunk == null)
            return;

        int chunkX = hashToChunkCoord(chunkHash)[0];
        int chunkZ = hashToChunkCoord(chunkHash)[1];

        int xStart, xEnd, zStart, zEnd;
        switch (direction) {
            case "WEST":
                xStart = chunkX * CHUNK_SIZE;
                xEnd = xStart;
                zStart = chunkZ * CHUNK_SIZE;
                zEnd = chunkZ * CHUNK_SIZE + CHUNK_SIZE - 1;
                break;
            case "EAST":
                xStart = chunkX * CHUNK_SIZE + CHUNK_SIZE - 1;
                xEnd = xStart;
                zStart = chunkZ * CHUNK_SIZE;
                zEnd = zStart + CHUNK_SIZE - 1;
                break;
            case "NORTH":
                xStart = chunkX * CHUNK_SIZE;
                xEnd = xStart + CHUNK_SIZE - 1;
                zStart = chunkZ * CHUNK_SIZE;
                zEnd = zStart;
                break;
            case "SOUTH":
                xStart = chunkX * CHUNK_SIZE;
                xEnd = xStart + CHUNK_SIZE - 1;
                zStart = chunkZ * CHUNK_SIZE + CHUNK_SIZE - 1;
                zEnd = zStart;
                break;
            default:
                return;
        }

        String unloadCall = "gl" + ID + ".removeBlocksInArea(" + xStart + "," + xEnd + ",0," + (MAX_HEIGHT - 1) + ","
                + zStart + "," + zEnd + ");";

        StringBuilder loadCall = new StringBuilder();
        for (int x = xStart; x <= xEnd; x++) {
            for (int z = zStart; z <= zEnd; z++) {
                for (int y = 0; y < MAX_HEIGHT; y++) {
                    BlockType block = getBlock(x, y, z);
                    if (block != BlockType.AIR && isVisible(x, y, z)) {
                        loadCall.append("gl").append(ID).append(".addBlock(")
                                .append(x).append(",").append(y).append(",").append(z)
                                .append(",").append(block.getId()).append(");");
                    }
                }
            }
        }

        Clerk.call(view, unloadCall);
        Clerk.call(view, loadCall.toString());
    }

    class Chunk {
        public BlockType[][][] blocks; // private
        public long hash; // private
        public int x, z;

        // random generate new chunk
        public Chunk(long hash) {
            this.blocks = new BlockType[CHUNK_SIZE][MAX_HEIGHT][CHUNK_SIZE];
            this.hash = hash;
            this.x = hashToChunkCoord(hash)[0];
            this.z = hashToChunkCoord(hash)[1];

            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int k = 0; k < CHUNK_SIZE; k++) {
                    int globalX = x * CHUNK_SIZE + i;
                    int globalZ = z * CHUNK_SIZE + k;
                    int height = Game.this.generateHeight(globalX, globalZ);

                    for (int j = 0; j < MAX_HEIGHT; j++) {
                        if (j <= height) {
                            if (j == height) {
                                blocks[i][j][k] = BlockType.GRASS;
                            } else if (j > height - 4) {
                                blocks[i][j][k] = BlockType.DIRT;
                            } else {
                                blocks[i][j][k] = BlockType.STONE;
                            }
                        } else {
                            blocks[i][j][k] = BlockType.AIR;
                        }
                    }
                }
            }
        }

        public BlockType getBlock(int x, int y, int z) {
            return blocks[x][y][z];
        }

        public void setBlock(int x, int y, int z, BlockType blockType) {
            blocks[x][y][z] = blockType;
        }

        public void load() {
            StringBuilder call = new StringBuilder();
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (blocks[i][j][k] != BlockType.AIR) {
                            int globalX = x * CHUNK_SIZE + i;
                            int globalZ = z * CHUNK_SIZE + k;
                            int globalY = j;

                            if (isVisible(globalX, globalY, globalZ)) {
                                call.append("gl").append(ID).append(".addBlock(")
                                        .append(globalX).append(",").append(globalY).append(",").append(globalZ)
                                        .append(",").append(blocks[i][j][k].getId()).append(");");
                            }
                        }
                    }
                }
            }
            Clerk.call(view, call.toString());
        }

        public void unload() {
            StringBuilder call = new StringBuilder();
            int xStart = x * CHUNK_SIZE;
            int xEnd = xStart + CHUNK_SIZE - 1;
            int zStart = z * CHUNK_SIZE;
            int zEnd = zStart + CHUNK_SIZE - 1;
            call.append("gl").append(ID).append(".removeBlocksInArea(")
                    .append(xStart).append(",").append(xEnd).append(",").append(0).append(",")
                    .append(MAX_HEIGHT - 1).append(",").append(zStart).append(",").append(zEnd)
                    .append(");");
            Clerk.call(view, call.toString());
        }

        // debug
        // generate empty chunk
        // public Chunk(long hash) {
        // this.blocks = new BlockType[CHUNK_SIZE][MAX_HEIGHT][CHUNK_SIZE];
        // this.hash = hash;
        //
        // for (int i = 0; i < CHUNK_SIZE; i++) {
        // for (int j = 0; j < MAX_HEIGHT; j++) {
        // for (int k = 0; k < CHUNK_SIZE; k++) {
        // blocks[i][j][k] = BlockType.AIR;
        // }
        // }
        // }
        // }

        // debug
        // print pos of every non air block in chunk
        public void info() {
            int countAll = 0;
            int countVisible = 0;
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < MAX_HEIGHT; j++) {
                    for (int k = 0; k < CHUNK_SIZE; k++) {
                        if (blocks[i][j][k] != BlockType.AIR) {
                            countAll++;
                            System.out.println("Block at: (" + i + ", " + j + ", " + k + ")");
                            if (isVisible(i, j, k)) {
                                countVisible++;
                            }
                        }
                    }
                }
            }
            System.out.println("Total blocks: " + countAll);
            System.out.println("Visible blocks: " + countVisible);
        }
    }

    // Generate height using Perlin noise
    private int generateHeight(int globalX, int globalZ) {
        double scale = 0.05;
        double noiseValue = terrainNoise.noise(globalX * scale, 0, globalZ * scale);
        int baseHeight = 8;
        int heightRange = 4;
        return (int) (noiseValue * heightRange + baseHeight);
    }

    private static class Noise {
        private static final int PERMUTATION_SIZE = 256;
        private int[] perm = new int[PERMUTATION_SIZE * 2];

        public Noise(long seed) {
            Random rand = new Random(seed);
            int[] p = new int[PERMUTATION_SIZE];
            for (int i = 0; i < PERMUTATION_SIZE; i++)
                p[i] = i;
            for (int i = 0; i < PERMUTATION_SIZE; i++) {
                int j = rand.nextInt(PERMUTATION_SIZE);
                int temp = p[i];
                p[i] = p[j];
                p[j] = temp;
            }
            for (int i = 0; i < PERMUTATION_SIZE; i++) {
                perm[i] = perm[i + PERMUTATION_SIZE] = p[i];
            }
        }

        private double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        public double noise(double x, double y, double z) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            int Z = (int) Math.floor(z) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            z -= Math.floor(z);
            double u = fade(x);
            double v = fade(y);
            double w = fade(z);

            int A = perm[X] + Y, AA = perm[A] + Z, AB = perm[A + 1] + Z;
            int B = perm[X + 1] + Y, BA = perm[B] + Z, BB = perm[B + 1] + Z;

            return lerp(w, lerp(v, lerp(u, grad(perm[AA], x, y, z),
                    grad(perm[BA], x - 1, y, z)),
                    lerp(u, grad(perm[AB], x, y - 1, z),
                            grad(perm[BB], x - 1, y - 1, z))),
                    lerp(v, lerp(u, grad(perm[AA + 1], x, y, z - 1),
                            grad(perm[BA + 1], x - 1, y, z - 1)),
                            lerp(u, grad(perm[AB + 1], x, y - 1, z - 1),
                                    grad(perm[BB + 1], x - 1, y - 1, z - 1))));
        }
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
            pitch = Math.max(-89.9, Math.min(89.9, pitch));

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
                handleKeyboard(parts.replace("\"", "").split(","));
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

                setBlock(adjacentBlock[0], adjacentBlock[1], adjacentBlock[2], selectedItem);
                break;
        }
    }

    private void handleKeyboard(String[] keys) {
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
                case "1":
                    selectedItem = BlockType.fromId(1);
                    break;
                case "2":
                    selectedItem = BlockType.fromId(2);
                    break;
                case "3":
                    selectedItem = BlockType.fromId(3);
                    break;
            }
        }
        updateCamera();
        if (currentChunkHash != playerChunk()) {
            handleChunkRendering();
        }
    }

    // raycasting using Amanatides-Woo algorithm
    // returnAdjacent - if false, returns the block hit by the ray
    // returnAdjacent - if true, returns the block adjacent to the hit block
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
}
