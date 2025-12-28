package com.muzo.ac2d;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

public class UiRenderer {

    // Ui a tıklayarak yapabileceğimiz eylemler.
    public enum Action { NONE, RESTART, EXIT }

    // Ui oluşturmak için gerekli değişkenler
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private OrthographicCamera uiCamera;
    private BitmapFont uiFont;
    private BitmapFont titleFont;

    // durdudma menüsündeki butonlar
    private final Rectangle btnRestart = new Rectangle();
    private final Rectangle btnExit = new Rectangle();

    private com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    // sol üstte gözüken bilgiler için ikonlar
    Texture heartTexture;
    Texture skullTexture;
    Texture arrowTexture;

    // Yapıcı fonksiyon. Yeni bi uiRenderer oluşturmak için sadece batch ve shaperenderer yeterli oluyor
    public UiRenderer(SpriteBatch batch, ShapeRenderer shapeRenderer) {
        this.batch = batch;
        this.shapeRenderer = shapeRenderer;
    }

    // Ui oluşturmak için gerekli şeyleri ayarlayan fonksiyon. Fontu ikonları vb yüklüyor.
    public void init(int width, int height) {
        heartTexture = new Texture(Gdx.files.internal("Heart.png"));
        skullTexture = new Texture(Gdx.files.internal("Skull.png"));
        arrowTexture = new Texture(Gdx.files.internal("arrow.png"));

        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, width, height);
        uiCamera.update();

        uiFont = new BitmapFont(Gdx.files.internal("uiFont.fnt"));
        titleFont = new BitmapFont(Gdx.files.internal("titleFont.fnt"));

