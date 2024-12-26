class WebGL implements Clerk {
    private static final int CHUNK_SIZE = 16; // NOTE: MAX_HEIGHT? (thow error if y > MAX_HEIGHT)
    final String ID;
    LiveView view;
    private Map<Long, Chunk> chunks;

    public WebGL(LiveView view) {
        this.view = view;
        ID = Clerk.getHashID(this);
        this.chunks = new HashMap<>();
        initializeWebGL();
    }

    public WebGL() {
        this(Clerk.view());
    }

    private void initializeWebGL() {
        Clerk.load(view, "views/WebGL/gl-util.js");
        Clerk.load(view, "views/WebGL/webGL.js");
        Clerk.write(view, "<canvas id='WebGLCanvas" + ID + "'></canvas>");
        Clerk.script(view, "const gl" + ID + " = new WebGL(document.getElementById('WebGLCanvas" + ID + "'));");
        setBlock(0, 0, 0, BlockType.STONE);
        setBlock(15, 0, 0, BlockType.STONE);
        setBlock(0, 0, 16, BlockType.STONE);
        setBlock(-17, 0, 0, BlockType.STONE);
        Clerk.call(view, "gl" + ID + ".start();");
    }

    public WebGL setBlock(int x, int y, int z, BlockType blockType) {
        Chunk chunk;
        // set block in java script(webGL)
        if (blockType == BlockType.AIR) {
            chunk = chunks.get(getChunkHash(x, z));
            if (chunk == null)
                return this;
            Clerk.call(view, "gl" + ID + ".removeBlock(" + x + "," + y + "," + z + ");");
        } else {
            chunk = chunks.computeIfAbsent(getChunkHash(x, z), k -> new Chunk(x, z));
            Clerk.call(view, "gl" + ID + ".addBlock(" + x + "," + y + "," + z + "," + blockType.getId() + ");");
        }

        // set block in java(chunk)
        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localY = Math.floorMod(y, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);
        chunk.setBlock(localX, localY, localZ, blockType);
        return this;
    }

    public BlockType getBlock(int x, int y, int z) {
        Chunk chunk = chunks.get(getChunkHash(x, z));

        if (chunk == null)
            return BlockType.AIR;

        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localY = Math.floorMod(y, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);

        return chunk.getBlock(localX, localY, localZ);
    }

    private long getChunkHash(int x, int z) {
        int chunkX = x / CHUNK_SIZE;
        int chunkZ = z / CHUNK_SIZE;
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    class Chunk {
        private BlockType[][][] blocks;

        public Chunk(int x, int z) {
            this.blocks = new BlockType[CHUNK_SIZE][CHUNK_SIZE][CHUNK_SIZE];

            // Initialize the chunk with default blocks (e.g., AIR)
            for (int i = 0; i < CHUNK_SIZE; i++) {
                for (int j = 0; j < CHUNK_SIZE; j++) {
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
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
                throw new IllegalArgumentException(
                    String.format("Chunk coordinates must be between 0 and %d, got: (%d, %d, %d)",
                        CHUNK_SIZE - 1, x, y, z)
                );
            }
        }
    }
}

public enum BlockType {
    AIR(0),
    STONE(1),
    Grass(2),
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
