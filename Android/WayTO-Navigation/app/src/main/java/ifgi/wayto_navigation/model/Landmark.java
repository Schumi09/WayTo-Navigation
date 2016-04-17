package ifgi.wayto_navigation.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Projection;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import java.util.ArrayList;
import java.util.List;

import ifgi.wayto_navigation.R;


/**
 * Created by Daniel Schumacher on 18.03.2016.
 */
public class Landmark {

    private String name;
    private String description;
    private Location location;
    private Point locationJTS;
    private LatLng locationLatLng;
    private PointF locationScreen;
    private Icon on_screen_icon;
    private Icon off_screen_icon;
    private MarkerOptions on_screen_markerOptions;
    private Marker on_screen_marker;

    private List<Annotation> off_screen_visualization;

    private static double WEDGE_CORNER_RATIO = 0.1;

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
        this.locationJTS = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createPoint(new Coordinate(lat, lon));
        this.locationLatLng = new LatLng(lat, lon);
        this.on_screen_markerOptions = getOn_screen_markerOptions();
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

    public void setOff_screen_visualization(List<Annotation> visualization) {
        this.off_screen_visualization = visualization;
    }

    private void removeOffScreenVisualization(MapboxMap map) {
        if (this.off_screen_visualization != null) {
            map.removeAnnotations(this.off_screen_visualization);
            this.off_screen_visualization = new ArrayList<>();
        }
    }

    private void removeOnScreenVisualization(MapboxMap map) {
        if (this.on_screen_marker != null) {
            map.removeAnnotation(this.on_screen_marker);
        }
    }

    public void removeVisualization(MapboxMap map) {
        removeOffScreenVisualization(map);
        removeOnScreenVisualization(map);
    }

    public class Wedge {

        public Wedge(MapboxMap map, Landmark landmark, Context context) {
            this.landmark = landmark;
            this.context = context;
            this.visualization = new ArrayList<>();
            draw(map);
            landmark.setOff_screen_visualization(this.visualization);
        }

        private List<Annotation> visualization;
        private Landmark landmark;
        private Context context;

        /**
         * Wedge Off-Screen visualization for distant landmarks
         * Reference: Sean Gustafson, Patrick Baudisch, Carl Gutwin, and Pourang Irani. 2008.
         * Wedge: clutter-free visualization of off-screen locations.
         * In Proceedings of the SIGCHI Conference on Human Factors in Computing Systems (CHI '08).
         * ACM, New York, NY, USA, 787-796. DOI=http://dx.doi.org/10.1145/1357054.1357179
         * todo: Calculate intersection point via bbox-polygon/landmark position
         * @param map Current MapboxMap object
         * @return MapBox Polygon Object
         */
        private void draw(MapboxMap map) {
            Coordinate[] bbox_px_coords = bboxCoordsSL(map);
            Polygon bbox_new = wedgeBboxPolygon(bbox_px_coords);
            Polygon bbox_px = new GeometryFactory().createPolygon(bbox_px_coords);
            this.landmark.locationScreen = map.getProjection().toScreenLocation(landmark.locationLatLng);
            Point landmark_sl = new GeometryFactory().createPoint(
                    new Coordinate(this.landmark.locationScreen.x, this.landmark.locationScreen.y));
            Coordinate intersection_heading_coord = DistanceOp.nearestPoints(bbox_new, landmark_sl)[0];
            LatLng intersection_heading = map.getProjection().fromScreenLocation(
                    new PointF((float)intersection_heading_coord.x, (float)intersection_heading_coord.y));
            Coordinate intersection_coord = DistanceOp.nearestPoints(bbox_px, landmark_sl)[0];
            LatLng intersection = map.getProjection().fromScreenLocation(
                    new PointF((float)intersection_coord.x, (float)intersection_coord.y));

            double distanceToScreen = distanceToScreen(map, intersection); //in pixel
            double leg = calculateLeg(distanceToScreen);
            double distance_ratio = calculateDistanceRatio(map);
            double distance = (leg * distance_ratio);
            //double map_orientation = map.getCameraPosition().bearing;
            double heading = heading(landmark.getLocationLatLng(), intersection_heading);// - map_orientation;
            double aperture = calculateAperture(distanceToScreen, leg);

            LatLng p1 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, -(aperture / 2), distance);
            LatLng p2 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, (aperture / 2), distance);
            LatLng mid_point = calculateMidPoint(p1, p2);

