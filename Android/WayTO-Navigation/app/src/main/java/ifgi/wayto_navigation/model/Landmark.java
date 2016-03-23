package ifgi.wayto_navigation.model;

import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.util.Log;

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

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Daniel Schumacher on 18.03.2016.
 */
public class Landmark {

    public String name;
    public String description;
    public Location location;
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

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public LatLng getLocationLatLng() {
        return locationLatLng;
    }

    public void setLocationLatLng(LatLng locationLatLng) {
        this.locationLatLng = locationLatLng;
    }

    public MarkerOptions drawMarker(MapboxMap map) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(new LatLng(this.location.getLatitude(), this.location.getLongitude()));
        return markerOptions;
    }

    public PolygonOptions drawWedge(MapboxMap map, Location current_user_position) {
        VisibleRegion bbox = map.getProjection().getVisibleRegion();
        LatLng p1;
        LatLng p2;
        Coordinate locationJTS = new Coordinate(
                this.location.getLatitude(), this.location.getLongitude());
        Coordinate userPositionJts = new Coordinate(
                current_user_position.getLatitude(), current_user_position.getLongitude());
        Coordinate[] connection_coordinates = new Coordinate[2];
        connection_coordinates[0] = userPositionJts;
        connection_coordinates[1] = locationJTS;
        LineString connection = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createLineString(connection_coordinates);

        List<LineString> bbox_borders = bboxToLineStringsJTS(bbox);
        int positionToLocation = positionToLocation(bbox_borders, connection);
        Coordinate intersection_coord = connection.intersection(bbox_borders
                .get(positionToLocation)).getCoordinate();
        LatLng intersection = new LatLng(intersection_coord.x, intersection_coord.y);
        double distanceToSceen = distanceToScreen(map, intersection); //in pixel

        double leg = calculateLeg(distanceToSceen);
        double distance = calculateDistanceRatio(distanceToSceen, intersection) * leg;
        double heading = heading(this.getLocationLatLng(), intersection)
                - map.getCameraPosition().bearing;
        double aperture = calculateAperture(distanceToSceen, leg);

        p1 = calculateWedgeEdge(heading, -(aperture/2), distance);
        p2 = calculateWedgeEdge(heading, (aperture/2), distance);

        PolygonOptions polygonOption = new PolygonOptions()
                .add(locationLatLng)
                .add(p1)
                .add(p2)
                .fillColor(Color.parseColor("#00000000"))
                .strokeColor(Color.parseColor("#990000"));
        Log.d("landmark", locationJTS.toString());
        Log.d("intersection", intersection + "");
        Log.d("Leg", leg + "");
        Log.d("distance", distance + "");
        Log.d("heading", heading + "");
        Log.d("aperture", aperture + "");
        Log.d("p1", p1.toString());
        Log.d("p2", p2.toString());
        Log.d("bearingmap", map.getCameraPosition().bearing + "");

        return polygonOption;
    }

    private double calculateDistanceRatio(double pixel_distance, LatLng intersection) {
        double ratio;
        double distance_edge_landmark = intersection.distanceTo(this.getLocationLatLng());
        ratio = distance_edge_landmark / pixel_distance;
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
        return Math.toDegrees((5 + dist * 0.3) / leg);
    }

    private double distanceToScreen(MapboxMap map, LatLng intersection) {

        PointF landmark_sl = map.getProjection().toScreenLocation(this.locationLatLng);
        PointF intersection_sl = map.getProjection().toScreenLocation(intersection);
        Log.d("screenlocationlandmark", landmark_sl.toString());

        return calculatePixelDistance(landmark_sl, intersection_sl);
    }

    private double calculatePixelDistance(PointF a, PointF b) {
        double dist;
        double dx = a.x - a.y;
        double dy = b.x - b.y;
        dist = Math.sqrt(dx * dx + dy*dy);

        return dist;
    }

    private double heading(LatLng landmark, LatLng border) {
        com.google.android.gms.maps.model.LatLng p1 = new com.google.android.gms.maps.model.LatLng
                (landmark.getLatitude(), landmark.getLongitude());
        com.google.android.gms.maps.model.LatLng p2 = new com.google.android.gms.maps.model.LatLng
                (border.getLatitude(), border.getLongitude());
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
     * @param connection
     * @return
     */
    private int positionToLocation (List<LineString> bbox_borders, LineString connection) {
        int position;
        for (position = 0; position < 4; position++) {
            if (connection.intersects(bbox_borders.get(position))) {
                return position;
            }
        }
        return -1;
    }

    private List<LineString> bboxToLineStringsJTS(VisibleRegion bbox) {
        List<LineString> bbox_borders = new ArrayList<LineString>();
        LineString top = bbox_border_ls(bbox.farLeft, bbox.farRight);
        LineString right = bbox_border_ls(bbox.farRight, bbox.nearRight);
        LineString bottom = bbox_border_ls(bbox.nearRight, bbox.nearLeft);
        LineString left = bbox_border_ls(bbox.nearLeft, bbox.farLeft);

        Log.d("bbox.farLeft", bbox.farLeft.toString());
        Log.d("bbox.farRight", bbox.farRight.toString());
        Log.d("bbox.nearRight", bbox.nearRight.toString());
        Log.d("bbox.nearLeft", bbox.nearLeft.toString());

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
}
