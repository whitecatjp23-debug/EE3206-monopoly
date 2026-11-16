package wintervillage.board;

/**
 * The semantic type of a Monopoly board tile.
 * <p>
 * This is part of the board model so that consumers can inspect what a tile
 * represents (e.g. to apply their own game rules).
 */
public enum TileType {
    /** The starting corner tile. */
    GO,
    /** A purchasable street / avenue. */
    PROPERTY,
    /** A railway station. */
    RAILROAD,
    /** A public utility (electricity / water). */
    UTILITY,
    /** A tax tile. */
    TAX,
    /** A "Chance" card tile (机会). */
    CHANCE,
    /** A "Community Chest" card tile (公共基金). */
    COMMUNITY,
    /** The jail corner tile. */
    JAIL,
    /** The free-parking corner tile. */
    FREE_PARKING,
    /** The "go to jail" corner tile. */
    GO_TO_JAIL
}
