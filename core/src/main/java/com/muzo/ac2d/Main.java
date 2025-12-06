package com.muzo.ac2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.graphics.GL20;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    public static final float PPM = 100; // Metre Başına Piksel
    public static final float UNIT_SCALE = 1 / PPM; // Metre cinsinden piksel

    private static final float FPS = 1/60f;

    private OrthographicCamera camera;
    private SpriteBatch batch;

    private World world;
    private Box2DDebugRenderer debugRenderer;

    @Override
    public void create() {
        batch = new SpriteBatch();

        // Camera
        camera = new OrthographicCamera(
            Gdx.graphics.getWidth() * UNIT_SCALE,
            Gdx.graphics.getHeight() * UNIT_SCALE
        );

        camera.position.set(0, 0, 0);
        camera.update();

        // Box2D Dünyası
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        world.step(FPS, 6, 2);

        batch.begin();
        debugRenderer.render(world, camera.combined);
        // Buraya oyun objelerinin çizim kodları gelecek


        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
