package com.muzo.ac2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter implements ContactListener {

    // TODO -> Düşman Todoları
    // TODO -> Player Todoları
    // TODO -> Optimizasyon yapılabilecek yerler var mı diye bakmak lazım
    // TODO -> Müzik ekleme oyuncu düşman ve oklara sprite ekleme
    // TODO -> İlerleyen zamanlarda oyunun level level olması
    // TODO -> Işıklı ve karanlık yerler olmalı mapte. Buna göre playerın visibilitysi etkilenmeli
    // TODO -> Ses efektlerini gerçekten ekleme
    // TODO -> Minimap eklenince cs deki gibi oyuncunun yaptığı sesi görselleştirme işi


    public static final float PPM = 50; // Metre Başına Piksel
    public static final float UNIT_SCALE = 1 / PPM; // Metre cinsinden piksel

    private ShapeRenderer shapeRenderer;
    private float meleeVisualTimer = 0f;



    private static final float FPS = 1/60f;

    // Buranın aşağısı gerekli şeyleri init etmek için.
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private UiRenderer ui;

    private World world;
    private Box2DDebugRenderer debugRenderer;

    private Player player;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;

    private Array<Enemy> enemies;
    private Vector2 playerSpawn = new Vector2(1, 9);
    private float playerHitCooldown = 0.5f;
    private float timeSincePlayerHit = 0f;
    private Array<Body> bodiesToDestroy = new Array<Body>();

    // Okun değişkenleri.
    private Array<Arrow> arrows;
    private float shootingCooldown = 1f;
    private float timeSinceLastShot = 0f;
    private Array<Body> bodiesToStop = new Array<Body>();

    // ses değişkenleri
    private Array<SoundEvent> soundEvents = new Array<SoundEvent>();
    private float worldClock = 0f;
    private float footstepTimer = 0f;

    // Tuzağın değişkenleri
    private Array<Trap> traps = new Array<Trap>();
    private Texture trapTexture;

    private boolean isPaused = false;
    private boolean isGameOver = false;
    private int initialEnemyCount = 0;

    private int trapCount = 5;

    // Collisionlar için bitmasking. Hex kodu ile binary şeklinde tanımlıyoruzki bilgisayar collisionları VE/VEYA operatörü ile kolay koaly çözebilsin.
    // 0000 0000 0000 0001
    // 0000 0000 0000 0010
    // 0000 0000 0000 0100
    // 0000 0000 1000 1000
    // 0000 0000 0001 0000
    // Burada ve yaparak, eğer hiçbir bit eşleşmezse collision olmadığını işlemci tek işlemde anlıyor.
    public static final short CATEGORY_PLAYER = 0x0001;
    public static final short CATEGORY_WALL   = 0x0002;
    public static final short CATEGORY_ARROW  = 0x0004;
    public static final short CATEGORY_ENEMY  = 0x0008;
    public static final short CATEGORY_ENEMY_ARROW = 0x0010;

    // Collision maskeleri
    // playerın collidelayabileceği şeylerin binarysi 0000 0000 1001 1110 oluyor. Böylece sadece ve işlemi yapıp collide var mı yok mu anlıyoruz.
    public static final short MASK_PLAYER     = CATEGORY_WALL | CATEGORY_ENEMY | CATEGORY_ARROW | CATEGORY_ENEMY_ARROW;
    public static final short MASK_WALL       = CATEGORY_PLAYER | CATEGORY_ARROW | CATEGORY_ENEMY | CATEGORY_ENEMY_ARROW;
    public static final short MASK_ARROW      = CATEGORY_WALL | CATEGORY_ENEMY | CATEGORY_PLAYER;
    public static final short MASK_ENEMY      = CATEGORY_WALL | CATEGORY_PLAYER | CATEGORY_ARROW;
    public static final short MASK_ENEMY_ARROW = CATEGORY_WALL | CATEGORY_PLAYER | CATEGORY_ENEMY;

    // kamera FOV için
    private static final float CAMERA_WORLD_WIDTH = 12f;

    // Oku germe mekaniği için değişkenler
    private boolean isCharging = false;
    private float chargeTimer = 0f;

    // Tutorial objelerinin listesi
    private Array<Tutorial> tutorials;

    // Program çalıştırıldığında bi kere çalışan fonksiyon. Ayarlamamzı ve initilaize etmemiz gereken şeyleri burada çağırmamız lazım.
    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        trapTexture = new Texture("trap.png");

        float viewportWidth = CAMERA_WORLD_WIDTH;
        float viewportHeight = viewportWidth * ((float)Gdx.graphics.getHeight() / Gdx.graphics.getWidth());

        camera = new OrthographicCamera(viewportWidth, viewportHeight);

        camera.position.set(0, 0, 0);
        camera.update();

        ui = new UiRenderer(batch, shapeRenderer);
        ui.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        world = new World(new Vector2(0, 0), true);
        world.setContactListener(this);
        debugRenderer = new Box2DDebugRenderer();

        player = new Player(world, playerSpawn.x, playerSpawn.y);

        arrows = new Array<Arrow>();

        enemies = new Array<Enemy>();

        map = new TmxMapLoader().load("test_map.tmx");

        if (map.getLayers().get("Tutorial") != null) {
            tutorials = TiledObjectUtil.parseTutorials(
                map.getLayers().get("Tutorial").getObjects()
            );
        } else {
            tutorials = new Array<Tutorial>();
        }
        enemies = TiledObjectUtil.parseTiledObjectLayer(
            world,
            map.getLayers().get("Duvarlar").getObjects(),
            map.getLayers().get("Düşmanlar").getObjects(),
            map.getLayers().get("DevriyeYolları").getObjects()
        );
        mapRenderer = new OrthogonalTiledMapRenderer(map, Main.UNIT_SCALE, batch);

        initialEnemyCount = enemies.size;
    }

    // Oyunun ana çalıştığı yer. Bu fonksiyon her frame de çalışır.
    @Override
    public void render() {

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);


        if (player != null && player.isDead) {
            isGameOver = true;
            isPaused = false;
        }
        if (!isGameOver && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            isPaused = !isPaused;
        }

        float delta = Gdx.graphics.getDeltaTime();
        worldClock += delta;
        timeSinceLastShot += delta;
        timeSincePlayerHit += delta;

        for (Enemy enemy : enemies) {
            if (enemy.isDead) continue;

            for (Trap t : traps) {
                if (!t.isTriggered) {
                    if (enemy.body.getPosition().dst(t.position) < 0.3f) {
                        t.isTriggered = true;
                        enemy.takeDamage(t.damage);
                    }
                }
            }
        }

        for (Body body : bodiesToStop) {
            if (body.getType() == BodyDef.BodyType.DynamicBody) {
                body.setType(BodyDef.BodyType.StaticBody);
            }
        }
        for (int i = arrows.size - 1; i >= 0; i--) {
            Arrow arrow = arrows.get(i);

            arrow.stateTime += delta;
            if (arrow.embeddedBody == null && !isPaused && !isGameOver) {
                arrow.whooshTimer += delta;
                if (arrow.whooshTimer >= 0.3f) {
                    arrow.whooshTimer = 0f;
                    Vector2 ap = arrow.body.getPosition();
                    soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_WHOOSH, new Vector2(ap), 0.5f, 0.8f, worldClock));
                }
            }

            if (arrow.embeddedBody != null) {
                Vector2 hostPos = arrow.embeddedBody.getPosition();
                float hostAngle = arrow.embeddedBody.getAngle();
                float cos = MathUtils.cos(hostAngle);
                float sin = MathUtils.sin(hostAngle);
                float ox = arrow.localOffset.x * cos - arrow.localOffset.y * sin;
                float oy = arrow.localOffset.x * sin + arrow.localOffset.y * cos;
                arrow.body.setTransform(hostPos.x + ox, hostPos.y + oy, hostAngle + arrow.localAngle);
                arrow.body.setLinearVelocity(0, 0);
                arrow.body.setAngularVelocity(0);
                continue;
            }

            Vector2 pos = arrow.body.getPosition();

            if (arrow.stateTime >= Arrow.MAX_LIFETIME) {
                world.destroyBody(arrow.body);
                arrows.removeIndex(i);
                continue;
            }

            if (pos.dst(player.body.getPosition()) > 20f) {
                world.destroyBody(arrow.body);
                arrows.removeIndex(i);
            }
        }
        bodiesToStop.clear();

        if (!isPaused && !isGameOver) {
            if (!player.isDead) {
                player.update();
                updatePlayerRotation();
                handleCombat(Gdx.graphics.getDeltaTime());

                Vector2 pv = player.body.getLinearVelocity();
                float speed = pv.len();
                if (!player.isCrouching && speed > 0.1f) {
                    footstepTimer += delta;
                    float baseInterval = 0.5f;
                    float interval = Math.max(0.22f, baseInterval * (1.2f - Math.min(1f, speed / 1.5f)));
                    if (footstepTimer >= interval) {
                        footstepTimer = 0f;
                        soundEvents.add(new SoundEvent(
                            SoundEvent.Type.STEP,
                            new Vector2(player.body.getPosition()),
                            0.6f,
                            1.2f,
                            worldClock
                        ));
                    }
                } else {
                    footstepTimer = Math.min(footstepTimer, 0.15f);
                }
            } else {
                player.body.setLinearVelocity(0, 0);
            }
        } else {
            player.body.setLinearVelocity(0, 0);
        }

        if (!isPaused && !isGameOver && player != null) {
            Vector2 playerPos = player.body.getPosition();
            for (Tutorial t : tutorials) {
                t.isShowing = t.bounds.contains(playerPos.x, playerPos.y);
            }
        }

        if (soundEvents.size > 0) {
            for (int i = soundEvents.size - 1; i >= 0; i--) {
                SoundEvent se = soundEvents.get(i);
                if (worldClock - se.time > se.ttl) {
                    soundEvents.removeIndex(i);
                }
            }
        }

        if (!isPaused && !isGameOver) {
            for (Enemy enemy : enemies) {
                if (enemy.isDead) continue;
                enemy.considerSounds(soundEvents, worldClock);
            }
            for (Enemy enemy : enemies) {
                if (enemy.isDead) {
                    continue;
                }
                Arrow newEnemyArrow = enemy.update(delta, player);
                if (newEnemyArrow != null) {
                    arrows.add(newEnemyArrow);
                }
            }
        }

        if (!isPaused && !isGameOver) {
            boolean broadcastAlert = false;
            for (Enemy enemy : enemies) {
                if (enemy.consumeAlertBroadcast()) {
                    broadcastAlert = true;
                }
            }
            if (broadcastAlert) {
                for (Enemy enemy : enemies) {
                    enemy.setAlert(true);
                }
            }
        }

        if (!isPaused && !isGameOver) {
            for (int i = 0; i < enemies.size; i++) {
                Enemy e = enemies.get(i);
                if (e.isDead && e.body != null) {
                    e.body.setLinearVelocity(0, 0);
                }
            }
        }


        if (player.isDead && Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            respawnAll();
            isGameOver = false;
            isPaused = false;
        }

        if (!isPaused && !isGameOver) {
            world.step(FPS, 6, 2);
        }

        if (bodiesToDestroy.size > 0) {
            for (int i = bodiesToDestroy.size - 1; i >= 0; i--) {
                Body b = bodiesToDestroy.get(i);
                Object ud = b.getUserData();
                if (ud instanceof Arrow) {
                    for (int j = arrows.size - 1; j >= 0; j--) {
                        if (arrows.get(j).body == b) {
                            arrows.removeIndex(j);
                            break;
                        }
                    }
                }
                if (b.getWorld() == world) {
                    world.destroyBody(b);
                }
            }
            bodiesToDestroy.clear();
        }


        // Kamera
        camera.position.set(player.body.getPosition(), 0);
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        // Harita
        mapRenderer.setView(camera);
        mapRenderer.render();

        batch.begin();
        for (Trap t : traps) {
            if (!t.isTriggered) {
                batch.draw(trapTexture, t.position.x, t.position.y, 0.3f, 0.3f);
            }
        }
        batch.end();
        drawPlayer();
        drawChargeBar();
        drawEnemies();
        drawArrows();
        if (!isPaused && !isGameOver) {
            ui.drawTutorials(camera, tutorials);
        }
        ui.drawEnemyStates(camera, enemies);

        if (!isPaused && !isGameOver) {
            debugRenderer.render(world, camera.combined);
        }

        if (!isPaused && !isGameOver) {
            for (int i = 0; i < enemies.size; i++) {
                Enemy e = enemies.get(i);
                if (e.isDead || e.currentState != Enemy.State.PATROLLING) continue;
                for (int j = 0; j < enemies.size; j++) {
                    if (i == j) continue;
                    Enemy other = enemies.get(j);
                    if (other.isDead && other.body != null) {
                        if (e.canSeeCorpse(other.body.getPosition())) {
                            Vector2 avgDir = new Vector2(0, 0);
                            int count = 0;
                            for (int k = 0; k < arrows.size; k++) {
                                Arrow a = arrows.get(k);
                                if (a.embeddedBody == other.body) {
                                    float ang = a.body.getAngle();
                                    avgDir.x += MathUtils.cos(ang);
                                    avgDir.y += MathUtils.sin(ang);
                                    count++;
                                }
                            }

                            if (count > 0) {
                                avgDir.scl(1f / count);
                                e.noticeCorpse(other, avgDir);
                            } else {
                                e.noticeCorpse(other, null);
                            }
                        }
                    }
                }
            }
        }

        // Ui kodları
        int dead = 0;
        for (int i = 0; i < enemies.size; i++) if (enemies.get(i).isDead) dead++;
        ui.drawHUD(Math.max(player.health, 0), dead, initialEnemyCount, player.arrowCount);
        if (isPaused && !isGameOver) {
            UiRenderer.Action act = ui.handlePauseMenuInput();
            if (act == UiRenderer.Action.RESTART) {
                respawnAll();
                isPaused = false;
            } else if (act == UiRenderer.Action.EXIT) {
                Gdx.app.exit();
            }
            ui.drawPauseMenu();
        }
        if (isGameOver) {
            ui.drawGameOver();
        }


    }

    // Bu built in lwjgl fonksiyonu ekran kartın belleğinde yani vram de memory leak olmasın diye kullanılıyor. Yani bellekten temizliyoruz
    // Uygulama kapandığında çalışyıor ama istersek kendimizde çağırabilriz.
    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        if (ui != null) ui.dispose();
    }

    // Pencere boyutu değiştiğinde kameranın boyutlarını yeniden hesaplayan fonksiyon
    @Override
    public void resize(int width, int height) {
        float viewportWidth = CAMERA_WORLD_WIDTH;
        camera.viewportWidth = viewportWidth;
        camera.viewportHeight = viewportWidth * ((float)height / width);

        camera.update();

        if (ui != null) ui.resize(width, height);
    }

    // Oyuncunun sürekli fareye bakmasını sağlayan fonksiyon.
    private void updatePlayerRotation() {
        Vector3 mouseScreen = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);

        camera.unproject(mouseScreen);

        Vector2 playerPos = player.body.getPosition();

        float angleRad = MathUtils.atan2(
            mouseScreen.y - playerPos.y,
            mouseScreen.x - playerPos.x
        );

        float angleDeg = angleRad * MathUtils.radiansToDegrees;

        player.setVisualRotation(angleDeg);
    }

    // Oyuncuyu çizme fonksiyonu. İleride buraya animasyonlu karakter scriptini renderlama koycaz
    private void drawPlayer() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        Vector2 pos = player.body.getPosition();
        float radius = Player.RADIUS;
        float rotation = player.visualRotation;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.identity();
        shapeRenderer.translate(pos.x, pos.y, 0);


        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(0, 0, radius, 16);

        shapeRenderer.setColor(Color.RED);

        shapeRenderer.rotate(0, 0, 1, rotation);

        float indicatorWidth = 0.2f;
        float indicatorHeight = 0.1f;

        shapeRenderer.rect(radius, -indicatorHeight / 2, indicatorWidth, indicatorHeight);

        shapeRenderer.end();
    }

    private void handleCombat(float delta) {

        if (Gdx.input.isKeyJustPressed(Input.Keys.T) && trapCount > 0) {
            traps.add(new Trap(player.body.getPosition().x - 0.15f, player.body.getPosition().y - 0.15f));
            trapCount--;
        }

        if (player.arrowCount < 0) player.arrowCount = 0;

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            performMeleeAttack();
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && timeSinceLastShot >= shootingCooldown && player.arrowCount > 0) {
            if (!isCharging) {
                isCharging = true;
                chargeTimer = 0f;
            }
            chargeTimer += delta;
        }
        else if (isCharging) {
            if (chargeTimer >= 1f && timeSinceLastShot >= shootingCooldown && player.arrowCount > 0) {
                fireArrow();
                player.arrowCount--;
                timeSinceLastShot = 0f;
            }
            isCharging = false;
            chargeTimer = 0f;
        }
    }

    private void performMeleeAttack() {
        meleeVisualTimer = 0.1f;
        Vector2 playerPos = player.body.getPosition();
        float attackRange = 0.3f; // Kılıç menzili (metre cinsinden)
        int damage = 1;


        for (Enemy enemy : enemies) {
            float distance = playerPos.dst(enemy.body.getPosition());

            if (distance <= attackRange) {
                enemy.takeDamage(damage);
                Vector2 pushDir = enemy.body.getPosition().cpy().sub(playerPos).nor();
                enemy.body.applyLinearImpulse(pushDir.scl(2f), enemy.body.getWorldCenter(), true);
            }
        }
    }
    private void fireArrow() {
        Vector3 mouseWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);

        Vector2 playerPos = player.body.getPosition();
        Vector2 direction = new Vector2(mouseWorld.x - playerPos.x, mouseWorld.y - playerPos.y).nor();

        float startX = playerPos.x + direction.x * Player.RADIUS * 1.5f;
        float startY = playerPos.y + direction.y * Player.RADIUS * 1.5f;

        Arrow newArrow = new Arrow(world, startX, startY, direction);
        newArrow.body.setUserData(newArrow);
        arrows.add(newArrow);

        SoundEvent shot = new SoundEvent(SoundEvent.Type.BOW_SHOT, playerPos, 1.2f, 2.5f, worldClock);
        soundEvents.add(shot);
    }

    // Okların bi yere saplanıp kalması için çalıştırılan kod.
    private void stopArrow(Fixture arrowFixture) {
        Body arrowBody = arrowFixture.getBody();

        arrowBody.setLinearVelocity(0, 0);
        arrowBody.setAngularVelocity(0);
        arrowBody.setGravityScale(0);

        if (!bodiesToStop.contains(arrowBody, true)) {
            bodiesToStop.add(arrowBody);
        }

        if (arrowBody.getUserData() instanceof Arrow) {
            ((Arrow) arrowBody.getUserData()).isStuck = true;
        }
    }

    // Bu kısımdan yıldızlara kadar olan kodlar ContactListener'a ait. Bu ContactListener bisim collision algılamamıza yarıyor. Böylece okların çarpması sağlanıyor.
    @Override
    public void beginContact(Contact contact) {
        Fixture fa = contact.getFixtureA();
        Fixture fb = contact.getFixtureB();

        if (isPaused || isGameOver) return;

        checkArrowHit(fa, fb);
        checkArrowHit(fb, fa);

        if (fa.getFilterData().categoryBits == CATEGORY_ARROW && fb.getFilterData().categoryBits == CATEGORY_WALL) {
            stopArrow(fa);
            Vector2 p = fa.getBody().getPosition();
            soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_IMPACT, new Vector2(p), 1.0f, 2.0f, worldClock));
        } else if (fa.getFilterData().categoryBits == CATEGORY_WALL && fb.getFilterData().categoryBits == CATEGORY_ARROW) {
            stopArrow(fb);
            Vector2 p = fb.getBody().getPosition();
            soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_IMPACT, new Vector2(p), 1.0f, 2.0f, worldClock));
        }

        short a = fa.getFilterData().categoryBits;
        short b = fb.getFilterData().categoryBits;
        if (!player.isDead) {
            if ((a == CATEGORY_ENEMY && b == CATEGORY_PLAYER) || (a == CATEGORY_PLAYER && b == CATEGORY_ENEMY)) {
                Fixture enemyFixture = (a == CATEGORY_ENEMY) ? fa : fb;
                Enemy e = (Enemy) enemyFixture.getBody().getUserData();

                if (e != null && !e.isDead && timeSincePlayerHit >= playerHitCooldown) {
                    player.takeDamage(1);
                    timeSincePlayerHit = 0f;
                }
            }
        }
    }

    @Override
    public void endContact(Contact contact) {

    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
        Fixture fa = contact.getFixtureA();
        Fixture fb = contact.getFixtureB();

        boolean isArrowA = fa.getFilterData().categoryBits == CATEGORY_ARROW;
        boolean isArrowB = fb.getFilterData().categoryBits == CATEGORY_ARROW;
        boolean isWallA = fa.getFilterData().categoryBits == CATEGORY_WALL;
        boolean isWallB = fb.getFilterData().categoryBits == CATEGORY_WALL;

        if ((isArrowA && isWallB) || (isWallA && isArrowB)) {

            contact.setRestitution(0f);
            contact.setFriction(1.0f);
            Object userDataA = fa.getBody().getUserData();
            Object userDataB = fb.getBody().getUserData();

            boolean stuckArrow = false;
            if (userDataA instanceof Arrow) {
                stuckArrow = ((Arrow) userDataA).isStuck;
            } else if (userDataB instanceof Arrow) {
                stuckArrow = ((Arrow) userDataB).isStuck;
            }

            if (stuckArrow) {
                contact.setEnabled(false);
            }
        }
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {

    }
    // ****************************************************************

    // Düşmanları çizmek için kod
    private void drawEnemies() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Enemy enemy : enemies) {
            if (enemy.isDead) continue;

            shapeRenderer.identity();
            Vector2 pos = enemy.body.getPosition();

            Vector2 facing = enemy.getFacingDir();
            float angle = facing.angleDeg();
            float fovHalf = 45f;
            float range = enemy.detectionRange;

            if (enemy.currentState == Enemy.State.CHASE_SHOOT) {
                shapeRenderer.setColor(1, 0, 0, 0.8f);
            } else if (enemy.currentState == Enemy.State.SUSPICIOUS || enemy.currentState == Enemy.State.SEARCHING) {
                shapeRenderer.setColor(1, 0.5f, 0, 0.7f);
            } else {
                shapeRenderer.setColor(Color.BLUE);
            }

            float startX = pos.x;
            float startY = pos.y;

            Vector2 leftLimit = new Vector2(
                startX + MathUtils.cosDeg(angle + fovHalf) * range,
                startY + MathUtils.sinDeg(angle + fovHalf) * range
            );
            Vector2 rightLimit = new Vector2(
                startX + MathUtils.cosDeg(angle - fovHalf) * range,
                startY + MathUtils.sinDeg(angle - fovHalf) * range
            );

            Vector2 leftHit = raycastToWall(pos, leftLimit);
            Vector2 rightHit = raycastToWall(pos, rightLimit);

            shapeRenderer.line(pos.x, pos.y, leftHit.x, leftHit.y);
            shapeRenderer.line(pos.x, pos.y, rightHit.x, rightHit.y);

            int segments = 12;
            Vector2 prevPoint = leftHit;
            for (int i = 1; i <= segments; i++) {
                float alpha = (float) i / segments;
                float currentAngle = (angle + fovHalf) - (fovHalf * 2 * alpha);

                Vector2 targetArc = new Vector2(
                    startX + MathUtils.cosDeg(currentAngle) * range,
                    startY + MathUtils.sinDeg(currentAngle) * range
                );
                Vector2 hitPoint = raycastToWall(pos, targetArc);

                shapeRenderer.line(prevPoint.x, prevPoint.y, hitPoint.x, hitPoint.y);
                prevPoint = hitPoint;
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Enemy enemy : enemies) {
            shapeRenderer.identity();
            Vector2 pos = enemy.body.getPosition();
            float radius = 0.1f;

            if (enemy.isDead) {
                shapeRenderer.setColor(Color.DARK_GRAY);
            } else {
                switch(enemy.currentState) {
                    case PATROLLING: shapeRenderer.setColor(Color.BLUE); break;
                    case SEARCHING: shapeRenderer.setColor(Color.ORANGE); break;
                    case CHASE_SHOOT: shapeRenderer.setColor(Color.CYAN); break;
                    case SUSPICIOUS: shapeRenderer.setColor(Color.FIREBRICK); break;
                }
            }

            shapeRenderer.circle(pos.x, pos.y, radius, 16);

            if (!enemy.isDead && enemy.isCurrentlySeeing()) {
                drawAlertBar(pos, radius, enemy.getSeeProgress());
            }
        }
        shapeRenderer.end();
    }

    // Düşmanların üstündeki playerı görme barını çizme kodu.
    private void drawAlertBar(Vector2 pos, float radius, float progress) {
        float barW = 0.4f;
        float barH = 0.05f;
        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.rect(pos.x - barW/2, pos.y + radius + 0.1f, barW, barH);
        shapeRenderer.setColor(Color.LIME);
        shapeRenderer.rect(pos.x - barW/2, pos.y + radius + 0.1f, barW * progress, barH);
    }

    // Oku germeyi göstermek için fonksiyon.
    private void drawChargeBar() {
        if (!isCharging) return;

        shapeRenderer.getTransformMatrix().idt();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector2 playerPos = player.body.getPosition();
        float barWidth = 0.6f;
        float barHeight = 0.08f;
        float offsetY = Player.RADIUS + 0.3f;

        float maxChargeTime = 1f;
        float progress = Math.min(chargeTimer / maxChargeTime, 1.0f);

        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.rect(
            playerPos.x - barWidth/2 - 0.02f,
            playerPos.y + offsetY - 0.02f,
            barWidth + 0.04f,
            barHeight + 0.04f
        );

        // Bar arka planı (koyu gri)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        shapeRenderer.rect(
            playerPos.x - barWidth/2,
            playerPos.y + offsetY,
            barWidth,
            barHeight
        );

        // Charge bar'ı (renk geçişi: sarı -> turuncu -> kırmızı)
        Color barColor = new Color();
        if (progress < 0.5f) {
            // Sarıdan turuncuya geçiş
            barColor.set(1f, 1f - (progress * 0.6f), 0f, 1f);
        } else {
            // Turuncudan kırmızıya geçiş
            barColor.set(1f, 0.7f - ((progress - 0.5f) * 1.4f), 0f, 1f);
        }

        shapeRenderer.setColor(barColor);
        shapeRenderer.rect(
            playerPos.x - barWidth/2,
            playerPos.y + offsetY,
            barWidth * progress,
            barHeight
        );

        shapeRenderer.end();
    }

    // Düşmanın duvar arkasını görmemesini sağlamak için
    private Vector2 raycastToWall(Vector2 from, Vector2 to) {
        final Vector2 hitPoint = new Vector2(to);

        world.rayCast(new RayCastCallback() {
            @Override
            public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {
                if (fixture.getFilterData().categoryBits == Main.CATEGORY_WALL) {
                    hitPoint.set(point);
                    return fraction;
                }
                return 1;
            }
        }, from, to);

        return hitPoint;
    }

    // Okları çizen fonksiyon
    private void drawArrows() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < arrows.size; i++) {
            Arrow a = arrows.get(i);
            if (a.body == null) continue;
            Vector2 p = a.body.getPosition();
            float angleDeg = a.body.getAngle() * MathUtils.radiansToDegrees;
            if (a.owner == Arrow.Owner.ENEMY) {
                shapeRenderer.setColor(1f, 0.55f, 0f, 1f); // orange
            } else {
                shapeRenderer.setColor(0.2f, 1f, 0.2f, 1f); // green
            }
            float w = Arrow.WIDTH;
            float h = Arrow.HEIGHT;
            shapeRenderer.identity();
            shapeRenderer.translate(p.x, p.y, 0);
            shapeRenderer.rotate(0, 0, 1, angleDeg);
            shapeRenderer.rect(-w/2f, -h/2f, w, h);
        }
        shapeRenderer.end();
    }

    // Oklar düşmana ya da bize çarptı mı diye kontrol ediyoruz. eğer çarparsa hasar veriyor yada yiyioruz.
    private void checkArrowHit(Fixture arrowFix, Fixture targetFix) {
        if (arrowFix.getFilterData().categoryBits == CATEGORY_ARROW) {
            Object ud = arrowFix.getBody().getUserData();
            if (!(ud instanceof Arrow)) return;
            Arrow arrow = (Arrow) ud;
            if (arrow.isStuck) return;

            short targetCat = targetFix.getFilterData().categoryBits;
            if (targetCat == CATEGORY_ENEMY && arrow.owner == Arrow.Owner.PLAYER) {
                Enemy e = (Enemy) targetFix.getBody().getUserData();
                if (e != null) {
                    int prev = e.health;
                    e.takeDamage(1);
                    if (e.isDead) {
                        Vector2 enemyPos = e.body.getPosition();
                        float enemyAngle = e.body.getAngle();
                        Vector2 arrowPos = arrow.body.getPosition();
                        float arrowAngle = arrow.body.getAngle();
                        float cos = MathUtils.cos(-enemyAngle);
                        float sin = MathUtils.sin(-enemyAngle);
                        float lx = (arrowPos.x - enemyPos.x);
                        float ly = (arrowPos.y - enemyPos.y);
                        float localX = lx * cos - ly * sin;
                        float localY = lx * sin + ly * cos;
                        arrow.embeddedBody = e.body;
                        arrow.localOffset.set(localX, localY);
                        arrow.localAngle = arrowAngle - enemyAngle;
                        arrow.isStuck = true;
                        for (Fixture f : arrow.body.getFixtureList()) {
                            Filter flt = f.getFilterData();
                            flt.maskBits = 0;
                            f.setFilterData(flt);
                        }
                        arrow.body.setLinearVelocity(0, 0);
                        arrow.body.setAngularVelocity(0);
                        arrow.body.setGravityScale(0);
                        soundEvents.add(new SoundEvent(SoundEvent.Type.SUSPICIOUS, new Vector2(enemyPos), 1.0f, 4.0f, worldClock));
                    } else {
                        scheduleDestroy(arrowFix.getBody());
                        Vector2 p = arrowFix.getBody().getPosition();
                        soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_IMPACT, new Vector2(p), 0.9f, 2.0f, worldClock));
                    }
                } else {
                    scheduleDestroy(arrowFix.getBody());
                    Vector2 p = arrowFix.getBody().getPosition();
                    soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_IMPACT, new Vector2(p), 0.9f, 2.0f, worldClock));
                }
            } else if (targetCat == CATEGORY_PLAYER && arrow.owner == Arrow.Owner.ENEMY) {
                if (!player.isDead) {
                    player.takeDamage(1);
                }
                scheduleDestroy(arrowFix.getBody());
                Vector2 p = arrowFix.getBody().getPosition();
                soundEvents.add(new SoundEvent(SoundEvent.Type.ARROW_IMPACT, new Vector2(p), 0.9f, 2.0f, worldClock));
            }
        }
    }

    // Bir objeyi doğrudan yok etmek programı çökertebilyior(Düşmanları yada okları mesela). bunun çözümü için silinecek vücutları bi listede topluyoruz. Listeden daha sonra renderda siliyoruz.
    private void scheduleDestroy(Body body) {
        if (body == null) return;
        if (!bodiesToDestroy.contains(body, true)) {
            bodiesToDestroy.add(body);
        }
    }

    // r tuşuna basınca çalışan fonksiyon. Okları siler, düşmanları ve playerı yine spawn eder. canı resetler vb.
    private void respawnAll() {
        if (player != null && player.body != null && player.body.getWorld() == world) {
            world.destroyBody(player.body);
        }
        player = new Player(world, playerSpawn.x, playerSpawn.y);
        player.health = 3;
        player.isDead = false;
        timeSincePlayerHit = 0f;

        for (int i = arrows.size - 1; i >= 0; i--) {
            Body b = arrows.get(i).body;
            if (b != null && b.getWorld() == world) {
                world.destroyBody(b);
            }
            arrows.removeIndex(i);
        }
        bodiesToDestroy.clear();

        soundEvents.clear();

        traps.clear();
        trapCount = 5;

        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e != null && e.body != null && e.body.getWorld() == world) {
                world.destroyBody(e.body);
            }
        }
        enemies.clear();

        enemies = TiledObjectUtil.createEnemiesOnly(
            world,
            map.getLayers().get("Düşmanlar").getObjects(),
            map.getLayers().get("DevriyeYolları").getObjects()
        );
        initialEnemyCount = enemies.size;
    }
}