        layoutPauseButtons();
    }

    // Mainde de bulunan eğer pencere resizelarsa ui'ı ona göre ayarlayan fonkisyon
    public void resize(int width, int height) {
        if (uiCamera != null) {
            uiCamera.setToOrtho(false, width, height);
            uiCamera.update();
            layoutPauseButtons();
        }
    }

    // pause menüdeki butonları göstermek için
    private void layoutPauseButtons() {
        float w = 220, h = 48;
        float cx = uiCamera.viewportWidth / 2f;
        float cy = uiCamera.viewportHeight / 2f;
        btnRestart.set(cx - w/2f, cy - h/2f + 30, w, h);
        btnExit.set(cx - w/2f, cy - h/2f - 30, w, h);
    }

    // None stateindeki ui'ı çizen fonksiyon
    public void drawHUD(int lives, int deadEnemies, int totalEnemies, int arrow) {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        int iconSize = 32;
        int screenPadding = 20;
        int rowGap = 20;

        // 1. Canlar (Kalpler)
        float heartY = uiCamera.viewportHeight - screenPadding - iconSize;
        for (int i = 0; i < lives; i++) {
            batch.draw(heartTexture, screenPadding + (i * (iconSize + 5)), heartY, iconSize, iconSize);
        }

        // 2. Skor (Kuru Kafa)
        float skullY = heartY - rowGap - iconSize;
        batch.draw(skullTexture, screenPadding, skullY, iconSize, iconSize);
        uiFont.draw(batch, deadEnemies + "/" + totalEnemies, screenPadding + iconSize + 10, skullY + 24);

        // 3. Ok (İkon ve Sayı)
        float arrowY = skullY - rowGap - iconSize;
        batch.draw(arrowTexture, screenPadding, arrowY, iconSize, iconSize);
        uiFont.draw(batch, String.valueOf(arrow), screenPadding + iconSize + 10, arrowY + 24);

        batch.end();
    }

    // Durdur menüsünü çzien fonksyin
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

    // Durdur menüsünde mouseun konumuna göre tıklanılan butonu bulma işi
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

    // Oyun bittiğinde yazdırılan ui
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

    // Düşmanların altında gözken debug için yazılmış fonksiyon. Düşmanların hangi statete olduğunu yazdırıyor.
    public void drawEnemyStates(OrthographicCamera worldCam, Array<Enemy> enemies) {
        if (enemies == null || enemies.size == 0) return;
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        Vector3 tmp = new Vector3();
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e.body == null) continue;
            tmp.set(e.body.getPosition().x, e.body.getPosition().y - 0.25f, 0f);
            worldCam.project(tmp);
            String text;
            if (e.isDead) {
                text = "DEAD";
            } else {
                switch (e.currentState) {
                    case PATROLLING: text = "PATROL"; break;
                    case SUSPICIOUS: text = "SUSPICIOUS"; break;
                    case CHASE_SHOOT: text = "CHASE"; break;
                    case SEARCHING: default: text = "SEARCH"; break;
                }
            }
            uiFont.setColor(Color.BLACK);
            uiFont.draw(batch, text, tmp.x - 22, tmp.y - 2);
            uiFont.setColor(Color.WHITE);
            uiFont.draw(batch, text, tmp.x - 24, tmp.y);
        }
        batch.end();
    }

    // Klasik kapatınca çalıştırmamız gereken fonksiyon. Mainde daha net açıkladım bunu
    public void dispose() {
        if (uiFont != null) uiFont.dispose();
        if (titleFont != null) titleFont.dispose();

        if (heartTexture != null) heartTexture.dispose();
        if (skullTexture != null) skullTexture.dispose();
    }

    //Tutorialları çizmek için gereken fonksiyon.
    public void drawTutorials(OrthographicCamera worldCam, Array<Tutorial> tutorials) {
        if (tutorials == null) return;

        for (Tutorial t : tutorials) {
            Vector3 tmp = new Vector3(
                t.bounds.x + t.bounds.width / 2f,
                t.bounds.y + t.bounds.height,
                0
            );
            worldCam.project(tmp);

            float verticalOffset = 0f;

            if (t.isShowing) {
                layout.setText(uiFont, t.text);
                float textWidth = layout.width;
                float textHeight = layout.height;

                float paddingX = 20f;
                float paddingY = 15f;
                float boxWidth = textWidth + (paddingX * 2);
                float boxHeight = textHeight + (paddingY * 2);

                float boxY = tmp.y + verticalOffset;

                shapeRenderer.setProjectionMatrix(uiCamera.combined);
                shapeRenderer.identity();
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

                shapeRenderer.setColor(0, 0, 0, 0.8f);
                shapeRenderer.rect(tmp.x - boxWidth / 2f, boxY, boxWidth, boxHeight);

                shapeRenderer.setColor(Color.YELLOW);
                shapeRenderer.rect(tmp.x - boxWidth / 2f, boxY, boxWidth, 2);
                shapeRenderer.rect(tmp.x - boxWidth / 2f, boxY + boxHeight - 2, boxWidth, 2);
                shapeRenderer.end();

                batch.setProjectionMatrix(uiCamera.combined);
                batch.setColor(Color.WHITE);
                batch.begin();
                uiFont.setColor(Color.WHITE);
                uiFont.draw(batch, t.text, tmp.x - textWidth / 2f, boxY + (boxHeight / 2f) + (textHeight / 2f));
                batch.end();

            } else {
                String questionMark = "?";
                layout.setText(uiFont, questionMark);

                float bounce = MathUtils.sin(Gdx.graphics.getFrameId() * 0.1f) * 5f;

                batch.setProjectionMatrix(uiCamera.combined);
                batch.setColor(Color.WHITE);
                batch.begin();
                uiFont.setColor(Color.BLACK);
                uiFont.draw(batch, questionMark, (tmp.x - layout.width / 2f) + 1, (tmp.y + bounce) - 2);
                uiFont.setColor(Color.WHITE);
                uiFont.draw(batch, questionMark, tmp.x - layout.width / 2f, tmp.y + verticalOffset + bounce);
                batch.end();
            }
        }
    }

    // Uiların ok atınca kaymasını çözmek için mainde uiCameraya erişme fonksiynou.
    public OrthographicCamera getUiCamera() {
        return uiCamera;
    }

    // Oyun kazanma ekranı.
    public void drawGameWon() {
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(0, 0.3f, 0, 0.7f);
        shapeRenderer.rect(0, 0, uiCamera.viewportWidth, uiCamera.viewportHeight);

        float panelW = 500, panelH = 250;
        float px = (uiCamera.viewportWidth - panelW) / 2f;
        float py = (uiCamera.viewportHeight - panelH) / 2f;

        shapeRenderer.setColor(0.1f, 0.4f, 0.1f, 0.95f);
        shapeRenderer.rect(px, py, panelW, panelH);

        float borderSize = 4f;
        shapeRenderer.setColor(1f, 0.84f, 0, 1f);
        shapeRenderer.rect(px - borderSize, py - borderSize, panelW + borderSize * 2, borderSize);
        shapeRenderer.rect(px - borderSize, py + panelH, panelW + borderSize * 2, borderSize);
        shapeRenderer.rect(px - borderSize, py, borderSize, panelH);
        shapeRenderer.rect(px + panelW, py, borderSize, panelH);

        shapeRenderer.end();

        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        titleFont.setColor(1f, 0.84f, 0, 1f); // Altın rengi
        GlyphLayout congratsLayout = new GlyphLayout(titleFont, "TEBRİKLER!");
        titleFont.draw(batch, "TEBRİKLER!",
            uiCamera.viewportWidth / 2f - congratsLayout.width / 2f,
            py + panelH - 40);

        uiFont.setColor(Color.WHITE);
        GlyphLayout winLayout = new GlyphLayout(uiFont, "Oyunu Bitirdin!");
        uiFont.draw(batch, "Oyunu Bitirdin!",
            uiCamera.viewportWidth / 2f - winLayout.width / 2f,
            uiCamera.viewportHeight / 2f + 20);

        GlyphLayout restartLayout = new GlyphLayout(uiFont, "Tekrar oynamak için R'ye bas");
        uiFont.draw(batch, "Tekrar oynamak için R'ye bas",
            uiCamera.viewportWidth / 2f - restartLayout.width / 2f,
            py + 40);

        batch.end();
    }
}
