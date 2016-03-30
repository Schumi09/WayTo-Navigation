package ifgi.wayto_navigation.model;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.util.Log;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.geometry.ILatLng;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Projection;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ifgi.wayto_navigation.MainActivity;


/**
 * Created by Daniel Schumacher on 18.03.2016.
 */
public class Landmark {

    public String name;
    public String description;
    public Location location;
    public Point locationJTS;
    public LatLng locationLatLng;
    public PointF locationScreen;
    public Icon on_screen_icon;
    public Icon off_screen_icon;

    public Icon getOn_screen_icon() {
        return on_screen_icon;
    }

    public Icon getOff_screen_icon() {
        return off_screen_icon;
    }

    public void setOn_screen_icon(Icon on_screen_icon) {
        this.on_screen_icon = on_screen_icon;
    }

    public void setOff_screen_icon(Icon off_screen_icon) {
        this.off_screen_icon = off_screen_icon;
    }


    public Point getLocationJTS() {
        return locationJTS;
    }

    public String getDescription() {
        return description;
    }

    public Landmark(String lname, double lon, double lat) {
        this.name = lname;
        this.location = new Location("Landmark");
        this.location.setLatitude(lat);
        this.location.setLongitude(lon);
        this.locationJTS = new GeometryFactory().createPoint(new Coordinate(lat, lon));
        this.locationLatLng = new LatLng(lat, lon);
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public LatLng getLocationLatLng() {
        return locationLatLng;
    }


    public MarkerOptions drawMarker(MapboxMap map) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(new LatLng(this.location.getLatitude(), this.location.getLongitude()));
        return markerOptions;
    }

    public PolygonOptions drawWedge(MapboxMap map) {
        VisibleRegion bbox = map.getProjection().getVisibleRegion();
        List<LineString> bbox_borders = bboxToLineStringsJTS(bbox, map);
        int positionToLocation = positionToLocation(bbox_borders, map);
        this.locationScreen = map.getProjection().toScreenLocation(this.locationLatLng);
        Point landmark_sl = new GeometryFactory().createPoint(
                new Coordinate(this.locationScreen.x, this.locationScreen.y));

        Coordinate intersection_coord = DistanceOp.nearestPoints(bbox_borders
                .get(positionToLocation), landmark_sl)[0];
        LatLng intersection = map.getProjection().fromScreenLocation(
                new PointF((float)intersection_coord.x, (float)intersection_coord.y));

        double distanceToScreen = distanceToScreen(map, intersection); //in pixel
        double leg = calculateLeg(distanceToScreen);
        double distance_ratio = calculateDistanceRatio(map);
        double distance = (leg * distance_ratio) + 20;
        double map_orientation = map.getCameraPosition().bearing;
        double heading = heading(this.getLocationLatLng(), intersection);// - map_orientation;
        double aperture = calculateAperture(distanceToScreen, leg);

        LatLng p1 = calculateWedgeEdge(heading, -(aperture / 2), distance);
        LatLng p2 = calculateWedgeEdge(heading, (aperture / 2), distance);
        LatLng mid_point = calculateMidPoint(p1, p2);

        PolygonOptions polygonOption = new PolygonOptions()
                .add(locationLatLng)
                .add(p1)
                .add(mid_point)
                .add(p2)
                .fillColor(Color.parseColor("#00000000"))
                .strokeColor(Color.parseColor("#990000"));

        return polygonOption;
    }

    private double calculateDistanceRatio(MapboxMap map) {
        double ratio;
        LatLng screenEdge1_wgs84 = map.getProjection().getVisibleRegion().farLeft;
        LatLng screenEdge2_wgs84 = map.getProjection().getVisibleRegion().farRight;
        double distance_wgs84 = screenEdge1_wgs84.distanceTo(screenEdge2_wgs84);
        PointF screenEdge1_px = map.getProjection().toScreenLocation(screenEdge1_wgs84);
        PointF screenEdge2_px = map.getProjection().toScreenLocation(screenEdge2_wgs84);
        double distance_px = calculatePixelDistance(screenEdge1_px, screenEdge2_px);
        ratio = distance_wgs84 / distance_px;
        return ratio;
    }

    private LatLng calculateWedgeEdge(double heading, double angle, double dist) {
        com.google.android.gms.maps.model.LatLng gLatLngLandmark = new
                com.google.android.gms.maps.model.LatLng(
                this.getLocationLatLng().getLatitude(), this.getLocation().getLongitude());
        com.google.android.gms.maps.model.LatLng googleEdge = SphericalUtil.computeOffset(
                gLatLngLandmark, dist, heading + angle);
        return new LatLng(googleEdge.latitude, googleEdge.longitude);
    }

    private LatLng calculateMidPoint(LatLng p1, LatLng p2){
        double heading = heading(p1, p2);
        double half_distance = p1.distanceTo(p2) / 2;
        com.google.android.gms.maps.model.LatLng gLatLngLandmark = new
                com.google.android.gms.maps.model.LatLng(
                p1.getLatitude(), p1.getLongitude());
        com.google.android.gms.maps.model.LatLng googleEdge = SphericalUtil.computeOffset(
                gLatLngLandmark, half_distance, heading);
        return new LatLng(googleEdge.latitude, googleEdge.longitude);
    }

