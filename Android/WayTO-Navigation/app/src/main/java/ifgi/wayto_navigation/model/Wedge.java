package ifgi.wayto_navigation.model;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.util.Log;

import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.util.GeometricShapeFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ifgi.wayto_navigation.utils.SpatialUtils;

import static ifgi.wayto_navigation.utils.SpatialUtils.calculateMidPoint;
import static ifgi.wayto_navigation.utils.SpatialUtils.calculateTargetLatLng;

/**
 * Created by Daniel on 14.06.2016.
 */
public class Wedge extends Visualization{


    public Wedge(MapboxMap map, Landmark landmark, Context context) {
        this.landmark = landmark;
        this.context = context;
        this.visualization = new ArrayList<>();
        this.onScreenAnchor = this.landmark.onScreenAnchor(map, OFFSET_RATIO, STEP);
        this.globals = Globals.getInstance();
        draw(map);
    }

    private List<Annotation> visualization;
    private Landmark landmark;
    private Context context;
    private Globals globals;
    private final LatLng onScreenAnchor;

    private static int INTRUSION_CONSTANT = 20;
    private static double APERTURE_CONSTANT = 0.04;
    private static int LEG_INCREASE = 90; //px

    private static double OFFSET_RATIO = 0.0;
    private static int STEP = 56;

    @Override
    public List<Annotation> getVisualization() {
        return this.visualization;
    }

    @Override
    public void remove(MapboxMap mapboxMap) {
        mapboxMap.removeAnnotations(this.getVisualization());
    }

