/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public class BankruptcyException extends MonoException {
    private final String playerName;

    public BankruptcyException(String playerName, String reason) {
        super(playerName + " is bankrupt! Reason: " + reason);
        this.playerName = playerName;
    }

    public String getPlayerName() { return playerName; }
}
