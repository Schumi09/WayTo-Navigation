package ifgi.wayto_navigation.model;

import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.geometry.ILatLng;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Daniel Schumacher on 18.03.2016.
 */
public class Landmark {

    public String name;

    public String getDescription() {
        return description;
    }

    public String description;
    public Location location;

    public Point getLocationJTS() {
        return locationJTS;
    }

    public Point locationJTS;
    public LatLng locationLatLng;

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
        List<LineString> bbox_borders = bboxToLineStringsJTS(bbox);
        int positionToLocation = positionToLocation(bbox_borders, this.locationJTS);
        Coordinate intersection_coord = DistanceOp.nearestPoints(bbox_borders
                .get(positionToLocation), this.locationJTS)[0];
        LatLng intersection = new LatLng(intersection_coord.x, intersection_coord.y);

        double distanceToScreen = distanceToScreen(map, intersection); //in pixel
        double leg = calculateLeg(distanceToScreen);
        double distance_ratio = calculateDistanceRatio(map);
        double distance = (leg * distance_ratio) + 30;
        double heading = heading(this.getLocationLatLng(), intersection);
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
        double INTRUSION_CONSTANT = 20;
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
        double heading = SphericalUtil.computeHeading(p1, p2) % 360;
        if (heading < -180) {
            return heading + 360;
        } else if (heading > 180) {
            return heading - 360;
        } else {
            return heading;
        }
    }

    /**
     * Check on what side of the mapbox the off screen location is
     * todo: considering intersection crosses a frame edge
     * @param bbox_borders
     * @param landmark
     * @return
     */
    private int positionToLocation (List<LineString> bbox_borders, Point landmark) {
        int position = 0;
        double distance = DistanceOp.distance(bbox_borders.get(0), landmark);
        for (int i = 1; i < 4; i++) {
            double d = DistanceOp.distance(bbox_borders.get(i), landmark);
            if (d < distance) {
                position = i;
                distance = d;
            }
        }
        return position;
    }

    private List<LineString> bboxToLineStringsJTS(VisibleRegion bbox) {
        List<LineString> bbox_borders = new ArrayList<LineString>();
        LineString top = bbox_border_ls(bbox.farLeft, bbox.farRight);
        LineString right = bbox_border_ls(bbox.farRight, bbox.nearRight);
        LineString bottom = bbox_border_ls(bbox.nearRight, bbox.nearLeft);
        LineString left = bbox_border_ls(bbox.nearLeft, bbox.farLeft);

        bbox_borders.add(top);
        bbox_borders.add(right);
        bbox_borders.add(bottom);
        bbox_borders.add(left);

        return bbox_borders;
    }

    private LineString bbox_border_ls(LatLng p1, LatLng p2) {
        Coordinate coord1 = new Coordinate(p1.getLatitude(),p1.getLongitude());
        Coordinate coord2 = new Coordinate(p2.getLatitude(),p2.getLongitude());
        Coordinate[] coords = new Coordinate[2];
        coords[0] = coord1;
        coords[1] = coord2;
        LineString ls = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createLineString(coords);
        return ls;
    }

    public boolean isOffScreen(MapboxMap map) {
        VisibleRegion bbox = map.getProjection().getVisibleRegion();
        boolean isOffScreen = bbox.latLngBounds.including(new ILatLng() {
            @Override
            public double getLatitude() {
                return location.getLatitude();
            }

            @Override
            public double getLongitude() {
                return location.getLongitude();
            }

            @Override
            public double getAltitude() {
                return location.getAltitude();
            }
        }) == false;
        return isOffScreen;
    }

    public String toString(){
        return this.name + " at " + this.location.toString();
    }
}
