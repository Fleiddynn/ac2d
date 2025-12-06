package com.muzo.ac2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public class Player {
    public Body body;
    private float speed = 1.5f; // Hareket hızı (Metre/Saniye)
    public float visualRotation;
    public static final float RADIUS = 0.1f;

    public Player(World world, float x, float y) {
        // Box2D
        BodyDef bdef = new BodyDef();
        bdef.position.set(x, y);
        bdef.type = BodyDef.BodyType.DynamicBody;

        body = world.createBody(bdef);
        body.setFixedRotation(true);

        // Collision
        CircleShape shape = new CircleShape();
        // Yarıçap (0.5 Metre = 50 Piksel)
        shape.setRadius(RADIUS);

        // Fixture
        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.density = 1.0f;
        fdef.friction = 0.5f;

        body.createFixture(fdef);

        shape.dispose();
    }

    public void update() {
        handleInput();
    }

    private void handleInput() {
        Vector2 velocity = new Vector2(0, 0);

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            velocity.y = speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            velocity.y = -speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            velocity.x = -speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            velocity.x = speed;
        }

        // Normalize
        if (velocity.len() > speed) {
            velocity.nor().scl(speed);
        }

        body.setLinearVelocity(velocity);
    }

    public void setVisualRotation(float degrees) {
        this.visualRotation = degrees;
    }
}
