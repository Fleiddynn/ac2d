package com.muzo.ac2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;

public class Enemy {
    public Body body;
    public Vector2 targetPosition;
    public float speed = 0.5f;     // Devriye hızı

    // Düşmanın Durumları
    public enum State { PATROLLING, CHASE_SHOOT }
    public State currentState = State.PATROLLING;

    public float detectionRange;
    private Array<Vector2> patrolPoints;
    private int currentPointIndex = 0;

    private float shootingCooldown = 1.0f;
    private float timeSinceLastShot = 0f;
    private World world;
    public static final float ENEMY_ARROW_SPEED = 5.0f;

    public Enemy(World world, float startX, float startY, float range, Array<Vector2> points) {
        this.world = world;
        this.detectionRange = 2f;
        this.patrolPoints = points;

        float ENEMY_RADIUS = 0.1f;

        BodyDef bdef = new BodyDef();
        bdef.type = BodyDef.BodyType.DynamicBody;
        bdef.position.set(startX, startY);
        bdef.fixedRotation = true;

        body = world.createBody(bdef);

        CircleShape shape = new CircleShape();
        shape.setRadius(ENEMY_RADIUS);

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.density = 1.0f;
        fdef.friction = 0.5f;
        fdef.restitution = 0.0f;

        // Çarpışma Filtreleri
        fdef.filter.categoryBits = Main.CATEGORY_ENEMY;
        fdef.filter.maskBits = Main.MASK_ENEMY;

        body.createFixture(fdef);
        shape.dispose();

        body.setUserData(this);

        if (patrolPoints != null && patrolPoints.size > 0) {
            targetPosition = patrolPoints.get(currentPointIndex);
        } else {
            targetPosition = new Vector2(startX, startY);
        }
    }

    public Arrow update(float delta, Vector2 playerPos) {
        float distanceToPlayer = body.getPosition().dst(playerPos);
        timeSinceLastShot += delta;

        if (distanceToPlayer <= detectionRange) {
            currentState = State.CHASE_SHOOT;
        } else {
            currentState = State.PATROLLING;
        }

        if (currentState == State.PATROLLING) {
            patrol(delta);
            return null;
        } else if (currentState == State.CHASE_SHOOT) {
            return chaseAndShoot(playerPos);
        }
        return null;
    }

    private void patrol(float delta) {
        if (patrolPoints == null || patrolPoints.size < 2) return;

        Vector2 currentPos = body.getPosition();

        if (currentPos.dst(targetPosition) < 0.1f) {
            currentPointIndex = (currentPointIndex + 1) % patrolPoints.size;
            targetPosition = patrolPoints.get(currentPointIndex);
        }

        Vector2 velocity = targetPosition.cpy().sub(currentPos).nor().scl(speed);
        body.setLinearVelocity(velocity);
    }

    private Arrow chaseAndShoot(Vector2 playerPos) {
        body.setLinearVelocity(0, 0);

        if (timeSinceLastShot >= shootingCooldown) {

            Vector2 enemyPos = body.getPosition();
            Vector2 direction = playerPos.cpy().sub(enemyPos).nor();

            float startX = enemyPos.x + direction.x * 0.4f;
            float startY = enemyPos.y + direction.y * 0.4f;

            Arrow newArrow = new Arrow(world, startX, startY, direction);
            newArrow.body.setUserData(newArrow);

            timeSinceLastShot = 0f;

            return newArrow;
        }
        return null;
    }


}
