package com.muzo.ac2d;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

public class TiledObjectUtil {

    // Create'de çağrılıp dünyayı oluşturan fonksiyon.
    public static Array<Enemy> parseTiledObjectLayer(
        World world,
        MapObjects wallObjects,
        MapObjects enemyObjects,
        MapObjects pathObjects
    ) {
        // Duvarların yüklenmesi
        for (MapObject object : wallObjects) {

            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                BodyDef bdef = new BodyDef();
                bdef.type = BodyDef.BodyType.StaticBody;

                bdef.position.set(
                    (rect.x + rect.width / 2) * Main.UNIT_SCALE,
                    (rect.y + rect.height / 2) * Main.UNIT_SCALE
                );

                Body body = world.createBody(bdef);
                PolygonShape shape = new PolygonShape();

                shape.setAsBox(
                    rect.width / 2 * Main.UNIT_SCALE,
                    rect.height / 2 * Main.UNIT_SCALE
                );

                FixtureDef fdef = new FixtureDef();
                fdef.shape = shape;
                fdef.density = 1.0f;

                fdef.filter.categoryBits = Main.CATEGORY_WALL;
                fdef.filter.maskBits     = Main.MASK_WALL;

                body.createFixture(fdef);
                shape.dispose();
            }
        }

        // Devriye Yollarının yüklemesi
        ObjectMap<Integer, Array<Vector2>> patrolPaths = new ObjectMap<Integer, Array<Vector2>>();

        for (MapObject object : pathObjects) {

            int id = object.getProperties().get("patrol_id", 0, Integer.class);
            if (id == 0) continue;

            Array<Vector2> points = new Array<Vector2>();

            if (object instanceof PolylineMapObject) {
                PolylineMapObject polylineObject = (PolylineMapObject) object;
                float[] vertices = polylineObject.getPolyline().getVertices();
                float objX = object.getProperties().get("x", 0f, Float.class);
                float objY = object.getProperties().get("y", 0f, Float.class);

                for (int i = 0; i < vertices.length; i += 2) {
                    points.add(new Vector2(
                        (vertices[i] + objX) * Main.UNIT_SCALE,
                        (vertices[i+1] + objY) * Main.UNIT_SCALE
                    ));
                }
            }
            else if (object instanceof PolygonMapObject) {
                PolygonMapObject polygonObject = (PolygonMapObject) object;
                float[] vertices = polygonObject.getPolygon().getVertices();
                float objX = object.getProperties().get("x", 0f, Float.class);
                float objY = object.getProperties().get("y", 0f, Float.class);

                for (int i = 0; i < vertices.length; i += 2) {
                    points.add(new Vector2(
                        (vertices[i] + objX) * Main.UNIT_SCALE,
                        (vertices[i+1] + objY) * Main.UNIT_SCALE
                    ));
                }
            }

            if (points.size > 0) {
                patrolPaths.put(id, points);
            }
        }

        // Düşmanların Yüklenmesi
        Array<Enemy> enemies = new Array<Enemy>();
        for (MapObject object : enemyObjects) {
            String type = object.getProperties().get("type", "", String.class);
            if (!type.equals("enemy")) continue;

            int patrolId = object.getProperties().get("patrol_id", 0, Integer.class);
            float range = object.getProperties().get("range", 3.0f, Float.class);

            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            float startX = (rect.x + rect.width / 2) * Main.UNIT_SCALE;
            float startY = (rect.y + rect.height / 2) * Main.UNIT_SCALE;

            Array<Vector2> path = patrolPaths.get(patrolId);

            Enemy newEnemy = new Enemy(world, startX, startY, range, path);
            enemies.add(newEnemy);
        }

        return enemies;
    }

    // Restart butonunda çağırılan düşmanları tekrar yükleyen fonksiyon. Tüm dünyanı yeniden oluşturup performans kaybetmeye gerek yok.
    public static Array<Enemy> createEnemiesOnly(
        World world,
        MapObjects enemyObjects,
        MapObjects pathObjects
    ) {
        ObjectMap<Integer, Array<Vector2>> patrolPaths = new ObjectMap<Integer, Array<Vector2>>();

        for (MapObject object : pathObjects) {
            int id = object.getProperties().get("patrol_id", 0, Integer.class);
            if (id == 0) continue;

            Array<Vector2> points = new Array<Vector2>();

            if (object instanceof PolylineMapObject) {
                PolylineMapObject polylineObject = (PolylineMapObject) object;
                float[] vertices = polylineObject.getPolyline().getVertices();
                float objX = object.getProperties().get("x", 0f, Float.class);
                float objY = object.getProperties().get("y", 0f, Float.class);

                for (int i = 0; i < vertices.length; i += 2) {
                    points.add(new Vector2(
                        (vertices[i] + objX) * Main.UNIT_SCALE,
                        (vertices[i+1] + objY) * Main.UNIT_SCALE
                    ));
                }
            }
            else if (object instanceof PolygonMapObject) {
                PolygonMapObject polygonObject = (PolygonMapObject) object;
                float[] vertices = polygonObject.getPolygon().getVertices();
                float objX = object.getProperties().get("x", 0f, Float.class);
                float objY = object.getProperties().get("y", 0f, Float.class);

                for (int i = 0; i < vertices.length; i += 2) {
                    points.add(new Vector2(
                        (vertices[i] + objX) * Main.UNIT_SCALE,
                        (vertices[i+1] + objY) * Main.UNIT_SCALE
                    ));
                }
            }

            if (points.size > 0) {
                patrolPaths.put(id, points);
            }
        }

        Array<Enemy> enemies = new Array<Enemy>();
        for (MapObject object : enemyObjects) {
            String type = object.getProperties().get("type", "", String.class);
            if (!type.equals("enemy")) continue;

            int patrolId = object.getProperties().get("patrol_id", 0, Integer.class);
            float range = object.getProperties().get("range", 3.0f, Float.class);

            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            float startX = (rect.x + rect.width / 2) * Main.UNIT_SCALE;
            float startY = (rect.y + rect.height / 2) * Main.UNIT_SCALE;

            Array<Vector2> path = patrolPaths.get(patrolId);

            Enemy newEnemy = new Enemy(world, startX, startY, range, path);
            enemies.add(newEnemy);
        }

        return enemies;
    }

    // Tutorial yazılarını göstermek için kullanılan fonksiyon.
    public static Array<Tutorial> parseTutorials(MapObjects tutorialObjects) {
        Array<Tutorial> tutorials = new Array<Tutorial>();

        for (MapObject object : tutorialObjects) {
            String tutorialText = object.getProperties().get("text", "", String.class);
            if (tutorialText.isEmpty()) continue;

            Rectangle rect = ((RectangleMapObject) object).getRectangle();

            float x = rect.x * Main.UNIT_SCALE;
            float y = rect.y * Main.UNIT_SCALE;
            float width = rect.width * Main.UNIT_SCALE;
            float height = rect.height * Main.UNIT_SCALE;

            Tutorial t = new Tutorial(x, y, width, height, tutorialText);

            tutorials.add(t);
        }
        return tutorials;
    }
}
