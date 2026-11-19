/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public abstract class Card {
    public enum CardType {
        CHANCE,
        COMMUNITY_CHEST
    }

    private final String description;
    private final CardType type;

    protected Card(String description, CardType type) {
        this.description = description;
        this.type = type;
    }

    public final String getDescription() {
        return description;
    }

    public final CardType getType() {
        return type;
    }

    public abstract void applyEffect(Player player);
}