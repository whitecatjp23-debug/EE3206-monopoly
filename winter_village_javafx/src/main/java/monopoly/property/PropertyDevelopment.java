package monopoly.property;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.InvalidActionException;
import monopoly.Player;

import java.util.Objects;

/**
 * 建造服務：房屋與酒店的增建、拆除。
 *
 * <p>官方規則的建造限制集中在這裡，避免 UI 各自實作而出現不同版本：
 * <ol>
 *   <li><b>集滿整組</b>：必須持有同一顏色組別的全部地產才能建造；</li>
 *   <li><b>無抵押</b>：組別內有任何一筆抵押中就不能建造；</li>
 *   <li><b>平均建造</b>：只能建在「目前等級最低」的那幾格，讓同組差距不超過 1；</li>
 *   <li><b>上限</b>：等級 5（酒店）為最高，之後不能再建。</li>
 * </ol>
 *
 * <p>其中「平均建造／平均拆除」需要窺看整組的建築狀態，屬於組別自己的知識，
 * 因此實際判斷在 {@link PropertyGroup#isLegalBuildTarget} 與
 * {@link PropertyGroup#isLegalSellTarget}；本服務只負責前置檢查、付款與復原。
 *
 * <p>每一步交易都遵守同一個原則：<b>先確認所有條件，再動任何狀態；
 * 狀態動完若付款失敗，就把剛才的改動還原</b>。
 * 因此呼叫端不會遇到「錢扣了但房屋沒蓋」或「房屋蓋了但沒付錢」的半套狀態。
 */
public final class PropertyDevelopment {

  /** 建立無狀態的建造服務。 */
  public PropertyDevelopment() {
  }

  /**
   * 判斷玩家現在能否在這一格增建。
   *
   * @param owner 想建造的玩家
   * @param property 目標地產
   */
  public boolean canBuild(Player owner, Property property) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!(property instanceof Developable developable)) {
      return false;
    }
    PropertyGroup group = property.getGroup();
    return group.isBuildable()
        && property.isOwnedBy(owner)
        && group.isCompleteFor(owner)
        && !group.hasMortgagedMember()
        && developable.canBuild()
        && owner.getBalance() >= group.getBuildingCost()
        && group.isLegalBuildTarget(property);
  }

  /**
   * 在這一格增建一級（房屋 1→4，第 4 棟房屋之後即為酒店）。
   *
   * @param owner 建造的玩家
   * @param property 目標地產
   * @return 實際支付的建造費用
   * @throws InvalidActionException 違反集滿整組、抵押、平均建造或上限規則
   * @throws InsufficientFundsException 現金不足（此時不會扣款，也不會留下建築）
   * @throws BankruptcyException 玩家實作判定為破產時
   */
  public int build(Player owner, Property property)
      throws InvalidActionException, InsufficientFundsException, BankruptcyException {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!(property instanceof AbstractDevelopableProperty target)) {
      throw new InvalidActionException(property.getName() + " 不能建造房屋或酒店。");
    }
    if (!property.isOwnedBy(owner)) {
      throw new InvalidActionException("只有持有人可以建造 " + property.getName() + "。");
    }

    PropertyGroup group = property.getGroup();
    if (!group.isBuildable()) {
      throw new InvalidActionException(group.getName() + " 組不能建造。");
    }
    if (!group.isCompleteFor(owner)) {
      throw new InvalidActionException(
          "必須先集滿 " + group.getName() + " 整組（"
              + group.countOwnedBy(owner) + "/" + group.getRequiredProperties() + "）才能建造。");
    }
    if (group.hasMortgagedMember()) {
      throw new InvalidActionException(group.getName() + " 組內有地產抵押中，不能建造。");
    }
    if (!target.canBuild()) {
      throw new InvalidActionException(property.getName() + " 已達酒店上限。");
    }
    if (!group.isLegalBuildTarget(property)) {
      throw new InvalidActionException("必須平均建造：先建等級較低的地產。");
    }

    int cost = group.getBuildingCost();
    // 先蓋再付款：付款失敗時把剛才加的那一級還原，避免留下沒付錢的建築。
    target.build();
    try {
      owner.decMoney(cost);
    } catch (InsufficientFundsException | BankruptcyException | RuntimeException failure) {
      target.sellBuilding();
      throw failure;
    }
    return cost;
  }

  /**
   * 判斷玩家現在能否拆除這一格的建築。
   *
   * @param owner 持有人
   * @param property 目標地產
   */
  public boolean canSellBuilding(Player owner, Property property) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!(property instanceof Developable developable)) {
      return false;
    }
    if (developable.getBuildingLevel() == BuildingLevel.NONE || !property.isOwnedBy(owner)) {
      return false;
    }
    // 平均拆除：只能拆組內等級最高的格子。
    return property.getGroup().isLegalSellTarget(property);
  }

  /**
   * 拆除一級建築（酒店退回 4 棟房屋），並退回半數建造費用。
   *
   * @param owner 持有人
   * @param property 目標地產
   * @return 退回的金額
   * @throws InvalidActionException 沒有建築、不是持有人，或違反平均拆除
   */
  public int sellBuilding(Player owner, Property property) throws InvalidActionException {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!(property instanceof AbstractDevelopableProperty target)) {
      throw new InvalidActionException(property.getName() + " 沒有可拆除的建築。");
    }
    if (!property.isOwnedBy(owner)) {
      throw new InvalidActionException("只有持有人可以拆除 " + property.getName() + " 的建築。");
    }
    if (target.getBuildingLevel() == BuildingLevel.NONE) {
      throw new InvalidActionException(property.getName() + " 沒有建築可拆除。");
    }
    if (!property.getGroup().isLegalSellTarget(property)) {
      throw new InvalidActionException("必須平均拆除：先拆等級較高的地產。");
    }

    int refund = property.getGroup().getBuildingCost() / 2;
    target.sellBuilding();
    owner.gainMoney(refund);
    return refund;
  }

  /**
   * 統計玩家在某一組已蓋的建築總數（房屋與酒店都算 1 級）。
   *
   * @param owner 要查詢的玩家
   * @param group 目標組別
   */
  public int countBuildings(Player owner, PropertyGroup group) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(group, "group");
    int total = 0;
    for (Property member : group.getMembersOwnedBy(owner)) {
      if (member instanceof Developable developable) {
        total += developable.getBuildingLevel().getLevel();
      }
    }
    return total;
  }
}
