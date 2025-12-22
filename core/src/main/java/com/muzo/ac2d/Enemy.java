package com.muzo.ac2d;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

public class Enemy {
    // Klasik değişkenler.
    public Body body;
    public Vector2 targetPosition;
    public float speed = 0.5f;
    public int health = 1;
    public boolean isDead = false;
    private World world;

    // Düşmanın state machineini ayarlamak için. Varsayılan olarka patrolling oluyor.
    public enum State {PATROLLING, SUSPICIOUS, CHASE_SHOOT, SEARCHING}
    public State currentState = State.PATROLLING;

    // Oyuncuyu fark etme değişkenleri
    public float detectionRange;
    public static final float MAX_VISION_RANGE = 5f;
    private final Vector2 facingDir = new Vector2(1, 0);
    private float seeDecayRate = 0.5f;
    private boolean playerDetectedByOthers = false;
    private float alertSeeTimer = 0f;
    private float alertBroadcastDelay = 0.7f;
    private boolean alertBroadcastReady = false;
    private boolean currentlySeeing = false;

    // Patrollar için değişkenler
    private Array<Vector2> patrolPoints;
    private int currentPointIndex = 0;
    private boolean waitingAtPoint = false;
    private float waitTimer = 0f;

    //
    private float waitDuration = 0f;
    private float suspiciousTimer = 0f;
    private static final float STOP_RADIUS_SEARCH = 0.1f;
    private static final float SLOW_RADIUS_SEARCH = 0.9f;
    private final Vector2 corpseIgnorePos = new Vector2(Float.NaN, Float.NaN);
    private Vector2 avoidTarget = null;
    private float corpseIgnoreTimer = 0f;
    private float progressCheckTimer = 0f;

    // Ok atma için değişkenler
    private float shootingCooldown = 1.0f;
    private float timeSinceLastShot = 0f;


    // Playerı araştırma için değişkenler
    private Vector2 lastKnownPlayerPos = new Vector2();
    private float lastSeenTimer = 0f;
    private float searchTimeout = 8.0f;
    private float searchOverallTimer = 0f;
    private static final float SEARCH_TOTAL_TIMEOUT = 6.0f;
    private float investigateSpeed = 0.8f;
    private float sweepTimer = 0f;
    private float sweepDuration = 3.0f;

    // Cesetlerle için değişkenler
    private Vector2 lastCorpseNoticed = new Vector2(Float.NaN, Float.NaN);
    private float corpseNoticeCooldown = 3f;
    private float corpseCooldownTimer = 0f;
    private static final IntSet investigatedCorpseIds = new IntSet();


    // Hareket ve yol bulma
    private float separationRadius = 0.6f;
    private float separationWeight = 0.8f;
    private float lastDistToGoal = Float.MAX_VALUE;
    private float stuckTimer = 0f;

    // Ses duyma için değişkenler
    public float hearingRadius = 5f;
    private float hearRetargetCooldown = 0.3f;
    private float hearRetargetTimer = 0f;
    private final IntSet processedSoundIds = new IntSet();

    // A* Pathfinding değişkenleri
    private Array<Vector2> currentPath = null;
    private int currentPathIndex = 0;
    private float pathRecalcTimer = 0f;
    private static final float PATH_RECALC_INTERVAL = 0.5f;
    private static final float PATH_NODE_REACH_DIST = 0.15f;


    // Düşmanlar için yapıcı fonksiyon. Spawn pozisyonu patrol pointleri vb. ayarlıyo.
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
        fdef.friction = 0.0f;
        fdef.restitution = 0.0f;

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

    // Playerdada var olan düşmanların damage yedirten fonksiyon. Ok yiyince çağırılıyor.
    public void takeDamage(int amount) {
        health -= amount;
        if (health <= 0) isDead = true;
        Filter filter = body.getFixtureList().first().getFilterData();
        filter.maskBits = Main.CATEGORY_WALL;
        body.getFixtureList().first().setFilterData(filter);
    }

