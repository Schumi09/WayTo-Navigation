package ifgi.wayto_navigation.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.util.Log;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Projection;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

import java.util.List;

import ifgi.wayto_navigation.R;

import static ifgi.wayto_navigation.utils.SpatialUtils.latLngToSLCoordinate;

/**
 * Created by Daniel on 20.04.2016.
 */
public class Globals {
    private static Globals instance = null;
    protected Globals() {
        // Exists only to defeat instantiation.
    }
    public static Globals getInstance() {
        if(instance == null) {
            instance = new Globals();
        }
        return instance;
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

    public boolean onScreenAnchorsTodo() {
        return onScreenAnchorsTodo;
    }

    public void setOnScreenAnchorsTodo(boolean onScreenAnchorsTodo) {
        this.onScreenAnchorsTodo = onScreenAnchorsTodo;
    }

    private boolean onScreenAnchorsTodo;


    public Icon getArrow_icon() {
        return arrow_icon;
    }

    public void setArrow_icon(Icon arrow_icon) {
        this.arrow_icon = arrow_icon;
    }

    private Icon arrow_icon;
}
