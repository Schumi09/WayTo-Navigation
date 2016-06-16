package ifgi.wayto_navigation.model;

/**
 * Created by Daniel on 14.06.2016.
 */

import android.content.Context;
import android.graphics.Color;

import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.ArrayList;
import java.util.List;

import ifgi.wayto_navigation.R;
import ifgi.wayto_navigation.utils.SpatialUtils;

import static ifgi.wayto_navigation.utils.SpatialUtils.calculateTargetLatLng;

/**
 * Visualizing off-screen landmark as tangible pointer
 * Sven Bertel, Hans-Ulrich Lutter, Tom Kohlberg, and Dora Spensberger (2014).
 * Tangible Pointers to Map Locations.
 * In Christian Freksa, Bernhard Nebel, Mary Hegarty, & Thomas Barkowsky (Eds).
 * Poster presentations of the Spatial Cognition 2014 conference.
 * SFB/TR 8 Report No. 036-06/2014 (pp. 17–20). Bremen / Freiburg.
 */
public class TangiblePointer extends Visualization{
    private List<Annotation> visualization;
    private LatLng onScreenAnchor;
    private Landmark landmark;
    private Context context;
    private float alpha;
    private boolean withStyle;

    private static double OFFSET_RATIO = 0.12;
    private static int STEP = 90;

    public TangiblePointer(MapboxMap map, Landmark l, Context c, boolean style) {
        this.context = c;
        this.visualization = new ArrayList<>();
        this.landmark = l;
        this.onScreenAnchor = this.landmark.onScreenAnchor(map, OFFSET_RATIO, STEP);
        this.withStyle = style;
        setLine(map);
    }

    @Override
    public List<Annotation> getVisualization() {
        return this.visualization;
    }

    @Override
    public void remove(MapboxMap mapboxMap) {
        mapboxMap.removeAnnotations(this.getVisualization());
    }

    private void setIcon(MapboxMap map) {
        LatLng position = this.onScreenAnchor;
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position).icon(this.landmark.getOff_screen_icon());
        this.visualization.add(map.addMarker(markerOptions));
    }

    private void setLine(MapboxMap map) {
        LatLng landmark = this.landmark.getLocationLatLng();
        float heading = (float) SpatialUtils.bearing(this.onScreenAnchor, landmark);
        double distance = this.onScreenAnchor.distanceTo(landmark);
        float width;
        PolylineOptions polylineOptions;
        LatLng p1;
        LatLng p2;
        if (withStyle) {
            width = (float) distance * 1 / 1000 + 2;
            this.alpha = (float) ((distance * 1 / 50 + 30) * 2.5) / 255;
            distance = Math.sqrt(distance) + 25;
            p1 = calculateTargetLatLng(this.onScreenAnchor, heading, 0, distance);
            p2 = calculateTargetLatLng(this.onScreenAnchor, heading - 180, 0, distance);
            polylineOptions = new PolylineOptions()
                    .add(p1).add(p2).color(Color.parseColor("#000000")).width(width).alpha(this.alpha);
        }else{
            distance = Math.sqrt(distance) + 25;
            p1 = calculateTargetLatLng(this.onScreenAnchor, heading, 0, distance);
            p2 = calculateTargetLatLng(this.onScreenAnchor, heading - 180, 0, distance);
            polylineOptions = new PolylineOptions()
                    .add(p1).add(p2).color(Color.parseColor("#000000")).width(4);
        }

        int count = 0;
        int maxTries = 10;
        while(this.visualization.size() == 0) {
            try {
                this.visualization.add(map.addPolyline(polylineOptions));
                setIcon(map);
                setArrow(p1, map, heading, withStyle);
            } catch (ArrayIndexOutOfBoundsException e) {
                if (++count == maxTries) throw e;
            }
        }
    }

    private void setArrow(LatLng position, MapboxMap map, float angle, boolean withStyle) {
        Globals globals = Globals.getInstance();
        MarkerViewOptions markerViewOptions = new MarkerViewOptions();
        angle = (float) (angle - map.getCameraPosition().bearing);
        if (globals.getArrow_icon() == null) {
            globals.setArrow_icon(IconFactory.getInstance(this.context)
                    .fromResource(R.drawable.arrow_black));
        }
        markerViewOptions.icon(globals.getArrow_icon());
        markerViewOptions.position(position);
        markerViewOptions.rotation(angle);
        markerViewOptions.anchor(0.5f, 0.5f);
        //markerView.setRotation(angle);
        if (withStyle) {
            markerViewOptions.alpha(alpha);
        }
        MarkerView markerView = map.addMarker(markerViewOptions);
        this.visualization.add(markerView);
    }
}
