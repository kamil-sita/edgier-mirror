package pl.sita.edgymirror;

import com.badlogic.gdx.graphics.Color;

public class GameObject {

    private Color color;
    private final Type type;

    public GameObject(Color color, Type type) {
        this.color = color;
        this.type = type;
    }

    public Color getColor(Color activeSpawnColor, Color inactiveSpawnColor, boolean isSpawnCoord) {
        if (getType() == Type.SPAWN) {
            if (isSpawnCoord) {
                return activeSpawnColor;
            } else {
                return inactiveSpawnColor;
            }
        }

        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        BLOCK,
        SPAWN
    }

}
