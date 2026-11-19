/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public interface Buyable {
    int getPrice();
    int calculateRent(); 
    Player getOwner(); //Own by whom
    boolean isOwned();
    void purchase(Player buyer) throws InsufficientFundsException, InvalidActionException;
}
