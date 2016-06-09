package ifgi.wayto_navigation.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
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
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.util.GeometricShapeFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ifgi.wayto_navigation.ImageUtils;
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
    private Icon off_screen_icon;
    private Icon on_screen_icon;
    private MarkerViewOptions on_screen_markerOptions;
    private Visualization visualization;



    private boolean isOnScreenOnly;
    public static final String VISUALIZATION_TYPE_KEY = "checkbox_visualization_type_preference";

    public Landmark(String lname, double lon, double lat, boolean isOnScreenOnly, Context context) {
        this.name = lname;
        this.location = new Location("Landmark");
        this.location.setLatitude(lat);
        this.location.setLongitude(lon);
        this.locationJTS = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createPoint(new Coordinate(lat, lon));
        this.locationLatLng = new LatLng(lat, lon);
        this.isOnScreenOnly = isOnScreenOnly;
        this.off_screen_icon = setBasicOffScreenMarkerIcon(context);
        this.on_screen_icon = createOnScreenMarkerIcon(context);
        this.on_screen_markerOptions = new MarkerViewOptions()
                .position(this.getLocationLatLng())
                .icon(this.on_screen_icon)
                .anchor(0.5f, 1);
    }

    public Icon getOff_screen_icon() {
        return off_screen_icon;
    }

    private void setOff_screen_icon(Icon off_screen_icon) {
        this.off_screen_icon = off_screen_icon;
    }

    public boolean isOnScreenOnly() {
        return isOnScreenOnly;
    }

    public void setOnScreenOnly(boolean onScreenOnly, Context context) {
        isOnScreenOnly = onScreenOnly;
        if (isOnScreenOnly) {
            this.off_screen_icon = setBasicOffScreenMarkerIcon(context);
        }
    }

    public Icon setBasicOffScreenMarkerIcon(Context context) {
        Drawable drawable_background = context.getDrawable(R.drawable.landmark_off_screen);
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[0]);
        layerDrawable.addLayer(drawable_background);
        layerDrawable.addLayer(context.getDrawable(ImageUtils.getIconID(this.name, context)));
        layerDrawable.setLayerGravity(1, Gravity.CENTER);
        //layerDrawable.setLayerInsetBottom(1, ImageUtils.dpToPx(context, 3));

        return IconFactory.getInstance(context).fromDrawable(layerDrawable.getCurrent());
    }

    private Icon createOnScreenMarkerIcon(Context context) {
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[0]);
        layerDrawable.addLayer(context.getDrawable(R.drawable.landmark_on_screen));
        layerDrawable.addLayer(context.getDrawable(ImageUtils.getIconID(this.name, context)));
        int icon_layer_index = layerDrawable.getNumberOfLayers() - 1;
        layerDrawable.setLayerGravity(icon_layer_index, Gravity.CENTER);
        layerDrawable.setLayerInsetBottom(icon_layer_index, ImageUtils.dpToPx(context, 10));
        layerDrawable.setLayerHeight(icon_layer_index, ImageUtils.dpToPx(context, 20));
        layerDrawable.setLayerWidth(icon_layer_index, ImageUtils.dpToPx(context, 20));
        Drawable drawable = layerDrawable.getCurrent();
        return IconFactory.getInstance(context).fromDrawable(drawable);
    }

    public String getDescription() {
        return description;
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

    public void removeVisualization(MapboxMap map) {
        if (this.visualization != null) {
            map.removeAnnotations(this.visualization.getVisualization());
        }
    }

    private WiFiPointer drawWiFiPointer(MapboxMap map, Context context) {
        return new WiFiPointer(map, this, context);
    }

    public void visualize(MapboxMap map, Context context) {

        Log.d("Landmark Name", this.getName());
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String style = sharedPref.getString(VISUALIZATION_TYPE_KEY, "");

        removeVisualization(map);

        if (!this.isOffScreen(map)) {
            Log.d("isOnScreen", "true");
            this.visualization = drawOnScreenMarker(map);
        }

        if (isOnScreenOnly) {
            return;
        }

        switch(style) {
            case "0": //Wedges
                this.visualization = drawWedge(map, context);
                break;
            case "1": //Tangible Pointer

                this.visualization = drawTangiblePointer(map, context, false);
                break;
            case "2": //Tangible Pointer with Transparency

                this.visualization = drawTangiblePointer(map, context, true);
                break;
            case "3": //Wi-Fi Pointer

                this.visualization = drawWiFiPointer(map, context);
        }

    }

    private class onScreen extends Visualization {

        List<Annotation> visualization;

        public onScreen(MapboxMap mapboxMap, Landmark landmark) {
            this.visualization = new ArrayList<>();
            this.visualization.add(mapboxMap.addMarker(landmark.getOn_screen_markerOptions()));
        }

        @Override
        public List<Annotation> getVisualization() {
            return this.visualization;
        }

        @Override
        public void remove(MapboxMap mapboxMap) {
            mapboxMap.removeAnnotations(this.getVisualization());
        }
    }

    public onScreen drawOnScreenMarker(MapboxMap map) {
        return new onScreen(map, this);
    }

    public class Wedge extends Visualization{

        public Wedge(MapboxMap map, Landmark landmark, Context context) {
            this.landmark = landmark;
            this.context = context;
            this.visualization = new ArrayList<>();
            draw(map);
        }

        private List<Annotation> visualization;
        private Landmark landmark;
        private Context context;

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
            //Coordinate[] intersection_coords = DistanceOp.nearestPoints(bbox_px, landmark_sl);
            /**
            Log.d(this.landmark.getName(), landmark_sl +"");
            Log.d(this.landmark.getName(), bbox_px.toString());*/
            //Coordinate intersection_coord = intersection_coords[0];
            Coordinate intersection_coord = intersection_heading_coord;
            //double d = landmark_sl.distance(new GeometryFactory().createPoint(intersection_coord));

            LatLng intersection = map.getProjection().fromScreenLocation(
                    new PointF((float)intersection_coord.x, (float)intersection_coord.y));


            double distanceToScreen = distanceToScreen(map, intersection); //in pixel
            double leg = calculateLeg(distanceToScreen);
            double ratio = leg / distanceToScreen;
            double true_distance = intersection.distanceTo(this.landmark.getLocationLatLng());
            double distance = ratio * true_distance;
            //double map_orientation = map.getCameraPosition().bearing;
            double heading = heading(landmark.getLocationLatLng(), intersection_heading);// - map_orientation;
            double aperture = calculateAperture(distanceToScreen, leg);

            LatLng p1 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, -(aperture / 2), distance);
            LatLng p2 = calculateTargetLatLng(this.landmark.getLocationLatLng(), heading, (aperture / 2), distance);
            LatLng mid_point = calculateMidPoint(p1, p2);
            //Log.d("Wedge", this.landmark.getName() + " Distance to screen px: " + distanceToScreen + " True distance " + true_distance + " Leg " + leg + " Ratio " + ratio + " Distance " + distance);
            PolylineOptions polygonOption = new PolylineOptions()
                    .add(locationLatLng)
                    .add(p1)
                    //.add(mid_point)
                    .add(p2)
                    .add(locationLatLng)
                    //.fillColor(Color.parseColor("#00000000"))
                    .width(1.5f)
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
            double INTRUSION_CONSTANT = 150;
            double leg = distanceToScreen + Math.log((distanceToScreen + INTRUSION_CONSTANT) / 12) * 10;
            return leg;
        }

        private double calculateAperture(double dist, double leg) {
            return Math.toDegrees((5 + dist * 0.05) / leg);
        }

        private double calculateDistance(double screen_distance, LatLng intersection) {
            double distance = intersection.distanceTo(this.landmark.getLocationLatLng());


            return 0;
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
    public class TangiblePointer extends Visualization{
        private List<Annotation> visualization;
        private LatLng onScreenAnchor;
        private Landmark landmark;
        private Context context;
        private float alpha;
        private boolean withStyle;

        public TangiblePointer(MapboxMap map, Landmark l, Context c, boolean style) {
            this.context = c;
            this.visualization = new ArrayList<>();
            this.landmark = l;
            this.onScreenAnchor = this.landmark.onScreenAnchor(map);
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
            float heading = (float) heading(this.onScreenAnchor, landmark);
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
            MarkerView markerView = map.addMarker(markerViewOptions);
            //markerView.setRotation(angle);
            if (withStyle) {
                markerView.setAlpha(alpha);
            }
            this.visualization.add(markerView);
        }
    }

    private TangiblePointer drawTangiblePointer(MapboxMap map, Context c, boolean style) {
        return new TangiblePointer(map, this, c, style);
    }

    public static void initOnScreenAnchors(MapboxMap map) {
        Globals globals = Globals.getInstance();
        if (globals.onScreenAnchorsTodo()) {
            globals.setOnScreenFrameCoords(Landmark.onScreenFrame(
                    Landmark.getBboxPolygonCoordinates(map)));
            List<Landmark.OnScreenAnchor> onScreenAnchors = Landmark.onScreenAnchors(
                    globals.getOnScreenFrameCoords());
            globals.setOnScreenAnchors(onScreenAnchors);
            globals.setOnScreenAnchorsTodo(false);
        }
    }

    /**
     * Calculating a map position to represent the off-screen position
     * @param map current mapboxmap object
     * @return LatLng the position
     */
    public LatLng onScreenAnchor(MapboxMap map) {
        Globals globals = Globals.getInstance();
        Landmark.initOnScreenAnchors(map);
        Projection proj = map.getProjection();
        LatLng userPosition = map.getCameraPosition().target;
        Coordinate[] connection_coordinates = new Coordinate[2];
        connection_coordinates[0] = latLngToSLCoordinate(userPosition, proj);
        connection_coordinates[1] = latLngToSLCoordinate(this.locationLatLng, proj);
        LineString connection = new GeometryFactory().createLineString(connection_coordinates);
        Coordinate[] onScreenFrameCoords = globals.getOnScreenFrameCoords();
        Polygon onScreenAnchorPolygon = new GeometryFactory().createPolygon(onScreenFrameCoords);
        //Log.d("Anchors", onScreenAnchors(onScreenFrameCoords).toString());
        Coordinate intersection = customIntersectionPoint(connection, onScreenAnchorPolygon)
                .getCoordinate();
        int anchor_position = getOnScreenAnchorPosition(intersection);
        List<OnScreenAnchor> onScreenAnchors = globals.getOnScreenAnchors();
        Coordinate anchor = onScreenAnchors.get(anchor_position).getCoordinate();
        onScreenAnchors.get(anchor_position).setIsFree(false);
        globals.setOnScreenAnchors(onScreenAnchors);

        LatLng value = proj.fromScreenLocation(new PointF(
                (float)anchor.x, (float)anchor.y));
        return value;
    }


    private MarkerViewOptions getOn_screen_markerOptions() {
            return this.on_screen_markerOptions;
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
        return pointF2Coordinate(landmark_sl).distance(pointF2Coordinate(intersection_sl));
    }

    private Coordinate pointF2Coordinate(PointF pointF) {
        return new Coordinate(pointF.x, pointF.y);
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
     * Calculates the heading from one coordinate to another
     * Todo: replace with own implementation
     * @param coord1
     * @param coord2
     * @return
     */
    public double heading(LatLng coord1, LatLng coord2) {
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

    public static Coordinate[] getBboxPolygonCoordinates(MapboxMap map) {
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

    public static Coordinate[] onScreenFrame(Coordinate[] coordinates) {
        double OFFSET = coordinates[1].x * 0.12; // 12% of display's top max pixel
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
        return new_coordinates;
    }

    public static class OnScreenAnchor {
        private Coordinate coordinate;
        private boolean isFree;

        public OnScreenAnchor(Coordinate coordinate) {
            this.coordinate = coordinate;
            this.isFree = true;
        }

        public void setIsFree(boolean state) {
            this.isFree = state;
        }

        public Coordinate getCoordinate() {
            return coordinate;
        }

        public boolean isFree() {
            return isFree;
        }

        @Override
        public String toString() {
            return "onScreenAnchor{" +
                    //"isFree=" + isFree +
                    ", coordinate=" + coordinate +
                    '}';
        }
    }

    public static List<OnScreenAnchor> onScreenAnchors(Coordinate[] coordinates) {
        List<OnScreenAnchor> anchors = new ArrayList<>();

        double long_dist = coordinates[1].x - coordinates[0].x;
        double space = 90; //px
        int long_number = (int) Math.ceil(long_dist / space);
        double short_dist = coordinates[2].y - coordinates[1].y;
        int short_number = (int) Math.ceil(short_dist / space);

        //long top:
        double long_x = coordinates[0].x;
        anchors.add(new OnScreenAnchor(coordinates[0]));
        for (int i=1; i<long_number; i++) {
            long_x += space;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, coordinates[1].y)));
        }
        //short right:
        double short_y = coordinates[1].y;
        //anchors.add(new onScreenAnchor(anchors.get(anchors.size() - 1).getCoordinate()));
        for (int i=1; i<short_number; i++) {
            short_y += space;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x , short_y)));
        }

        //long bottom:
        //anchors.add(new onScreenAnchor(new Coordinate(long_x, short_y));
        for (int i=1; i<long_number; i++) {
            long_x -= space;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, short_y)));
        }

        //short left:
        //anchors.add(new onScreenAnchor(new Coordinate(long_x, short_y)));
        for (int i=1; i<short_number-1; i++) {
            short_y -= space;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, short_y)));
        }

        return anchors;
    }

    private static int getOnScreenAnchorPosition(Coordinate intersection) {
        Globals globals = Globals.getInstance();
        List<OnScreenAnchor> points = globals.getOnScreenAnchors();
        int position = 0;
        double distance = intersection.distance(points.get(0).getCoordinate());
        for (int i = 1; i < points.size(); i++) {
            double d = intersection.distance(points.get(i).getCoordinate());
            if (d < distance) {
                distance = d;
                position = i;
            }
        }

        if (points.get(position).isFree()) {
            return position;
        } else {
            position = positionIndex(position, points.size());
            while (!points.get(position).isFree()) {
                position = positionIndex(position, points.size());
            }
            return position;
        }
    }

    private static int positionIndex(int position, int max) {
        int i;
        if (position == max -1) {
            i = 0;
        }else {
            position++;
            i = position;
        }
        return i;
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

    private static Coordinate latLngToSLCoordinate(LatLng latLng, Projection projection) {
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
