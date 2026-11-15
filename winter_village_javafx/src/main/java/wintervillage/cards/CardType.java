package wintervillage.cards;

import javafx.scene.paint.Color;

import java.util.List;

/** The two card families used by the board. */
public enum CardType {
    ACTIVATION("activation", "激活", "激活卡", Color.web("#E8871E"),
            List.of(
                    new CardDefinition("city_pulse", "城市脉动", "选择一处建筑，免费激活它。"),
                    new CardDefinition("double_energy", "双倍能量", "本次激活的建筑产出翻倍。"),
                    new CardDefinition("precision_dispatch", "精准调度", "立即重置一个建筑的冷却。"),
                    new CardDefinition("emergency_mobilization", "紧急动员", "本回合额外激活一次。"))),

    RESOURCE("resource", "公共资源", "公共资源卡", Color.web("#2E86C1"),
            List.of(
                    new CardDefinition("public_supply", "公共补给", "从公共资源池获得 2 份资源。"),
                    new CardDefinition("shared_power", "共享电力", "所有玩家获得 1 点电力。"),
                    new CardDefinition("community_repair", "社区维修", "修复一处受损建筑。"),
                    new CardDefinition("collaboration_network", "协作网络", "抽一张激活卡并立即使用。")));

    private final String id;
    private final String deckLabel;
    private final String displayName;
    private final Color accent;
    private final List<CardDefinition> cards;

    CardType(String id, String deckLabel, String displayName,
             Color accent, List<CardDefinition> cards) {
        this.id = id;
        this.deckLabel = deckLabel;
        this.displayName = displayName;
        this.accent = accent;
        this.cards = cards;
    }

    public String id() {
        return id;
    }

    public String deckLabel() {
        return deckLabel;
    }

    public String displayName() {
        return displayName;
    }

    public Color accent() {
        return accent;
    }

    public List<CardDefinition> cards() {
        return cards;
    }
}
