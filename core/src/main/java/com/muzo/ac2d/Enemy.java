package com.muzo.ac2d;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

public class Enemy {
    public Body body;
    public Vector2 targetPosition;
    public float speed = 0.5f;     // Devriye hızı

    public int health = 1;
    public boolean isDead = false;

    public void takeDamage(int amount) {
        health -= amount;
        if (health <= 0) isDead = true;
    }

    // Düşmanın Durumları
    public enum State { PATROLLING, CHASE_SHOOT, SEARCHING }
    public State currentState = State.PATROLLING;

    public float detectionRange;
    public static final float MAX_VISION_RANGE = 5f;
    private Array<Vector2> patrolPoints;
    private int currentPointIndex = 0;
    private boolean waitingAtPoint = false;
    private float waitTimer = 0f;
    private float waitDuration = 0f;

    private float shootingCooldown = 1.0f;
    private float timeSinceLastShot = 0f;
    private World world;
    public static final float ENEMY_ARROW_SPEED = 5.0f;

    private boolean playerDetectedByOthers = false; // Diğer düşmanlardan gelen haber
    private float alertSeeTimer = 0f;
    private float alertBroadcastDelay = 0.7f;
    private boolean alertBroadcastReady = false;
    private boolean currentlySeeing = false;

    private Vector2 lastKnownPlayerPos = new Vector2();
    private float lastSeenTimer = 0f;
    private float searchTimeout = 8.0f;
    private float searchOverallTimer = 0f;
    private static final float SEARCH_TOTAL_TIMEOUT = 6.0f;
    private float investigateSpeed = 0.8f;
    private float sweepTimer = 0f;
    private float sweepDuration = 3.0f;
    private float sweepRadius = 1.2f;

    private Vector2 avoidTarget = null;
    private float avoidHoldTimer = 0f;

    private static final float STOP_RADIUS_PATROL = 0.08f;
    private static final float SLOW_RADIUS_PATROL = 0.7f;
    private static final float STOP_RADIUS_SEARCH = 0.1f;
    private static final float SLOW_RADIUS_SEARCH = 0.9f;

    private final Vector2 facingDir = new Vector2(1, 0);
    private float seeDecayRate = 0.5f;

    private float separationRadius = 0.6f;
    private float separationWeight = 0.8f;

    private Vector2 lastCorpseNoticed = new Vector2(Float.NaN, Float.NaN);
    private float corpseNoticeCooldown = 3f;
    private float corpseCooldownTimer = 0f;

    private final Vector2 corpseIgnorePos = new Vector2(Float.NaN, Float.NaN);
    private float corpseIgnoreTimer = 0f; // e.g., 12s cooldown on same corpse spot

    private float progressCheckTimer = 0f;
    private float lastDistToGoal = Float.MAX_VALUE;
    private float stuckTimer = 0f;
    private static final float PROGRESS_INTERVAL = 0.4f;
    private static final float PROGRESS_MIN_DELTA = 0.05f;
    private static final float STUCK_TIME = 1.2f;

    public Enemy(World world, float startX, float startY, float range, Array<Vector2> points) {
        this.world = world;
        float base = range > 0 ? range : 3.0f;
        this.detectionRange = Math.min(base, MAX_VISION_RANGE);
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

    // Ses dyuma kısmı
    public float hearingRadius = 5f;
    private float hearRetargetCooldown = 0.4f;
    private float hearRetargetTimer = 0f;
    private final IntSet processedSoundIds = new IntSet();

    public void considerSounds(Array<SoundEvent> sounds, float nowSeconds) {
        if (isDead) return;
        if (hearRetargetTimer > 0f) return;
        if (sounds == null || sounds.size == 0) return;
        Vector2 pos = body.getPosition();
        SoundEvent best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < sounds.size; i++) {
            SoundEvent s = sounds.get(i);
            if (processedSoundIds.contains(s.id)) continue;
            if (nowSeconds - s.time > s.ttl) continue;
            float dist = pos.dst(s.position);
            float maxHear = hearingRadius * Math.max(0.5f, s.strength);
            if (dist > maxHear) continue;
            float score = s.strength * 2f - (dist / Math.max(0.01f, maxHear));
            if (score > bestScore) { bestScore = score; best = s; }
        }
        if (best != null) {
            onHear(best);
            hearRetargetTimer = hearRetargetCooldown;
        }
    }

