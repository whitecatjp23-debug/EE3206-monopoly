/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public class InsufficientFundsException extends MonoException {
    private final int required;
    private final int available;

    public InsufficientFundsException(int required, int available) {
        super(String.format("Insufficient funds: required $%d, available $%d.", required, available));
        this.required = required;
        this.available = available;
    }

    public int getRequired() { return required; }
    public int getAvailable() { return available; }
}