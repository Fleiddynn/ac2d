package com.muzo.ac2d;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.physics.box2d.*;

public class TiledObjectUtil {
    public static void parseTiledObjectLayer(World world, MapObjects objects) {

        for (MapObject object : objects) {

            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();

                // Box2D
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
    }
}