    public void onHear(SoundEvent s) {
        lastKnownPlayerPos.set(s.position);
        lastSeenTimer = 0f;
        searchOverallTimer = 0f;
        playerDetectedByOthers = true;
        if (currentState != State.SEARCHING) {
            currentState = State.SEARCHING;
            sweepTimer = 0f;
        }
        processedSoundIds.add(s.id);
    }

    public Arrow update(float delta, Player player) {
        State prevState = currentState;
        timeSinceLastShot += delta;
        if (corpseCooldownTimer > 0f) corpseCooldownTimer -= delta;
        if (corpseIgnoreTimer > 0f) corpseIgnoreTimer -= delta;
        if (hearRetargetTimer > 0f) hearRetargetTimer -= delta;

        Vector2 playerPos = player.body.getPosition();
        boolean sees = canSeePlayer(playerPos, player.isCrouching);

        if (sees) {
            float seeRate = player.isCrouching ? 0.6f : 1.0f;
            alertSeeTimer += delta * seeRate;
            lastKnownPlayerPos.set(playerPos);
            lastSeenTimer = 0f;
            if (alertSeeTimer >= alertBroadcastDelay) {
                alertBroadcastReady = true;
            }
            currentlySeeing = true;
        } else {
            alertSeeTimer = Math.max(0f, alertSeeTimer - delta * seeDecayRate);
            lastSeenTimer += delta;
            currentlySeeing = false;
        }

        boolean alerted = playerDetectedByOthers || sees;

        if (!alerted) {
            currentState = State.PATROLLING;
        } else if (sees) {
            currentState = State.CHASE_SHOOT;
        } else {
            currentState = State.SEARCHING;
        }

        if (currentState == State.SEARCHING && prevState != State.SEARCHING) {
            sweepTimer = 0f;
            searchOverallTimer = 0f;
        }

        if (currentState == State.PATROLLING) {
            patrol(delta);
            return null;
        } else if (currentState == State.CHASE_SHOOT) {
            return chaseAndShoot(playerPos);
        } else if (currentState == State.SEARCHING) {
            search(delta);
            searchOverallTimer += delta;
            if (lastSeenTimer >= searchTimeout || searchOverallTimer >= SEARCH_TOTAL_TIMEOUT) {
                playerDetectedByOthers = false;
                avoidTarget = null;
                sweepTimer = 0f;
                searchOverallTimer = 0f;
                progressCheckTimer = 0f;
                lastDistToGoal = Float.MAX_VALUE;
                stuckTimer = 0f;
                if (!Float.isNaN(lastCorpseNoticed.x) && !Float.isNaN(lastCorpseNoticed.y)) {
                    corpseIgnorePos.set(lastCorpseNoticed);
                    corpseIgnoreTimer = 12f;
                }
                currentState = State.PATROLLING;
            }
            return null;
        }
        return null;
    }

    public boolean isCurrentlySeeing() {
        return !isDead && currentlySeeing;
    }

    public float getSeeProgress() {
        if (isDead) return 0f;
        if (alertBroadcastDelay <= 0f) return currentlySeeing ? 1f : 0f;
        return MathUtils.clamp(alertSeeTimer / alertBroadcastDelay, 0f, 1f);
    }

