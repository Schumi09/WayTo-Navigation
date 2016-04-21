package ifgi.wayto_navigation.model;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.List;

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

}
