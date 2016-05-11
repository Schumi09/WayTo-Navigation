package ifgi.wayto_navigation.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.List;

import ifgi.wayto_navigation.R;

/**
 * Created by Daniel on 20.04.2016.
 */
public class Globals {
    private static Globals ourInstance = new Globals();

    public static synchronized Globals getInstance() {
        return ourInstance;
    }

    private Globals() {
    }

    public List<Landmark.OnScreenAnchor> getOnScreenAnchors() {
        return onScreenAnchors;
    }

    public void setOnScreenAnchors(List<Landmark.OnScreenAnchor> onScreenAnchors) {
        this.onScreenAnchors = onScreenAnchors;
    }

    private List<Landmark.OnScreenAnchor> onScreenAnchors;

    public Coordinate[] getOnScreenFrameCoords() {
        return onScreenFrameCoords;
    }

    public void setOnScreenFrameCoords(Coordinate[] onScreenFrameCoords) {
        this.onScreenFrameCoords = onScreenFrameCoords;
    }

    private Coordinate[] onScreenFrameCoords;

    public Bitmap getArrow_bmp() {
        return arrow_bmp;
    }

    public void setArrow_bmp(Bitmap bmp) {
        this.arrow_bmp = bmp;
    }

    private Bitmap arrow_bmp;

}