    private void patrol(float delta) {
        if (patrolPoints == null || patrolPoints.size < 2) return;

        Vector2 currentPos = body.getPosition();

        float distToPoint = currentPos.dst(targetPosition);
        if (!waitingAtPoint && distToPoint <= STOP_RADIUS_PATROL) {
            waitingAtPoint = true;
            waitDuration = MathUtils.random(1f, 2f);
            waitTimer = waitDuration;
            avoidTarget = null;
            body.setLinearVelocity(0, 0);
            if (patrolPoints.size > 1) {
                int nextIdx = (currentPointIndex + 1) % patrolPoints.size;
                Vector2 next = patrolPoints.get(nextIdx);
                Vector2 dir = next.cpy().sub(currentPos);
                if (dir.len2() > 0.0001f) facingDir.set(dir).nor();
            }
        }
        if (waitingAtPoint) {
            waitTimer -= delta;
            body.setLinearVelocity(0, 0);
            applySeparation();
            if (waitTimer <= 0f) {
                waitingAtPoint = false;
                currentPointIndex = (currentPointIndex + 1) % patrolPoints.size;
                targetPosition = patrolPoints.get(currentPointIndex);
                avoidTarget = null;
                progressCheckTimer = 0f;
                lastDistToGoal = Float.MAX_VALUE;
                stuckTimer = 0f;
            }
            return;
        }

        Vector2 toTarget = targetPosition.cpy().sub(currentPos);
        boolean blocked = !hasLineOfSight(currentPos, targetPosition);
        if (blocked) {
            if (avoidTarget == null || currentPos.dst(avoidTarget) < 0.12f || avoidHoldTimer <= 0f) {
                Vector2 dir = toTarget.cpy().nor();
                Vector2 perp = new Vector2(-dir.y, dir.x);
                float mag = MathUtils.random(0.6f, 1.0f);
                boolean leftFirst = MathUtils.randomBoolean();
                Vector2 optionA = currentPos.cpy().add(perp.cpy().scl(mag));
                Vector2 optionB = currentPos.cpy().sub(perp.cpy().scl(mag));
                boolean aOk = hasLineOfSight(optionA, targetPosition);
                boolean bOk = hasLineOfSight(optionB, targetPosition);
                avoidTarget = (aOk && !bOk) ? optionA : (!aOk && bOk ? optionB : (leftFirst ? optionA : optionB));
                avoidHoldTimer = 0.6f;
            }
            avoidHoldTimer -= delta;
            moveArrive(avoidTarget, speed, STOP_RADIUS_PATROL, SLOW_RADIUS_PATROL);
            if (hasLineOfSight(currentPos, targetPosition) && currentPos.dst2(avoidTarget) > 0.25f) {
                avoidTarget = null;
            }
        } else {
            moveArrive(targetPosition, speed, STOP_RADIUS_PATROL, SLOW_RADIUS_PATROL);
            avoidTarget = null;
        }

        applySeparation();

        Vector2 goal = (avoidTarget != null) ? avoidTarget : targetPosition;
        float dist = currentPos.dst(goal);
        progressCheckTimer += delta;
        if (progressCheckTimer >= PROGRESS_INTERVAL) {
            float progressed = (lastDistToGoal == Float.MAX_VALUE) ? 0f : (lastDistToGoal - dist);
            if (progressed < PROGRESS_MIN_DELTA) {
                stuckTimer += progressCheckTimer;
            } else {
                stuckTimer = 0f;
            }
            lastDistToGoal = dist;
            progressCheckTimer = 0f;
        }
        if (stuckTimer >= STUCK_TIME) {
            Vector2 dir = toTarget.len2() > 0.0001f ? toTarget.cpy().nor() : new Vector2(facingDir);
            Vector2 perp = new Vector2(-dir.y, dir.x);
            avoidTarget = currentPos.cpy().add(perp.scl(MathUtils.random(-1f, 1f) >= 0 ? 0.9f : -0.9f));
            avoidHoldTimer = 0.9f;
            stuckTimer = 0f;
            lastDistToGoal = Float.MAX_VALUE;
        }
    }

