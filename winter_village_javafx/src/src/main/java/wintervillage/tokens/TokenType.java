package wintervillage.tokens;

/** The six classic player-token silhouettes authored for this project. */
public enum TokenType {
    TOP_HAT("top_hat", "礼帽"),
    RACE_CAR("race_car", "赛车"),
    DOG("dog", "小狗"),
    SHIP("ship", "帆船"),
    BOOT("boot", "靴子"),
    CAT("cat", "小猫");

    private final String id;
    private final String label;

    TokenType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String fileName() {
        return id + ".glb";
    }
}