    /**
     * calculates the length of each leg in pixels (wedge)
     * @param distanceToScreen distance between target and intersection in pixels
     * @return
     */
    private double calculateLeg(double distanceToScreen) {
        double INTRUSION_CONSTANT = 200;
        double leg = distanceToScreen + Math.log((distanceToScreen + INTRUSION_CONSTANT) / 12) * 10;
        return leg;
    }

    private double calculateAperture(double dist, double leg) {
        return Math.toDegrees((5 + dist * 0.15) / leg);
    }

    private double distanceToScreen(MapboxMap map, LatLng intersection) {

        PointF landmark_sl = map.getProjection().toScreenLocation(this.locationLatLng);
        PointF intersection_sl = map.getProjection().toScreenLocation(intersection);
        return calculatePixelDistance(landmark_sl, intersection_sl);
    }

    private double calculatePixelDistance(PointF a, PointF b) {
        double dist;
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        dist = Math.sqrt(dx * dx + dy*dy);

        return dist;
    }

    private double screenResolutionRatio(MapboxMap map, VisibleRegion bbox) {
        double origin_resolution = 208 * 320;
        double current_resolution = map.getProjection().toScreenLocation(bbox.farRight).x *
                map.getProjection().toScreenLocation(bbox.nearLeft).y;

        double ratio = current_resolution / origin_resolution;
        return ratio;
    }

    private double heading(LatLng coord1, LatLng coord2) {
        com.google.android.gms.maps.model.LatLng p1 = new com.google.android.gms.maps.model.LatLng
                (coord1.getLatitude(), coord1.getLongitude());
        com.google.android.gms.maps.model.LatLng p2 = new com.google.android.gms.maps.model.LatLng
                (coord2.getLatitude(), coord2.getLongitude());
        double heading = SphericalUtil.computeHeading(p1, p2);
        return heading;
        /**
        if (heading < -180) {
            return heading + 360;
        } else if (heading > 180) {
            return heading - 360;
        } else {
            return heading;
        }*/
    }

    /**
     * Check on what side of the mapbox the off screen location is
     * todo: considering intersection crosses a frame edge
     * @param bbox_borders
     * @param map
     * @return
     */
    private int positionToLocation (List<LineString> bbox_borders, MapboxMap map) {
        Projection projection = map.getProjection();
        PointF screen_location = projection.toScreenLocation(this.locationLatLng);
        Point loc = new GeometryFactory().createPoint(new Coordinate(screen_location.x, screen_location.y));
        int position = 0;
        double distance = DistanceOp.distance(bbox_borders.get(0), loc);
        for (int i = 1; i < 4; i++) {
            double d = DistanceOp.distance(bbox_borders.get(i), loc);
            if (d < distance) {
                position = i;
                distance = d;
            }
        }
        return position;
    }


    /**
    private List<LineString> bbbox_bordersToSL(List<LineString> bbox) {
        List<LineString> bbox_sl = new ArrayList<>();
        for (int i=0; i < 3; i++) {
            LineString ls = bbox.get(i);
            Point sp = ls.getStartPoint();
            Point sp_sl = new GeometryFactory().createPoint(new Co)
            Point ep = ls.getEndPoint();


        }
        return bbox_sl;
    }*/

    private List<LineString> bboxToLineStringsJTS(VisibleRegion bbox, MapboxMap map) {
        List<LineString> bbox_borders = new ArrayList<>();
        LineString top = bbox_border_ls(bbox.farLeft, bbox.farRight, map.getProjection());
        LineString right = bbox_border_ls(bbox.farRight, bbox.nearRight, map.getProjection());
        LineString bottom = bbox_border_ls(bbox.nearRight, bbox.nearLeft, map.getProjection());
        LineString left = bbox_border_ls(bbox.nearLeft, bbox.farLeft, map.getProjection());

        bbox_borders.add(top);
        bbox_borders.add(right);
        bbox_borders.add(bottom);
        bbox_borders.add(left);

        return bbox_borders;
    }

    private LineString bbox_border_ls(LatLng p1, LatLng p2, Projection proj) {
        PointF p1_sl = proj.toScreenLocation(p1);
        PointF p2_sl = proj.toScreenLocation(p2);
        Coordinate coord1 = new Coordinate(p1_sl.x,p1_sl.y);
        Coordinate coord2 = new Coordinate(p2_sl.x,p2_sl.y);
        Coordinate[] coords = new Coordinate[2];
        coords[0] = coord1;
        coords[1] = coord2;
        LineString ls = new GeometryFactory().createLineString(coords);
        return ls;
    }

    
    public boolean isOffScreen(MapboxMap map) {
        VisibleRegion bbox = map.getProjection().getVisibleRegion();
        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = new Coordinate(bbox.farLeft.getLatitude(), bbox.farLeft.getLongitude());
        coordinates[1] = new Coordinate(bbox.farRight.getLatitude(), bbox.farRight.getLongitude());
        coordinates[2] = new Coordinate(bbox.nearRight.getLatitude(), bbox.nearRight.getLongitude());
        coordinates[3] = new Coordinate(bbox.nearLeft.getLatitude(), bbox.nearLeft.getLongitude());
        coordinates[4] = new Coordinate(bbox.farLeft.getLatitude(), bbox.farLeft.getLongitude());


        Polygon bbox_polygon = new GeometryFactory().createPolygon(coordinates);
        return !bbox_polygon.contains(this.locationJTS);
    }

    public String toString(){
        return this.name + " at " + this.location.toString();
    }


}
