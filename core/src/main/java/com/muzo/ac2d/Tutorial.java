package com.muzo.ac2d;

import com.badlogic.gdx.math.Rectangle;

public class Tutorial {
    public Rectangle bounds;
    public String text;
    public boolean isShowing;

    public Tutorial(float x, float y, float width, float height, String text) {
        this.bounds = new Rectangle(x, y, width, height);
        this.text = text;
        this.isShowing = false;
    }
}
