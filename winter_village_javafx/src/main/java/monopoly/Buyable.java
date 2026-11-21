/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
/**
 * 可被玩家擁有的地產抽象。
 *
 * <p>購買流程由 {@code PropertyMarket} 統一處理，避免付款、所有權轉移與
 * 破產判定分散在不同實作中。
 */
public interface Buyable {
    /** 回傳購買價格。 */
    int getPrice();

    /**
     * 依骰子總點數計算租金。
     *
     * @param diceTotal 兩顆骰子的點數和
     */
    int calculateRent(int diceTotal);

    /** 回傳目前持有人；無人持有時回傳 null。 */
    Player getOwner();

    /** 判斷地產是否已被持有。 */
    default boolean isOwned() {
        return getOwner() != null;
    }
}
