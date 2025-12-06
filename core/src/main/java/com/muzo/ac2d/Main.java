package com.muzo.ac2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
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

        // Duvarları Ekle


        player = new Player(world, 0, 0);

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

        player.update();
        updatePlayerRotation();

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
}