    private Arrow chaseAndShoot(Vector2 playerPos) {
        Vector2 enemyPos = body.getPosition();
        Vector2 toPlayer = playerPos.cpy().sub(enemyPos);
        Vector2 dir = toPlayer.cpy().nor();

        float desiredDist = 1.5f;
        float dist = toPlayer.len();
        Vector2 lateral = new Vector2(-dir.y, dir.x);

        boolean hasLOS = hasLineOfSight(enemyPos, playerPos);
        if (!hasLOS) {
            Vector2 candidateLeft = enemyPos.cpy().add(lateral.scl(0.5f));
            Vector2 candidateRight = enemyPos.cpy().sub(lateral.scl(1f));
            boolean leftOk = hasLineOfSight(candidateLeft, playerPos);
            boolean rightOk = hasLineOfSight(candidateRight, playerPos);
            Vector2 target = leftOk ? candidateLeft : (rightOk ? candidateRight : playerPos);
            Vector2 vel = target.cpy().sub(enemyPos).nor().scl(investigateSpeed);
            body.setLinearVelocity(vel);
            if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
        } else {
            if (dist > desiredDist) {
                Vector2 vel = dir.cpy().scl(investigateSpeed);
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            } else if (dist < desiredDist * 0.7f) {
                Vector2 vel = dir.cpy().scl(-investigateSpeed);
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            } else {
                body.setLinearVelocity(0, 0);
            }
        }

        if (timeSinceLastShot >= shootingCooldown && hasLOS && alertSeeTimer >= alertBroadcastDelay) {
            Vector2 enemyPosNow = body.getPosition();
            Vector2 direction = playerPos.cpy().sub(enemyPosNow).nor();

            float startX = enemyPosNow.x + direction.x * 0.4f;
            float startY = enemyPosNow.y + direction.y * 0.4f;

            Arrow newArrow = new Arrow(world, startX, startY, direction);
            newArrow.owner = Arrow.Owner.ENEMY;
            newArrow.body.setUserData(newArrow);

            timeSinceLastShot = 0f;

            return newArrow;
        }
        return null;
    }

    private boolean canSeePlayer(Vector2 playerPos, boolean playerCrouching) {
        if (playerDetectedByOthers) {
            //
        }

        Vector2 enemyPos = body.getPosition();
        float distance = enemyPos.dst(playerPos);

        float baseRange = Math.min(detectionRange > 0 ? detectionRange : 3.0f, MAX_VISION_RANGE);
        float effectiveRange = baseRange * (playerCrouching ? 0.6f : 1f);
        if (distance > effectiveRange) return false;

        Vector2 lookDir = facingDir;

        Vector2 dirToPlayer = playerPos.cpy().sub(enemyPos).nor();
        float dot = MathUtils.clamp(lookDir.dot(dirToPlayer), -1f, 1f);
        float angle = MathUtils.acos(dot) * MathUtils.radiansToDegrees;

        if (angle > 45f) return false;

        final boolean[] hasObstacle = {false};
        world.rayCast(new RayCastCallback() {
            @Override
            public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
                if (fixture.getFilterData().categoryBits == Main.CATEGORY_WALL) {
                    hasObstacle[0] = true;
                    return 0;
                }
                return 1;
            }
        }, enemyPos, playerPos);

