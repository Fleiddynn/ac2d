package com.muzo.ac2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public class UiRenderer {
    public enum Action { NONE, RESTART, EXIT }

    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private OrthographicCamera uiCamera;

    private BitmapFont uiFont;
    private BitmapFont titleFont;

    private final Rectangle btnRestart = new Rectangle();
    private final Rectangle btnExit = new Rectangle();

    public UiRenderer(SpriteBatch batch, ShapeRenderer shapeRenderer) {
        this.batch = batch;
        this.shapeRenderer = shapeRenderer;
    }

    public void init(int width, int height) {
        // UI camerası
        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, width, height);
        uiCamera.update();

        // Font yükleme
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("Monocraft-Bold.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = 22; p.color = Color.WHITE; p.borderColor = new Color(0,0,0,0.75f); p.borderWidth = 2f; p.shadowColor = new Color(0,0,0,0.5f); p.shadowOffsetX = 2; p.shadowOffsetY = 2;
        uiFont = gen.generateFont(p);
        p.size = 36; p.borderWidth = 3f; p.shadowOffsetX = 3; p.shadowOffsetY = 3;
        titleFont = gen.generateFont(p);
        gen.dispose();

        layoutPauseButtons();
    }

    public void resize(int width, int height) {
        if (uiCamera != null) {
            uiCamera.setToOrtho(false, width, height);
            uiCamera.update();
            layoutPauseButtons();
        }
    }

    private void layoutPauseButtons() {
        float w = 220, h = 48;
        float cx = uiCamera.viewportWidth / 2f;
        float cy = uiCamera.viewportHeight / 2f;
        btnRestart.set(cx - w/2f, cy - h/2f + 30, w, h);
        btnExit.set(cx - w/2f, cy - h/2f - 30, w, h);
    }

    public void drawHUD(int lives, int deadEnemies, int totalEnemies) {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        uiFont.draw(batch, "Lives: " + lives, 10, uiCamera.viewportHeight - 10);
        uiFont.draw(batch, "Dead: " + deadEnemies + "/" + totalEnemies, 10, uiCamera.viewportHeight - 40);
        batch.end();
    }

    public void drawPauseMenu() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.identity();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, uiCamera.viewportWidth, uiCamera.viewportHeight);
        float panelW = 360, panelH = 220;
        float px = (uiCamera.viewportWidth - panelW)/2f;
        float py = (uiCamera.viewportHeight - panelH)/2f;
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.9f);
        shapeRenderer.rect(px, py, panelW, panelH);
        shapeRenderer.setColor(0.25f, 0.25f, 0.25f, 1f);
        shapeRenderer.rect(btnRestart.x, btnRestart.y, btnRestart.width, btnRestart.height);
        shapeRenderer.rect(btnExit.x, btnExit.y, btnExit.width, btnExit.height);
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        titleFont.draw(batch, "Paused", px + 20, py + panelH - 20);
        uiFont.draw(batch, "Restart", btnRestart.x + 20, btnRestart.y + btnRestart.height - 14);
        uiFont.draw(batch, "Exit", btnExit.x + 20, btnExit.y + btnExit.height - 14);
        batch.end();
    }

    public Action handlePauseMenuInput() {
        if (Gdx.input.justTouched()) {
            float mx = Gdx.input.getX();
            float my = uiCamera.viewportHeight - Gdx.input.getY();
            if (btnRestart.contains(mx, my)) {
                return Action.RESTART;
            } else if (btnExit.contains(mx, my)) {
                return Action.EXIT;
            }
        }
        return Action.NONE;
    }

    public void drawGameOver() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.6f);
        shapeRenderer.rect(0, 0, uiCamera.viewportWidth, uiCamera.viewportHeight);
        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        titleFont.draw(batch, "You Died", uiCamera.viewportWidth/2f - 100, uiCamera.viewportHeight/2f + 40);
        uiFont.draw(batch, "Press R to restart", uiCamera.viewportWidth/2f - 100, uiCamera.viewportHeight/2f - 10);
        batch.end();
    }

    public void drawEnemyStates(OrthographicCamera worldCam, Array<Enemy> enemies) {
        if (enemies == null || enemies.size == 0) return;
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        Vector3 tmp = new Vector3();
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e.body == null) continue;
            tmp.set(e.body.getPosition().x, e.body.getPosition().y - 0.25f, 0f); // a bit below the enemy
            worldCam.project(tmp);
            // Compose state text
            String text;
            if (e.isDead) {
                text = "DEAD";
            } else {
                switch (e.currentState) {
                    case PATROLLING: text = "PATROLLING"; break;
                    case CHASE_SHOOT: text = "CHASE"; break;
                    case SEARCHING: default: text = "SEARCH"; break;
                }
            }
            uiFont.draw(batch, text, tmp.x - 24, tmp.y); // centered-ish
        }
        batch.end();
    }

    public void dispose() {
        if (uiFont != null) uiFont.dispose();
        if (titleFont != null) titleFont.dispose();
    }
}
