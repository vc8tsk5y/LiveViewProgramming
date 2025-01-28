// BlockType
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
// BlockType
