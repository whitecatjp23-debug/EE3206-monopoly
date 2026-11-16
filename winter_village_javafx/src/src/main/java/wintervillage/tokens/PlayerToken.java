package wintervillage.tokens;

import javafx.scene.Group;
import javafx.scene.paint.Color;

/** Runtime state for one player token on the board. */
public final class PlayerToken {

    private final int index;
    private final String colorName;
    private final Color color;
    private final TokenType type;
    private final Group root;

    private int tileIndex;

    PlayerToken(int index, String colorName, Color color, TokenType type, Group root) {
        this.index = index;
        this.colorName = colorName;
        this.color = color;
        this.type = type;
        this.root = root;
    }

    public int index() {
        return index;
    }

    public String colorName() {
        return colorName;
    }

    public Color color() {
        return color;
    }

    public TokenType type() {
        return type;
    }

    public Group root() {
        return root;
    }

    public int tileIndex() {
        return tileIndex;
    }

    void setTileIndex(int tileIndex) {
        this.tileIndex = tileIndex;
    }
}
