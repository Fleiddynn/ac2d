package com.muzo.ac2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public class Arrow {
    public Body body;
    public static final float WIDTH = 0.3f;
    public static final float HEIGHT = 0.05f;

    private float speed = 15f;

    public boolean isStuck = false;

    public float stateTime = 0;
    public static final float MAX_LIFETIME = 5f;

    public enum Owner { PLAYER, ENEMY }
    public Owner owner = Owner.PLAYER;

    public Body embeddedBody = null;
    public Vector2 localOffset = new Vector2();
    public float localAngle = 0f;
    public boolean attachPending = false;

    public float whooshTimer = 0f;

    public Arrow(World world, float startX, float startY, Vector2 direction) {
        BodyDef bdef = new BodyDef();
        bdef.position.set(startX, startY);
        bdef.type = BodyDef.BodyType.DynamicBody;
        bdef.bullet = true;

        body = world.createBody(bdef);

        body.setFixedRotation(true);

        float angle = direction.angleRad();
        body.setTransform(body.getPosition(), angle);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(WIDTH / 2, HEIGHT / 2);

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.density = 0.5f;
        fdef.friction = 1f;
        fdef.restitution = 0.0f;

        fdef.filter.categoryBits = Main.CATEGORY_ARROW;
        fdef.filter.maskBits     = Main.MASK_ARROW;

        body.createFixture(fdef);
        shape.dispose();

        body.setLinearVelocity(direction.scl(speed));
    }
}
