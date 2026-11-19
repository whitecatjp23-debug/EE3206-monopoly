/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
import java.util.List;

public interface Player {
    String getName();
    int getBalance();
    int getPosition();
    boolean isInJail();
    boolean isBankrupt();

    void gainMoney(int amount); //add money for players
    void decMoney(int amount) throws InsufficientFundsException, BankruptcyException; // decrease money for players

    void setPosition(int newPosition); //place to go
    void moveBy(int steps); //how many steps
    void sendToJail();
    void releaseFromJail();

    void addProperty(Buyable property);
    List<Buyable> getOwnedProperties(); //all the properties of the player.
}