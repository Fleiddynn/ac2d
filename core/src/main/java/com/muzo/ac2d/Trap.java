package com.muzo.ac2d;

import com.badlogic.gdx.math.Vector2;

public class Trap {
    public Vector2 position;
    public boolean isTriggered = false;
    public int damage = 1;

    public Trap(float x, float y) {
        this.position = new Vector2(x, y);
    }
}