        return !hasObstacle[0];
    }

    public boolean updateDetection(Vector2 playerPos) {
        return canSeePlayer(playerPos, false);
    }

    public void setAlert(boolean alert) {
        this.playerDetectedByOthers = alert;
        if (!alert) {
            alertSeeTimer = 0f;
            lastSeenTimer = 0f;
        }
    }

    public boolean consumeAlertBroadcast() {
        if (alertBroadcastReady) {
            alertBroadcastReady = false;
            return true;
        }
        return false;
    }

    private void search(float delta) {
        Vector2 currentPos = body.getPosition();
        Vector2 toLast = lastKnownPlayerPos.cpy().sub(currentPos);
        Vector2 desiredTarget;
        if (toLast.len() > STOP_RADIUS_SEARCH) {
            desiredTarget = lastKnownPlayerPos;
            sweepTimer = 0f;
        } else {
            sweepTimer += delta;
            float t = (sweepDuration <= 0f) ? 1f : (sweepTimer / sweepDuration);
            float angle = (t % 1f) * MathUtils.PI2;
            desiredTarget = new Vector2(MathUtils.cos(angle), MathUtils.sin(angle)).scl(sweepRadius).add(lastKnownPlayerPos);
        }

        boolean blocked = !hasLineOfSight(currentPos, desiredTarget);
        if (blocked) {
            if (avoidTarget == null || currentPos.dst(avoidTarget) < 0.12f || avoidHoldTimer <= 0f) {
                Vector2 dir = desiredTarget.cpy().sub(currentPos);
                if (dir.len2() < 0.0001f) dir.set(facingDir);
                dir.nor();
                Vector2 perp = new Vector2(-dir.y, dir.x);
                float mag = MathUtils.random(0.5f, 0.9f);
                boolean leftFirst = MathUtils.randomBoolean();
                Vector2 optionA = currentPos.cpy().add(perp.cpy().scl(mag));
                Vector2 optionB = currentPos.cpy().sub(perp.cpy().scl(mag));
                boolean aOk = hasLineOfSight(optionA, desiredTarget);
                boolean bOk = hasLineOfSight(optionB, desiredTarget);
                avoidTarget = (aOk && !bOk) ? optionA : (!aOk && bOk ? optionB : (leftFirst ? optionA : optionB));
                avoidHoldTimer = 0.5f;
            }
            avoidHoldTimer -= delta;
            moveArrive(avoidTarget, investigateSpeed, 0.06f, 0.6f);
            if (hasLineOfSight(currentPos, desiredTarget) && currentPos.dst2(avoidTarget) > 0.25f) {
                avoidTarget = null;
            }
        } else {
            moveArrive(desiredTarget, investigateSpeed, STOP_RADIUS_SEARCH, SLOW_RADIUS_SEARCH);
            avoidTarget = null;
        }

        applySeparation();
    }

    private void moveArrive(Vector2 target, float maxSpeed, float stopRadius, float slowRadius) {
        Vector2 pos = body.getPosition();
        Vector2 to = target.cpy().sub(pos);
        float dist = to.len();
        if (dist <= stopRadius) {
            body.setLinearVelocity(0, 0);
            return;
        }
        Vector2 dir = (dist > 0.0001f) ? to.scl(1f / dist) : new Vector2();
        float speedFactor = MathUtils.clamp((dist - stopRadius) / Math.max(0.0001f, (slowRadius - stopRadius)), 0.2f, 1f);
        Vector2 vel = dir.scl(maxSpeed * speedFactor);
        body.setLinearVelocity(vel);
        if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
    }

    private boolean hasLineOfSight(Vector2 from, Vector2 to) {
        final boolean[] blocked = {false};
        world.rayCast(new RayCastCallback() {
            @Override
            public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
                if (fixture.getFilterData().categoryBits == Main.CATEGORY_WALL) {
                    blocked[0] = true;
                    return 0;
                }
                return 1;
            }
        }, from, to);
        return !blocked[0];
    }

    public boolean canSeeCorpse(Vector2 corpsePos) {
        Vector2 enemyPos = body.getPosition();
        float distance = enemyPos.dst(corpsePos);
        float effectiveRange = Math.min(detectionRange > 0 ? detectionRange : 3.0f, MAX_VISION_RANGE);
        if (distance > effectiveRange) return false;

        Vector2 lookDir = body.getLinearVelocity().cpy().nor();
        if (lookDir.len() == 0 && targetPosition != null) lookDir.set(targetPosition).sub(enemyPos).nor();
        Vector2 dirToCorpse = corpsePos.cpy().sub(enemyPos).nor();
        float dot = MathUtils.clamp(lookDir.dot(dirToCorpse), -1f, 1f);
        float angle = MathUtils.acos(dot) * MathUtils.radiansToDegrees;
        if (angle > 60f) return false; // a bit wider tolerance for spotting corpses
        return hasLineOfSight(enemyPos, corpsePos);
    }

    public void noticeCorpse(Vector2 corpsePos) {
        if (corpseIgnoreTimer > 0f && !Float.isNaN(corpseIgnorePos.x) && !Float.isNaN(corpseIgnorePos.y) && corpseIgnorePos.dst2(corpsePos) < 0.04f) {
            return;
        }
        if (corpseCooldownTimer > 0f && !Float.isNaN(lastCorpseNoticed.x) && !Float.isNaN(lastCorpseNoticed.y) && lastCorpseNoticed.dst2(corpsePos) < 0.01f) {
            return;
        }
        lastCorpseNoticed.set(corpsePos);
        corpseCooldownTimer = corpseNoticeCooldown;
        lastKnownPlayerPos.set(corpsePos);
        if (currentState != State.SEARCHING) {
            lastSeenTimer = 0f;
            searchOverallTimer = 0f;
        }
        playerDetectedByOthers = true;
        if (currentState != State.SEARCHING) {
            currentState = State.SEARCHING;
            sweepTimer = 0f;
        }
    }

    public void noticeCorpse(Vector2 corpsePos, Vector2 arrowDir) {
        if (corpseIgnoreTimer > 0f && !Float.isNaN(corpseIgnorePos.x) && !Float.isNaN(corpseIgnorePos.y) && corpseIgnorePos.dst2(corpsePos) < 0.04f) {
            return;
        }
        if (corpseCooldownTimer > 0f && !Float.isNaN(lastCorpseNoticed.x) && !Float.isNaN(lastCorpseNoticed.y) && lastCorpseNoticed.dst2(corpsePos) < 0.01f) {
            return;
        }
        lastCorpseNoticed.set(corpsePos);
        corpseCooldownTimer = corpseNoticeCooldown;
        Vector2 dir = arrowDir.cpy();
        if (dir.len2() == 0) dir.set(1, 0);
        dir.nor();
        lastKnownPlayerPos.set(corpsePos.cpy().sub(dir.scl(2f)));
        if (currentState != State.SEARCHING) {
            lastSeenTimer = 0f;
            searchOverallTimer = 0f;
        }
        playerDetectedByOthers = true;
        if (currentState != State.SEARCHING) {
            currentState = State.SEARCHING;
            sweepTimer = 0f;
        }
    }

    private void applySeparation() {
        Vector2 selfPos = body.getPosition();
        final Array<Body> neighbors = new Array<Body>();
        float r = separationRadius;
        world.QueryAABB(new QueryCallback() {
            @Override
            public boolean reportFixture(Fixture fixture) {
                if (fixture.getFilterData().categoryBits == Main.CATEGORY_ENEMY) {
                    Body b = fixture.getBody();
                    if (b != body && !neighbors.contains(b, true)) neighbors.add(b);
                }
                return true;
            }
        }, selfPos.x - r, selfPos.y - r, selfPos.x + r, selfPos.y + r);

        if (neighbors.size == 0) return;
        Vector2 push = new Vector2();
        for (int i = 0; i < neighbors.size; i++) {
            Body nb = neighbors.get(i);
            Vector2 np = nb.getPosition();
            float d = selfPos.dst(np);
            if (d < 0.0001f || d > separationRadius) continue;
            Vector2 away = selfPos.cpy().sub(np).nor().scl((separationRadius - d) / separationRadius);
            push.add(away);
        }
        if (push.len2() > 0.0001f) {
            push.scl(separationWeight);
            Vector2 newVel = body.getLinearVelocity().cpy().add(push);
            if (newVel.len() > investigateSpeed) newVel.nor().scl(investigateSpeed);
            body.setLinearVelocity(newVel);
            if (newVel.len2() > 0.0001f) facingDir.set(newVel).nor();
        }
    }
}