    // Haritadaki ses olaylarını dinleyen fonksiyon. Düşmanın sesi duyabilip duymadığına karar verir.
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
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best != null) {
            onHear(best);
            hearRetargetTimer = hearRetargetCooldown;
        }
    }

    // Eğer ses duyarsa düşmanı searching moduna sokup pathe ses duyduğu yeri ekliyor.
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
        currentPath = null;
    }

    // Düşmanın her framede çalışan kodu. Ne yapacağına karar verir timerları ayarlar vb.
    public Arrow update(float delta, Player player) {
        State prevState = currentState;
        timeSinceLastShot += delta;
        pathRecalcTimer += delta;
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

        boolean alerted = playerDetectedByOthers;
        boolean barFull = alertSeeTimer >= alertBroadcastDelay;

        if (sees && barFull) {
            currentState = State.CHASE_SHOOT;
        } else if (sees && !barFull) {
            currentState = State.SUSPICIOUS;
        } else if (alerted && !sees) {
            currentState = State.SEARCHING;
        } else if (!sees && !alerted) {
            if (currentState != State.SEARCHING) {
                if (currentState != State.PATROLLING) {
                    currentState = State.PATROLLING;
                    targetPosition = patrolPoints.get(currentPointIndex);
                    currentPath = null;
                }
            }
        }

        if (currentState == State.SEARCHING && prevState != State.SEARCHING) {
            sweepTimer = 0f;
            searchOverallTimer = 0f;
        }

        if (currentState == State.PATROLLING) {
            patrol(delta);
            return null;
        } else if (currentState == State.SUSPICIOUS) {
            suspiciousTimer += delta;
            followSuspicious(delta, playerPos);
            return null;
        } else if (currentState == State.CHASE_SHOOT) {
            return chaseAndShoot(delta, playerPos);
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
                currentPath = null;
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

    // Oyuncu görüş alanında varsa ona doğru gider.
    private void followSuspicious(float delta, Vector2 playerPos) {
        Vector2 enemyPos = body.getPosition();
        Vector2 toPlayer = playerPos.cpy().sub(enemyPos);
        Vector2 dir = toPlayer.cpy().nor();

        float desiredDist = 2.0f;
        float dist = toPlayer.len();

        boolean hasLOS = hasLineOfSight(enemyPos, playerPos);

        if (!hasLOS) {
            if (pathRecalcTimer >= PATH_RECALC_INTERVAL || currentPath == null) {
                currentPath = findPath(enemyPos, playerPos);
                currentPathIndex = 0;
                pathRecalcTimer = 0f;
            }

            if (currentPath != null && currentPath.size > 0) {
                followPath(delta);
            } else {
                Vector2 vel = dir.scl(investigateSpeed * 0.7f); // Daha yavaş
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            }
        } else {
            currentPath = null;
            if (dist > desiredDist) {
                Vector2 vel = dir.cpy().scl(investigateSpeed * 0.7f);
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            } else if (dist < desiredDist * 0.8f) {
                Vector2 vel = dir.cpy().scl(-investigateSpeed * 0.5f);
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            } else {
                body.setLinearVelocity(0, 0);
            }

            facingDir.set(dir);
        }

        applySeparation();
    }

    // Mainde çizim için ihtiyacımız olan bilgiyi döndüren fonskyion.
    public boolean isCurrentlySeeing() {
        return !isDead && currentlySeeing;
    }

    // Mainde düşmanın üstündeki görme barını çizmek için ihtiyacımız olan bilgiyi dödnürr.
    public float getSeeProgress() {
        if (isDead) return 0f;
        if (alertBroadcastDelay <= 0f) return currentlySeeing ? 1f : 0f;
        return MathUtils.clamp(alertSeeTimer / alertBroadcastDelay, 0f, 1f);
    }

    // Düşmanın patrol durumundaki pathingini ayarlayan fonksiyon.
    private void patrol(float delta) {
        if (patrolPoints == null || patrolPoints.size < 2) return;

        Vector2 currentPos = body.getPosition();
        float distToPoint = currentPos.dst(targetPosition);

        if (!waitingAtPoint && distToPoint <= 0.15f) {
            waitingAtPoint = true;
            waitDuration = MathUtils.random(1.5f, 3f);
            waitTimer = waitDuration;
            body.setLinearVelocity(0, 0);

            int nextIdx = (currentPointIndex + 1) % patrolPoints.size;
            Vector2 next = patrolPoints.get(nextIdx);
            Vector2 dir = next.cpy().sub(currentPos);
            if (dir.len2() > 0.0001f) facingDir.set(dir).nor();

            return;
        }

        if (waitingAtPoint) {
            waitTimer -= delta;
            body.setLinearVelocity(0, 0);
            if (waitTimer <= 0f) {
                waitingAtPoint = false;
                currentPointIndex = (currentPointIndex + 1) % patrolPoints.size;
                targetPosition = patrolPoints.get(currentPointIndex);

                currentPath = null;
                currentPathIndex = 0;
                pathRecalcTimer = 0.6f;
            }
            return;
        }

        if (currentPath == null || pathRecalcTimer >= PATH_RECALC_INTERVAL) {
            currentPath = findPath(currentPos, targetPosition);
            currentPathIndex = 0;
            pathRecalcTimer = 0f;
        }

        if (currentPath != null && currentPath.size > 0) {
            followPath(delta);
        } else {
            moveArrive(targetPosition, speed, 0.1f, 0.5f);
        }

        applySeparation();

        progressCheckTimer += delta;
        if (progressCheckTimer >= 0.5f) {
            float distToGoal = currentPos.dst(targetPosition);

            if (!waitingAtPoint && body.getLinearVelocity().len() > 0.1f) {
                if (Math.abs(lastDistToGoal - distToGoal) < 0.05f) {
                    stuckTimer += 0.5f;
                } else {
                    stuckTimer = 0f;
                }
            }
            lastDistToGoal = distToGoal;
            progressCheckTimer = 0f;
        }

        if (stuckTimer >= 1.0f) {
            currentPath = null;
            stuckTimer = 0f;

            float randomAngle = MathUtils.random(0, 360);
            Vector2 jitter = new Vector2(0.5f, 0).setAngleDeg(randomAngle);

            body.setLinearVelocity(jitter);
            body.applyLinearImpulse(jitter.scl(0.1f), body.getWorldCenter(), true);
        }
    }

    // Düşman playerı görüyorsa kovalayıp ateş etmesine yarayan fonksiyon.
    private Arrow chaseAndShoot(float delta, Vector2 playerPos) {
        Vector2 enemyPos = body.getPosition();
        Vector2 toPlayer = playerPos.cpy().sub(enemyPos);
        Vector2 dir = toPlayer.cpy().nor();

        float desiredDist = 1.5f;
        float dist = toPlayer.len();
        Vector2 lateral = new Vector2(-dir.y, dir.x);

        boolean hasLOS = hasLineOfSight(enemyPos, playerPos);

        if (!hasLOS) {
            if (pathRecalcTimer >= PATH_RECALC_INTERVAL || currentPath == null) {
                currentPath = findPath(enemyPos, playerPos);
                currentPathIndex = 0;
                pathRecalcTimer = 0f;
            }

            if (currentPath != null && currentPath.size > 0) {
                followPath(delta);
            } else {
                Vector2 candidateLeft = enemyPos.cpy().add(lateral.scl(0.5f));
                Vector2 candidateRight = enemyPos.cpy().sub(lateral.scl(1f));
                boolean leftOk = hasLineOfSight(candidateLeft, playerPos);
                boolean rightOk = hasLineOfSight(candidateRight, playerPos);
                Vector2 target = leftOk ? candidateLeft : (rightOk ? candidateRight : playerPos);
                Vector2 vel = target.cpy().sub(enemyPos).nor().scl(investigateSpeed);
                body.setLinearVelocity(vel);
                if (vel.len2() > 0.0001f) facingDir.set(vel).nor();
            }
        } else {
            currentPath = null;
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

            facingDir.set(dir);
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

    // Düşmanın playerı görüp görmediğinin bilgisini döndüren fonksiyon. Update'de çağırıyoruz.
    private boolean canSeePlayer(Vector2 playerPos, boolean playerCrouching) {
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

    // Düşmanlardan biri playerı görürse diğerlerine haber veriyor.
    public void setAlert(boolean alert) {
        this.playerDetectedByOthers = alert;
        if (!alert) {
            alertSeeTimer = 0f;
            lastSeenTimer = 0f;
        }
    }

    // Bir alertı sadece bi kere çalıştırmak için fonksyion. yoksa sürekli alert olup buga giriyorlar.
    public boolean consumeAlertBroadcast() {
        if (alertBroadcastReady) {
            alertBroadcastReady = false;
            return true;
        }
        return false;
    }

    // Player'ı aramak için fonksiyon. Geliştirilmesi lazım.
    private void search(float delta) {
        Vector2 currentPos = body.getPosition();
        Vector2 toTarget = lastKnownPlayerPos.cpy().sub(currentPos);
        float distToTarget = toTarget.len();

        if (distToTarget > STOP_RADIUS_SEARCH) {
            if (pathRecalcTimer >= PATH_RECALC_INTERVAL || currentPath == null) {
                currentPath = findPath(currentPos, lastKnownPlayerPos);
                currentPathIndex = 0;
                pathRecalcTimer = 0f;
            }

            if (currentPath != null && currentPath.size > 0) {
                followPath(delta);
            } else {
                moveArrive(lastKnownPlayerPos, investigateSpeed, STOP_RADIUS_SEARCH, SLOW_RADIUS_SEARCH);
            }
            sweepTimer = 0f;
        } else {
            body.setLinearVelocity(0, 0);
            sweepTimer += delta;

            float angle = (sweepTimer * 2f) % MathUtils.PI2;
            facingDir.set(MathUtils.cos(angle), MathUtils.sin(angle));

            if (sweepTimer >= sweepDuration) {
                endSearch();
            }
        }
        applySeparation();
    }

    // Search bittiğinde değişkenleri sıfırlayıp patrola geri dönüyoruz.
    private void endSearch() {
        playerDetectedByOthers = false;
        currentState = State.PATROLLING;
        searchOverallTimer = 0f;
        sweepTimer = 0f;
        currentPath = null;

        waitingAtPoint = false;
        stuckTimer = 0f;
        progressCheckTimer = 0f;
        lastDistToGoal = Float.MAX_VALUE;

        if (patrolPoints != null && patrolPoints.size > 0) {
            targetPosition = patrolPoints.get(currentPointIndex);
        }

        body.setLinearVelocity(0, 0);
    }

    // Düşmanı bi yere gitmesini sağlayan fonksiyon
    private void moveArrive(Vector2 target, float maxSpeed, float stopRadius, float slowRadius) {
        Vector2 pos = body.getPosition();
        Vector2 to = target.cpy().sub(pos);
        float dist = to.len();

        if (dist <= stopRadius) {
            body.setLinearVelocity(0, 0);
            return;
        }

        Vector2 desiredVelocity = to.nor().scl(maxSpeed);

        body.setAwake(true);

        Vector2 velocityChange = desiredVelocity.sub(body.getLinearVelocity());
        body.applyForceToCenter(velocityChange.scl(body.getMass() * 10), true);

        if (body.getLinearVelocity().len() > maxSpeed) {
            body.setLinearVelocity(body.getLinearVelocity().nor().scl(maxSpeed));
        }

        if (body.getLinearVelocity().len2() > 0.001f) {
            facingDir.set(body.getLinearVelocity()).nor();
        }
    }

    // Düşmandan raycast atarak bi yeri görüp görmediğinin bilgisini aldığımız fonksiyon.
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

        if (blocked[0]) return false;
        return true;
    }

    // Düşmanın konumundan başka bi konuma gitmesi için path yollarını bulan A* Learning benzeri bi fonksiyon.
    private Array<Vector2> findPath(Vector2 start, Vector2 goal) {
        Array<Vector2> path = new Array<Vector2>();

        if (start.dst(goal) < 0.2f) {
            path.add(goal);
            return path;
        }

        if (hasLineOfSight(start, goal)) {
            path.add(goal);
            return path;
        }

        Vector2 dir = goal.cpy().sub(start).nor();
        float totalDist = start.dst(goal);
        float stepSize = 0.5f;

        Vector2 current = start.cpy();
        for (float d = stepSize; d < totalDist; d += stepSize) {
            Vector2 testPoint = start.cpy().add(dir.cpy().scl(d));

            if (hasLineOfSight(current, testPoint)) {
                if (hasLineOfSight(testPoint, goal)) {
                    path.add(goal);
                    return path;
                }
                path.add(testPoint.cpy());
                current = testPoint;
            } else {
                Vector2 perp = new Vector2(-dir.y, dir.x);
                Vector2 left = testPoint.cpy().add(perp.cpy().scl(0.8f));
                Vector2 right = testPoint.cpy().sub(perp.cpy().scl(0.8f));

                if (hasLineOfSight(current, left) && hasLineOfSight(left, goal)) {
                    path.add(left);
                    path.add(goal);
                    return path;
                } else if (hasLineOfSight(current, right) && hasLineOfSight(right, goal)) {
                    path.add(right);
                    path.add(goal);
                    return path;
                }
            }
        }

        if (path.size == 0) return null;
        path.add(goal);
        return path;
    }

    // Path listesindeki yerlere vararak gitmesini sağlayan kod.
    private void followPath(float delta) {
        if (currentPath == null || currentPath.size == 0) return;

        Vector2 currentPos = body.getPosition();
        Vector2 targetNode = currentPath.get(Math.min(currentPathIndex, currentPath.size - 1));

        float dist = currentPos.dst(targetNode);
        if (dist < PATH_NODE_REACH_DIST) {
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size) {
                currentPath = null;
                body.setLinearVelocity(0, 0);
                return;
            }
            targetNode = currentPath.get(currentPathIndex);
        }

        float moveSpeed = (currentState == State.PATROLLING) ? speed : investigateSpeed;
        moveArrive(targetNode, moveSpeed, 0.05f, 0.3f);
    }

    // Bi düşmanın ceset görüp görmediğinin bilgisini veren fonksiyon.
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
        if (angle > 60f) return false;
        return hasLineOfSight(enemyPos, corpsePos);
    }

    // Bi ceset görüldüğünde olması gereken olayları ayarlayan fonksiyon. Daha melee atak yapılmadığı için bi tanesi kullanılmıyor şuanlık.
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
        currentPath = null;
    }

    public void noticeCorpse(Enemy deadEnemy, Vector2 arrowDir) {
        Vector2 corpsePos = deadEnemy.body.getPosition();

        if (deadEnemy.isDead && investigatedCorpseIds.contains(deadEnemy.hashCode())) return;

        lastKnownPlayerPos.set(corpsePos);
        playerDetectedByOthers = true;
        currentState = State.SEARCHING;

        float distToCorpse = body.getPosition().dst(corpsePos);
        if (distToCorpse < 1.5f) {
            lastKnownPlayerPos.set(corpsePos);
            investigatedCorpseIds.add(deadEnemy.hashCode());
        } else {
            Vector2 offset = body.getPosition().cpy().sub(corpsePos).nor().scl(2.0f);
            lastKnownPlayerPos.set(corpsePos.cpy().add(offset));
        }

        if (arrowDir != null && arrowDir.len2() > 0) {
            Vector2 arrowSource = corpsePos.cpy().sub(arrowDir.cpy().nor().scl(3f));
        }
        currentPath = null;
    }

    // Düşmanlar birbirine takılmasın diye çevredeki diğer düşmanlara bakıp aksi yönde bi kuvvet uylguyor. böylece birbirlerine çarpıp takılmıyorlar.
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

    // Düşmanın baktığı yönün kopyasını döndürüyor. Mainde çağırmak için. Buna göre görüş açısını çiziyoruz düşmanların.
    public Vector2 getFacingDir() {
        return facingDir.cpy();
    }
}
