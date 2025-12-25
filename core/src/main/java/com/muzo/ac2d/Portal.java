package com.muzo.ac2d;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class Portal {
    public Rectangle bounds;
    public String targetMap;
    public float playerTimeInside;
    public boolean isPlayerInside;

    private static final float ACTIVATION_TIME = 1.0f;

    // Yapıcı fonksiyonu. Sadece gerekli şeyleri setliyor.
    public Portal(float x, float y, float width, float height, String targetMap) {
        this.bounds = new Rectangle(x, y, width, height);
        this.targetMap = targetMap;
        this.playerTimeInside = 0f;
        this.isPlayerInside = false;
    }

    // Portalın içinde durulan süreyi hesaplamaki çin
    public void update(float delta, Vector2 playerPos) {
        isPlayerInside = bounds.contains(playerPos.x, playerPos.y);

        if (isPlayerInside) {
            playerTimeInside += delta;
        } else {
            playerTimeInside = 0f;
        }
    }

    // Portalların timingleri için kullanılan fonksiyon.
    public boolean shouldActivate() {
        return isPlayerInside && playerTimeInside >= ACTIVATION_TIME;
    }

    // Mainde portal animasyonunda kullanılıyor
    public float getActivationProgress() {
        return Math.min(playerTimeInside / ACTIVATION_TIME, 1.0f);
    }
}
