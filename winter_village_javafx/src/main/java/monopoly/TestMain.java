/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
import java.util.ArrayList;
import java.util.List;

public class TestMain {
    public static void main(String[] args) throws InsufficientFundsException, BankruptcyException {
        System.out.println("=== STARTING MONOPOLY S1 ARCHITECTURE TESTS ===");

        // 1. Test Player Operations & Encapsulation (Mocking Player behavior)
        Player testPlayer = new Player() {
            private int balance = 1500;
            private int position = 0;
            private boolean inJail = false;
            private final List<Buyable> properties = new ArrayList<>();

            @Override public String getName() { return "Tester"; }
            @Override public int getBalance() { return balance; }
            @Override public int getPosition() { return position; }
            @Override public boolean isInJail() { return inJail; }
            @Override public boolean isBankrupt() { return balance < 0; }
            
            @Override public void gainMoney(int amount) {
                balance += amount;
                System.out.println("[Money] Added $" + amount + ". New balance: $" + balance);
            }
            
            @Override public void decMoney(int amount) throws InsufficientFundsException, BankruptcyException {
                if (amount > balance) {
                    throw new InsufficientFundsException(amount, balance);
                }
                balance -= amount;
                System.out.println("[Money] Deducted $" + amount + ". New balance: $" + balance);
            }
            
            @Override public void setPosition(int p) { this.position = p; }
            @Override public void moveBy(int steps) { this.position = (this.position + steps) % 40; }
            @Override public void sendToJail() { this.inJail = true; }
            @Override public void releaseFromJail() { this.inJail = false; }
            @Override public void addProperty(Buyable prop) { properties.add(prop); }
            @Override public void removeProperty(Buyable prop) { properties.remove(prop); }
            @Override public List<Buyable> getOwnedProperties() { return properties; }
        };

        // 2. Test Exceptions (Insufficient Funds)
        System.out.println("\n--- Testing Exception Handling ---");
        try {
            testPlayer.decMoney(2000); // Should fail
        } catch (InsufficientFundsException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        } catch (BankruptcyException e) {
            System.out.println("Bankruptcy caught: " + e.getMessage());
        }

        // 3. Test Tile Polymorphism
        System.out.println("\n--- Testing Tile Polymorphism ---");
        monopoly.board.tile.Tile goTile = new monopoly.board.tile.GoTile(0, "GO");
        goTile.landOn(testPlayer, 7);

        // 4. Test Generic Deck & Cards
        System.out.println("\n--- Testing Generic Deck & Cards ---");
        Card moneyCard = new Card("Bank Error", Card.CardType.COMMUNITY_CHEST) {
            @Override
            public void applyEffect(Player player) {
                player.gainMoney(100);
            }
        };

        List<Card> cardList = new ArrayList<>();
        cardList.add(moneyCard);
        
        Deck<Card> deck = new Deck<>(cardList);
        deck.shuffle();
        
        Card drawnCard = deck.draw();
        if (drawnCard != null) {
            System.out.println("Drew card: " + drawnCard.getDescription());
            drawnCard.applyEffect(testPlayer);
        }

        System.out.println("\n=== ALL TESTS COMPLETED SUCCESSFULLY ===");
    }
}