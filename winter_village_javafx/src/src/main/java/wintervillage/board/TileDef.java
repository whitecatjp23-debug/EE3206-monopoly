package wintervillage.board;

import javafx.scene.paint.Color;

/**
 * Immutable description of a single board tile. Consumers of the board can read
 * these definitions (name, type, group colour, price) to implement game logic,
 * without touching any rendering code.
 */
public final class TileDef {

    private final int index;
    private final String name;
    private final TileType type;
    private final Color color;
    private final int price;

    public TileDef(int index, String name, TileType type, Color color, int price) {
        this.index = index;
        this.name = name;
        this.type = type;
        this.color = color;
        this.price = price;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public TileType type() {
        return type;
    }

    /** The property colour-group colour, or the tile accent colour. */
    public Color color() {
        return color;
    }

    /** Purchase price; 0 for non-property tiles. */
    public int price() {
        return price;
    }

    // ------------------------------------------------------------------
    // Colour helpers (classic Monopoly palette)
    // ------------------------------------------------------------------
    private static Color c(String hex) {
        return Color.web(hex);
    }

    private static final Color BROWN = c("#8D5B3F");
    private static final Color LIGHT_BLUE = c("#A5D8F3");
    private static final Color PINK = c("#D85B96");
    private static final Color ORANGE = c("#F28C28");
    private static final Color RED = c("#E0343E");
    private static final Color YELLOW = c("#F4D03F");
    private static final Color GREEN = c("#2E9E5B");
    private static final Color DARK_BLUE = c("#1F4E9C");
    private static final Color RAIL = c("#3E3A39");
    private static final Color UTIL = c("#BFC3C9");
    private static final Color CHANCE = c("#E8871E");
    private static final Color COMMUNITY = c("#2E86C1");
    private static final Color CORNER = c("#3E8E5A");

    /**
     * The 40 tiles of a classic board, in clockwise layout order (counter-clockwise
     * movement order), localised to a Chinese 大富翁 theme.
     */
    public static TileDef[] all() {
        return new TileDef[] {
                new TileDef(0, "起点", TileType.GO, CORNER, 0),
                new TileDef(1, "地中海大道", TileType.PROPERTY, BROWN, 60),
                new TileDef(2, "机会", TileType.CHANCE, CHANCE, 0),
                new TileDef(3, "波罗的海大道", TileType.PROPERTY, BROWN, 60),
                new TileDef(4, "所得税", TileType.TAX, UTIL, 200),
                new TileDef(5, "国王十字车站", TileType.RAILROAD, RAIL, 200),
                new TileDef(6, "东方大道", TileType.PROPERTY, LIGHT_BLUE, 100),
                new TileDef(7, "机会", TileType.CHANCE, CHANCE, 0),
                new TileDef(8, "佛蒙特大道", TileType.PROPERTY, LIGHT_BLUE, 100),
                new TileDef(9, "康涅狄格大道", TileType.PROPERTY, LIGHT_BLUE, 120),

                new TileDef(10, "监狱 / 探访", TileType.JAIL, CORNER, 0),
                new TileDef(11, "圣查尔斯广场", TileType.PROPERTY, PINK, 140),
                new TileDef(12, "电力公司", TileType.UTILITY, UTIL, 150),
                new TileDef(13, "州大道", TileType.PROPERTY, PINK, 140),
                new TileDef(14, "弗吉尼亚大道", TileType.PROPERTY, PINK, 160),
                new TileDef(15, "利物浦街车站", TileType.RAILROAD, RAIL, 200),
                new TileDef(16, "圣詹姆斯广场", TileType.PROPERTY, ORANGE, 180),
                new TileDef(17, "公共基金", TileType.COMMUNITY, COMMUNITY, 0),
                new TileDef(18, "田纳西大道", TileType.PROPERTY, ORANGE, 180),
                new TileDef(19, "纽约大道", TileType.PROPERTY, ORANGE, 200),

                new TileDef(20, "免费停车", TileType.FREE_PARKING, CORNER, 0),
                new TileDef(21, "肯塔基大道", TileType.PROPERTY, RED, 220),
                new TileDef(22, "机会", TileType.CHANCE, CHANCE, 0),
                new TileDef(23, "印第安纳大道", TileType.PROPERTY, RED, 220),
                new TileDef(24, "伊利诺伊大道", TileType.PROPERTY, RED, 240),
                new TileDef(25, "帕丁顿车站", TileType.RAILROAD, RAIL, 200),
                new TileDef(26, "大西洋大道", TileType.PROPERTY, YELLOW, 260),
                new TileDef(27, "文特诺大道", TileType.PROPERTY, YELLOW, 260),
                new TileDef(28, "自来水公司", TileType.UTILITY, UTIL, 150),
                new TileDef(29, "马文花园", TileType.PROPERTY, YELLOW, 280),

                new TileDef(30, "进监狱", TileType.GO_TO_JAIL, CORNER, 0),
                new TileDef(31, "太平洋大道", TileType.PROPERTY, GREEN, 300),
                new TileDef(32, "北卡罗来纳大道", TileType.PROPERTY, GREEN, 300),
                new TileDef(33, "公共基金", TileType.COMMUNITY, COMMUNITY, 0),
                new TileDef(34, "宾夕法尼亚大道", TileType.PROPERTY, GREEN, 320),
                new TileDef(35, "尤斯顿车站", TileType.RAILROAD, RAIL, 200),
                new TileDef(36, "机会", TileType.CHANCE, CHANCE, 0),
                new TileDef(37, "公园广场", TileType.PROPERTY, DARK_BLUE, 350),
                new TileDef(38, "奢侈品税", TileType.TAX, UTIL, 100),
                new TileDef(39, "木板路", TileType.PROPERTY, DARK_BLUE, 400),
        };
    }
}
