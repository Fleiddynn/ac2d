package com.muzo.ac2d;

import com.badlogic.gdx.math.Vector2;

public class Trap {
    public Vector2 position;
    public boolean isTriggered = false;
    public float damage = 1f; // Düşmana verilen hasar

    public Trap(float x, float y) {
        // Tuzağın dünyadaki konumu
        this.position = new Vector2(x, y);
    }
}