    /**
     * Wedge Off-Screen visualization for distant landmarks
     * Reference: Sean Gustafson, Patrick Baudisch, Carl Gutwin, and Pourang Irani. 2008.
     * Wedge: clutter-free visualization of off-screen locations.
     * In Proceedings of the SIGCHI Conference on Human Factors in Computing Systems (CHI '08).
     * ACM, New York, NY, USA, 787-796. DOI=http://dx.doi.org/10.1145/1357054.1357179
     * todo: Calculate intersection point via bbox-polygon/landmark position
     * @param map Current MapboxMap object
     */
    private void draw(MapboxMap map) {
        PointF locationScreen = map.getProjection().toScreenLocation(landmark.getLocationLatLng());
        Point landmark_sl = new GeometryFactory().createPoint(
                new Coordinate(locationScreen.x, locationScreen.y));
        LatLng heading_coord = this.onScreenAnchor;

        double distanceToScreen = landmark_sl.distance(new GeometryFactory().createPoint(
                SpatialUtils.pointF2Coordinate(
                        map.getProjection().toScreenLocation(heading_coord)))); //in pixel
        double leg = calculateLeg(distanceToScreen);
        double ratio = (leg + LEG_INCREASE) / distanceToScreen;
        double true_distance = heading_coord.distanceTo(this.landmark.getLocationLatLng());

        double distance = (ratio * true_distance);
        //double map_orientation = map.getCameraPosition().bearing;
        double heading = SpatialUtils.bearing(landmark.getLocationLatLng(), heading_coord);
        heading = Math.round(heading);
                //- map.getCameraPosition().bearing;
        double aperture = calculateAperture(distanceToScreen, leg);

        LatLng p1 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, -(aperture / 2), distance);
        LatLng p2 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, (aperture / 2), distance);
        LatLng mid_point = calculateMidPoint(p1, p2);
        //Log.d("Wedge", this.landmark.getName() + " Distance to screen px: " + distanceToScreen + " True distance " + true_distance + " Leg " + leg + " Ratio " + ratio + " Distance " + distance);
        PolylineOptions polygonOption = new PolylineOptions()
                .add(this.landmark.getLocationLatLng())
                .add(p1)
                //.add(mid_point)
                .add(p2)
                .add(this.landmark.getLocationLatLng())
                //.fillColor(Color.parseColor("#00000000"))
                .width(1.8f)
                .color(Color.parseColor("#990000"));
        //this.visualization.add(map.addPolyline(new PolylineOptions().add(landmark.getLocationLatLng()).add(intersection).color(Color.parseColor("#990000"))));

        this.visualization.add(map.addPolyline(polygonOption));
        //com.mapbox.mapboxsdk.annotations.Polygon Polyline = (com.mapbox.mapboxsdk.annotations.Polyline) this.visualization.get(0);
        drawWedgeMarker(map, mid_point);
    }

    private void drawWedgeMarker(MapboxMap map, LatLng mid_point) {
        this.visualization.add(map.addMarker(new MarkerOptions()
                .position(
                        new LatLng(mid_point.getLatitude(), mid_point.getLongitude()))
                .icon(this.landmark.getOff_screen_icon())));
    }

    private Coordinate transform_coordinate(Coordinate coordinate) {
        coordinate.y = coordinate.y * -1;
        return coordinate;
    }

    private Coordinate[] transform_coordinates (Coordinate[] coordinates) {
        for (int i=0; i<coordinates.length; i++) {
            coordinates[i] = transform_coordinate(coordinates[i]);
        }
        return coordinates;
    }

    private LineString createArc(Coordinate center, double radius, double start_angle, double end_angle) {
        center = transform_coordinate(center);
        GeometricShapeFactory gsf = new GeometricShapeFactory();
        gsf.setSize(radius * 2); // radius
        gsf.setNumPoints(200);
        gsf.setCentre(center);
        LineString coordinates = gsf.createArc(start_angle, end_angle);
        coordinates = new GeometryFactory().createLineString(transform_coordinates(coordinates.getCoordinates()));
        return coordinates;
    }


    private Polygon wedgeBboxPolygon(Coordinate[] coordinates) {
        LineMerger lm = new LineMerger();

        double min_x = coordinates[0].x;
        double max_x = coordinates[1].x;
        double min_y = coordinates[0].y;
        double max_y = coordinates[2].y;
        double OFFSET = max_y * 1/5;


        LineString arc_top_right = createArc(new Coordinate(max_x - OFFSET, min_y + OFFSET), OFFSET, 0, Math.PI /2);
        LineString arc_bottom_right = createArc(new Coordinate(max_x - OFFSET, max_y - OFFSET), OFFSET, (Math.PI * 1.5), Math.PI /2);
        LineString arc_bottom_left = createArc(new Coordinate(min_x + OFFSET, max_y - OFFSET), OFFSET, (Math.PI), Math.PI /2);
        LineString arc_top_left = createArc(new Coordinate(min_x + OFFSET, min_y + OFFSET), OFFSET, (Math.PI / 2), Math.PI /2);
        lm.add(arc_top_left);
        lm.add(arc_bottom_left);
        lm.add(arc_bottom_right);
        lm.add(arc_top_right);
        //lm.add(connection);
        //Coordinate[] lol = arc_top_left_start + arc_top_left_start;
        Collection ls = lm.getMergedLineStrings();
        ls.add(new GeometryFactory().createPoint(arc_top_left.getCoordinateN(0)));
        Geometry geom = new GeometryFactory().buildGeometry(ls);

        return new GeometryFactory().createPolygon(geom.getCoordinates());
    }


    /**
     * calculates the length of each leg in pixels (wedge)
     * @param distanceToScreen distance between target and intersection in pixels
     * @return
     */
    private double calculateLeg(double distanceToScreen) {
        double leg = distanceToScreen + Math.log((distanceToScreen + INTRUSION_CONSTANT) / 12) * 10;
        return leg;
    }

    private double calculateAperture(double dist, double leg) {
        return Math.toDegrees((5 + dist * APERTURE_CONSTANT) / leg);
    }

    private double calculateDistance(double screen_distance, LatLng intersection) {
        double distance = intersection.distanceTo(this.landmark.getLocationLatLng());
        return distance;
    }
}
