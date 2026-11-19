/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package monopoly;

/**
 *
 * @author joe
 */
public abstract class Cell implements Actionable {
    private final int index;
    private final String name;

    protected Cell(int index, String name) {
        if (index < 0 || index >= 40) {
            throw new IllegalArgumentException("Board index must be between 0 and 39.");
        }
        this.index = index;
        this.name = name;
    }

    public final int getIndex() {
        return index;
    }

    public final String getName() {
        return name;
    }

    @Override
    public abstract void landOn(Player player);

    @Override
    public String toString() {
        return String.format("[%02d] %s", index, name);
    }
}
