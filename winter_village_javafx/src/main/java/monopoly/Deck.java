/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
import java.util.Collections;
import java.util.List;

public class Deck<T extends Card> {
    private final List<T> cards;

    public Deck(List<T> cards) {
        this.cards = cards;
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public T draw() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.remove(0);
    }

    public void addCard(T card) {
        cards.add(card);
    }
}
