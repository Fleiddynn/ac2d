package com.muzo.ac2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
    public static final float PPM = 50; // Metre Başına Piksel
    public static final float UNIT_SCALE = 1 / PPM; // Metre cinsinden piksel

    private static final float FPS = 1/60f;

    private OrthographicCamera camera;
    private SpriteBatch batch;

    private World world;
    private Box2DDebugRenderer debugRenderer;

    private Player player;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private ShapeRenderer shapeRenderer;

    private Array<Arrow> arrows;
    private float shootingCooldown = 0.5f;
    private float timeSinceLastShot = 0f;
    private Array<Body> bodiesToStop = new Array<Body>();

    public static final short CATEGORY_PLAYER = 0x0001;
    public static final short CATEGORY_WALL   = 0x0002;
    public static final short CATEGORY_ARROW  = 0x0004;

    // Collision maskeleri
    public static final short MASK_PLAYER     = CATEGORY_WALL;
    public static final short MASK_WALL       = CATEGORY_PLAYER | CATEGORY_ARROW;
    public static final short MASK_ARROW      = CATEGORY_WALL;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        // Camera
        float viewportWidth = 6f;
        float viewportHeight = viewportWidth * ((float)Gdx.graphics.getHeight() / Gdx.graphics.getWidth());

        camera = new OrthographicCamera(viewportWidth, viewportHeight);

        camera.position.set(0, 0, 0);
        camera.update();

        // Box2D
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();

        player = new Player(world, 0, 0);

        arrows = new Array<Arrow>();

        map = new TmxMapLoader().load("test_map.tmx");
        TiledObjectUtil.parseTiledObjectLayer(
            world,
            map.getLayers().get("Duvarlar").getObjects()
        );
        mapRenderer = new OrthogonalTiledMapRenderer(map, Main.UNIT_SCALE, batch);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = Gdx.graphics.getDeltaTime();
        timeSinceLastShot += delta;

        for (Body body : bodiesToStop) {
            if (body.getType() == BodyDef.BodyType.DynamicBody) {
                body.setType(BodyDef.BodyType.StaticBody);
            }
        }
        for (int i = arrows.size - 1; i >= 0; i--) {
            Arrow arrow = arrows.get(i);

            arrow.stateTime += delta;

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
        player.update();
        updatePlayerRotation();
        handleShooting();

        world.step(FPS, 6, 2);

        // Kamera
        camera.position.set(player.body.getPosition(), 0);
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        // Harita
        mapRenderer.setView(camera);
        mapRenderer.render();

        drawPlayer();

        debugRenderer.render(world, camera.combined);

        batch.begin();
        batch.end();


    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
    }

    // Pencere boyutu değiştiğinde kameranın boyutlarını yeniden hesaplayan fonksiyon
    @Override
    public void resize(int width, int height) {
        float viewportWidth = 6f;
        camera.viewportWidth = viewportWidth;
        camera.viewportHeight = viewportWidth * ((float)height / width);

        camera.update();
    }

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

    private void drawPlayer() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        Vector2 pos = player.body.getPosition();
        float radius = Player.RADIUS;
        float rotation = player.visualRotation;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.identity();
        shapeRenderer.translate(pos.x, pos.y, 0);

        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(0, 0, radius, 16); // Artık (0, 0)'da çiziyoruz, çünkü translate ettik.

        shapeRenderer.setColor(Color.RED);

        shapeRenderer.rotate(0, 0, 1, rotation);

        float indicatorWidth = 0.2f;
        float indicatorHeight = 0.1f;

        shapeRenderer.rect(radius, -indicatorHeight / 2, indicatorWidth, indicatorHeight);

        shapeRenderer.end();
    }

    private void handleShooting() {
        if (Gdx.input.justTouched() && Gdx.input.isButtonPressed(Input.Buttons.LEFT) && timeSinceLastShot >= shootingCooldown) {

            Vector3 mouseWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouseWorld);

            Vector2 playerPos = player.body.getPosition();

            Vector2 direction = new Vector2(mouseWorld.x - playerPos.x, mouseWorld.y - playerPos.y);
            direction.nor();

            float startX = playerPos.x + direction.x * Player.RADIUS * 1.5f;
            float startY = playerPos.y + direction.y * Player.RADIUS * 1.5f;

            Arrow newArrow = new Arrow(world, startX, startY, direction);
            newArrow.body.setUserData(newArrow);
            arrows.add(newArrow);

            timeSinceLastShot = 0f;
        }
    }

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
    @Override
    public void beginContact(Contact contact) {
        Fixture fa = contact.getFixtureA();
        Fixture fb = contact.getFixtureB();

        if (fa.getFilterData().categoryBits == CATEGORY_ARROW && fb.getFilterData().categoryBits == CATEGORY_WALL) {
            stopArrow(fa);
        } else if (fa.getFilterData().categoryBits == CATEGORY_WALL && fb.getFilterData().categoryBits == CATEGORY_ARROW) {
            stopArrow(fb);
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
}
