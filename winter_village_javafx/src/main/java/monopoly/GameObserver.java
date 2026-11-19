/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public interface GameObserver {
    void onGameStateChanged();
    void onLogMessage(String message);
    void onPlayerPrompt(String title, String message);
}
    

