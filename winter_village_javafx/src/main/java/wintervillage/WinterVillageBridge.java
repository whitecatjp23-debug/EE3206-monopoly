package wintervillage;

import javafx.scene.Node;
import oopgroup.WinterVillagePlayerHUD;
import wintervillage.cards.CardType;
import wintervillage.tokens.PlayerTokenLayer;

import java.util.List;

public class WinterVillageBridge implements WinterVillagePlayerHUD.WinterVillageUIBridge {

    private final WinterVillagePlayerHUD hud;
    private final Node board3D;
    private final PlayerTokenLayer tokenLayer;

    public WinterVillageBridge(WinterVillagePlayerHUD hud, Node board3D, PlayerTokenLayer tokenLayer) {
        this.hud = hud;
        this.board3D = board3D;
        this.tokenLayer = tokenLayer;
    }

    @Override
    public void onMatchStarted(List<WinterVillagePlayerHUD.Player> players) {
        if (tokenLayer != null) {
            tokenLayer.resetAll(false);
            for (int i = 0; i < tokenLayer.getPlayerCount(); i++) {
                tokenLayer.setTokenVisible(i, i < players.size());
            }
            tokenLayer.setTurnIndex(0);
        }
    }

    @Override
    public void onRollDiceRequested(WinterVillagePlayerHUD.Player activePlayer, int d1, int d2) {
        if (tokenLayer != null) {
            // ★ 關閉 3D 自動移動，避免在監獄時亂跑
            tokenLayer.setAutoMoveEnabled(false);
        }

        if (WinterVillageApp.currentView != null && WinterVillageApp.currentView.getDiceController() != null) {
            try {
                WinterVillageApp.currentView.getDiceController().roll(d1, d2);
            } catch (Exception e) {
                WinterVillageApp.currentView.getDiceController().rollRandom();
            }
        }
    }

    @Override
    public void onPlayerMoved(WinterVillagePlayerHUD.Player player, int fromTile, int toTile) {
        if (tokenLayer == null) return;
        int playerIndex = hud.getPlayers().indexOf(player);

        if (playerIndex >= 0 && playerIndex < tokenLayer.getPlayerCount()) {
            int forwardSteps = (toTile - fromTile + 40) % 40;
            // ★ 手動強制移動
            tokenLayer.forceMovePlayerSteps(playerIndex, forwardSteps);
        }
    }

    @Override
    public void onPlayerTeleported(WinterVillagePlayerHUD.Player player, int fromTile, int toTile) {
        if (tokenLayer == null) return;
        int playerIndex = hud.getPlayers().indexOf(player);
        if (playerIndex >= 0 && playerIndex < tokenLayer.getPlayerCount()) {
            tokenLayer.teleportPlayer(playerIndex, toTile);
        }
    }

    @Override
    public void onPlayerBankrupt(WinterVillagePlayerHUD.Player player) {
        if (tokenLayer == null) return;
        int playerIndex = hud.getPlayers().indexOf(player);
        if (playerIndex >= 0) tokenLayer.setTokenVisible(playerIndex, false);
    }

    @Override
    public void onDrawChanceCard() {
        if (WinterVillageApp.currentView != null && WinterVillageApp.currentView.getCardController() != null) {
            WinterVillageApp.currentView.getCardController().draw(CardType.ACTIVATION);
        }
    }

    @Override
    public void onDrawCommunityCard() {
        if (WinterVillageApp.currentView != null && WinterVillageApp.currentView.getCardController() != null) {
            WinterVillageApp.currentView.getCardController().draw(CardType.RESOURCE);
        }
    }

    @Override
    public void onPropertyPurchased(WinterVillagePlayerHUD.Player buyer, WinterVillagePlayerHUD.PropertyData property) {}
    @Override
    public void onBuildingUpdated(WinterVillagePlayerHUD.Player owner, WinterVillagePlayerHUD.PropertyData property, int newBuildingLevel) {}
    @Override
    public void onTradeExecuted(WinterVillagePlayerHUD.Player p1, WinterVillagePlayerHUD.Player p2) {}

    @Override
    public void onTurnEnded(WinterVillagePlayerHUD.Player nextPlayer) {
        if (tokenLayer != null) {
            tokenLayer.setTurnIndex(hud.getPlayers().indexOf(nextPlayer));
            tokenLayer.setAutoMoveEnabled(true); // ★ 回合結束，恢復自動移動
        }
    }
}