package wintervillage.cards;

public record CardDefinition(
        String id,
        String title,
        String description,
        int amount,        // + gain, - pay; 0 for non-money cards
        int targetTile,
        boolean goToJail   // true only for "Go to Jail"
) {

    public CardDefinition(String id, String title, String description) {
        this(id, title, description, 0, -1, false);
    }
}