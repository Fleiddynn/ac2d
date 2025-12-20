package com.muzo.ac2d;

import com.badlogic.gdx.math.Vector2;

public class SoundEvent {
    public enum Type { STEP, BOW_SHOT, ARROW_WHOOSH, ARROW_IMPACT, SUSPICIOUS }

    private static int NEXT_ID = 1;

    public final int id;
    public final Type type;
    public final Vector2 position = new Vector2();
    public final float strength; // relative loudness (Metreyle scalelenmesi için)
    public final float ttl;      // Kaç saniye duyuluyor ses
    public final float time;     // Dünya oluşturulduğundan beri geçen saniye

    public SoundEvent(Type type, Vector2 pos, float strength, float ttl, float timeSeconds) {
        this.id = NEXT_ID++;
        this.type = type;
        this.position.set(pos);
        this.strength = strength;
        this.ttl = ttl;
        this.time = timeSeconds;
    }
}