            PolygonOptions polygonOption = new PolygonOptions()
                    .add(locationLatLng)
                    .add(p1)
                    .add(mid_point)
                    .add(p2)
                    .fillColor(Color.parseColor("#00000000"))
                    .strokeColor(Color.parseColor("#990000"));

            this.visualization.add(map.addPolygon(polygonOption));
            com.mapbox.mapboxsdk.annotations.Polygon polygon = (com.mapbox.mapboxsdk.annotations.Polygon) this.visualization.get(0);
            drawWedgeMarker(map, polygon);

        }

        private void drawWedgeMarker(MapboxMap map, com.mapbox.mapboxsdk.annotations.Polygon polygon) {
            LatLng mid_point = polygon.getPoints().get(2);
            this.visualization.add(map.addMarker(new MarkerOptions()
                    .position(
                            new LatLng(mid_point.getLatitude(), mid_point.getLongitude()))
                    .icon(this.landmark.getOff_screen_icon())));
        }

        private Polygon wedgeBboxPolygon(Coordinate[] coordinates) {
            double LONG_OFFSET = coordinates[1].x * WEDGE_CORNER_RATIO  * 0.25;
            double SHORT_OFFSET = coordinates[2].y * WEDGE_CORNER_RATIO;

            Coordinate[] new_coordinates = new Coordinate[9];
            new_coordinates[0] = coordinates[0];
            new_coordinates[0].x += LONG_OFFSET;
            new_coordinates[1] = coordinates[1];
            new_coordinates[1].x -= LONG_OFFSET;
            new_coordinates[2] = coordinates[1];
            new_coordinates[2].y += SHORT_OFFSET;
            new_coordinates[3] = coordinates[2];
            new_coordinates[3].y -= SHORT_OFFSET;
            new_coordinates[4] = coordinates[2];
            new_coordinates[4].x -= LONG_OFFSET;
            new_coordinates[5] = coordinates[3];
            new_coordinates[5].x += LONG_OFFSET;
            new_coordinates[6] = coordinates[3];
            new_coordinates[6].y -= SHORT_OFFSET;
            new_coordinates[7] = coordinates[3];
            new_coordinates[7].y += SHORT_OFFSET;
            new_coordinates[8] = new_coordinates[0];

            return new GeometryFactory().createPolygon(new_coordinates);
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
            return Math.toDegrees((5 + dist * 0.1) / leg);
        }
    }

    public Wedge drawWedge(MapboxMap map, Context context) {
        return new Wedge(map, this, context);
    }

    /**
     * Visualizing off-screen landmark as tangible pointer
     * Sven Bertel, Hans-Ulrich Lutter, Tom Kohlberg, and Dora Spensberger (2014).
     * Tangible Pointers to Map Locations.
     * In Christian Freksa, Bernhard Nebel, Mary Hegarty, & Thomas Barkowsky (Eds).
     * Poster presentations of the Spatial Cognition 2014 conference.
     * SFB/TR 8 Report No. 036-06/2014 (pp. 17–20). Bremen / Freiburg.
     */
    public class TangiblePointer{
        private List<Annotation> visualization;
        private LatLng onScreenAnchor;
        private Landmark landmark;
        private Context context;
        private float alpha;

        public TangiblePointer(MapboxMap map, Landmark l, Context c) {
            this.context = c;
            this.visualization = new ArrayList<>();
            this.landmark = l;
            this.onScreenAnchor = this.landmark.onScreenAnchor(map);
            setIcon(map);
            setLine(map);
            this.landmark.off_screen_visualization = this.visualization;
        }

        private void setIcon(MapboxMap map) {
            LatLng position = this.landmark.onScreenAnchor(map);
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position).icon(this.landmark.getOff_screen_icon());
            this.visualization.add(map.addMarker(markerOptions));
        }

        private void setLine(MapboxMap map) {
            LatLng landmark = this.landmark.getLocationLatLng();
            double heading = heading(this.onScreenAnchor, landmark);
            double distance = this.onScreenAnchor.distanceTo(landmark);
            float width = (float) distance * 1/1000 + 2;
            this.alpha = (float) ((distance * 1/50 + 30) * 2.5);
            distance = Math.sqrt(distance) + 25;
            LatLng p1 = calculateTargetLatLng(this.onScreenAnchor, heading, 0, distance);
            LatLng p2 = calculateTargetLatLng(this.onScreenAnchor, heading - 180, 0, distance);
            PolylineOptions polylineOptions = new PolylineOptions()
                    .add(p1).add(p2).color(Color.parseColor("#000000")).width(width).alpha(this.alpha / 255);
            ArrowParams params = new ArrowParams(map, p1, heading - map.getCameraPosition().bearing, this);
            this.visualization.add(map.addPolyline(polylineOptions));
            new SetArrow().execute(params);
            //setArrow(map, p1, heading - map.getCameraPosition().bearing);
        }

        private class ArrowParams {
            public ArrowParams(MapboxMap map, LatLng l, Double d, TangiblePointer tangiblePointer) {
                this.map = map;
                this.l = l;
                this.angle = d;
                this.tangiblePointer = tangiblePointer;
            }

            public MapboxMap map;
            public LatLng l;
            public Double angle;
            public TangiblePointer tangiblePointer;
        }

        private class SetArrow extends AsyncTask<ArrowParams, Void, MarkerOptions> {
            MapboxMap map;
            TangiblePointer tangiblePointer;

            @Override
            protected MarkerOptions doInBackground(ArrowParams... params) {
                this.map = params[0].map;
                this.tangiblePointer = params[0].tangiblePointer;
                LatLng l = params[0].l;
                double angle = params[0].angle;
                return setArrow(l, angle);
            }

            protected void onPostExecute(MarkerOptions options) {
                this.tangiblePointer.visualization.add(map.addMarker(options));
            }
        }


        private MarkerOptions setArrow(LatLng position, Double angle) {

            Bitmap icon_bmp = BitmapFactory.decodeResource(
                    this.context.getResources(), R.drawable.arrow);
            Matrix matrix = new Matrix();
            matrix.postRotate(angle.floatValue());
            icon_bmp = Bitmap.createBitmap(icon_bmp, 0, 0, icon_bmp.getWidth(), icon_bmp.getHeight(), matrix, true);
            IconFactory mIconFactory = IconFactory.getInstance(this.context);
            Drawable mIconDrawable = new BitmapDrawable(this.context.getResources(), icon_bmp);
            mIconDrawable.setAlpha(Math.round(this.alpha));

            Icon icon = mIconFactory.fromDrawable(mIconDrawable);
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position).icon(icon);
            //this.visualization.add(map.addMarker(markerOptions));
            return markerOptions;
        }

    }

    public TangiblePointer drawTangiblePointer(MapboxMap map, Context c) {
        return new TangiblePointer(map, this, c);
    }

    /**
     * Calculating a map position to represent the off-screen position
     * @param map current mapboxmap object
     * @return LatLng the position
     */
    public LatLng onScreenAnchor(MapboxMap map) {
        Projection proj = map.getProjection();
        LatLng userPosition = map.getCameraPosition().target;
        Coordinate[] connection_coordinates = new Coordinate[2];
        connection_coordinates[0] = latLngToSLCoordinate(userPosition, proj);
        connection_coordinates[1] = latLngToSLCoordinate(this.locationLatLng, proj);
        LineString connection = new GeometryFactory().createLineString(connection_coordinates);
        Polygon onScreenAnchorPolygon = onScreenFrame(getBboxPolygonCoordinates(map));
        Coordinate intersection = customIntersectionPoint(connection, onScreenAnchorPolygon).getCoordinate();

        LatLng value = proj.fromScreenLocation(new PointF(
                (float)intersection.x, (float)intersection.y));
        return value;
    }


    private MarkerOptions getOn_screen_markerOptions() {
            return new MarkerOptions()
                    .position(
                            new LatLng(this.location.getLatitude(), this.location.getLongitude()));
    }

    public void drawOnScreenMarker(MapboxMap map) {
        this.on_screen_marker = map.addMarker(this.on_screen_markerOptions);
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

    private LatLng calculateTargetLatLng(LatLng origin, double heading, double angle, double dist) {
        com.google.android.gms.maps.model.LatLng gLatLngLandmark = new
                com.google.android.gms.maps.model.LatLng(
                origin.getLatitude(), origin.getLongitude());
        com.google.android.gms.maps.model.LatLng googleEdge = SphericalUtil.computeOffset(
                gLatLngLandmark, dist, heading + angle);
        return new LatLng(googleEdge.latitude, googleEdge.longitude);
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

    private Geometry customIntersectionPoint(LineString ls, Polygon polygon) {

        List<LineString> lineStrings = new ArrayList<>();
        Coordinate[] polygon_coordinates = polygon.getCoordinates();
        for (int i=0; i<polygon_coordinates.length - 1; i++) {
            Coordinate[] temp_ls = new Coordinate[2];
            temp_ls[0] = polygon_coordinates[i];
            temp_ls[1] = polygon_coordinates[i+1];
            lineStrings.add(new GeometryFactory().createLineString(temp_ls));
        }
        for (int i=0; i<lineStrings.size(); i++) {
            LineString current_ls = lineStrings.get(i);
            if (current_ls.intersects(ls)) {
                return current_ls.intersection(ls);
            }
        }
        return null;
    }

    /**
    private double screenResolutionRatio(MapboxMap map, VisibleRegion bbox) {
        double origin_resolution = 208 * 320;
        double current_resolution = map.getProjection().toScreenLocation(bbox.farRight).x *
                map.getProjection().toScreenLocation(bbox.nearLeft).y;
        double ratio = current_resolution / origin_resolution;
        return ratio;
    }*/

    private double heading(LatLng coord1, LatLng coord2) {
        com.google.android.gms.maps.model.LatLng p1 = new com.google.android.gms.maps.model.LatLng
                (coord1.getLatitude(), coord1.getLongitude());
        com.google.android.gms.maps.model.LatLng p2 = new com.google.android.gms.maps.model.LatLng
                (coord2.getLatitude(), coord2.getLongitude());
        double heading = SphericalUtil.computeHeading(p1, p2);

        return heading;
    }

    private Polygon getBboxPolygonJTS(MapboxMap map) {
        return new GeometryFactory().createPolygon(getBboxPolygonCoordinates(map));
    }

    private Coordinate[] getBboxPolygonCoordinates(MapboxMap map) {
        Projection proj = map.getProjection();
        VisibleRegion bbox = proj.getVisibleRegion();

        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = latLngToSLCoordinate(bbox.farLeft, proj);
        coordinates[1] = latLngToSLCoordinate(bbox.farRight, proj);
        coordinates[2] = latLngToSLCoordinate(bbox.nearRight, proj);
        coordinates[3] = latLngToSLCoordinate(bbox.nearLeft, proj);
        coordinates[4] = coordinates[0];
        return coordinates;
    }

    private Polygon onScreenFrame(Coordinate[] coordinates) {
        double OFFSET = 100;
        Coordinate[] new_coordinates = new Coordinate[5];
        new_coordinates[0] = coordinates[0];
        new_coordinates[0].x += OFFSET;
        new_coordinates[0].y += OFFSET;
        new_coordinates[1] = coordinates[1];
        new_coordinates[1].x -= OFFSET;
        new_coordinates[1].y += OFFSET;
        new_coordinates[2] = coordinates[2];
        new_coordinates[2].x -= OFFSET;
        new_coordinates[2].y -= OFFSET;
        new_coordinates[3] = coordinates[3];
        new_coordinates[3].x += OFFSET;
        new_coordinates[3].y -= OFFSET;
        new_coordinates[4] = new_coordinates[0];
        return new GeometryFactory().createPolygon(new_coordinates);
    }

    private Coordinate[] bboxCoordsSL(MapboxMap map) {
        Projection proj = map.getProjection();
        VisibleRegion bbox = proj.getVisibleRegion();
        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = latLngToSLCoordinate(bbox.farLeft, proj);
        coordinates[1] = latLngToSLCoordinate(bbox.farRight, proj);
        coordinates[2] = latLngToSLCoordinate(bbox.nearRight, proj);
        coordinates[3] = latLngToSLCoordinate(bbox.nearLeft, proj);
        coordinates[4] = coordinates[0];

        return coordinates;
    }

    private Coordinate latLngToSLCoordinate (LatLng latLng, Projection projection) {
        PointF p = projection.toScreenLocation(latLng);
        return new Coordinate(p.x, p.y);
    }

    public boolean isOffScreen(MapboxMap map) {
        Polygon bbox_polygon = getBboxPolygonJTS(map);
        Coordinate sl = latLngToSLCoordinate(this.locationLatLng, map.getProjection());
        return !bbox_polygon.contains(new GeometryFactory().createPoint(sl));
    }


    /**
     * String representation of Landmark
     * @return
     */
    public String toString(){
        return this.name + " at " + this.location.toString();
    }
}
