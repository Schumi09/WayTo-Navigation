package ifgi.wayto_navigation.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;

import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
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
import ifgi.wayto_navigation.utils.ImageUtils;
import ifgi.wayto_navigation.utils.SpatialUtils;

import static ifgi.wayto_navigation.utils.SpatialUtils.bboxToLineStringsJTS;
import static ifgi.wayto_navigation.utils.SpatialUtils.coordinateToPointF;
import static ifgi.wayto_navigation.utils.SpatialUtils.createLineStringFromLatLngs;
import static ifgi.wayto_navigation.utils.SpatialUtils.latLngToSLCoordinate;
import static ifgi.wayto_navigation.utils.SpatialUtils.pointF2Coordinate;


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
    private Visualization visualization;


    private double heading_from_map_center;

    private boolean isOnScreenOnly;
    private double rangeToVisualize;

    private static int DISTANCE_THRESHOLD = 10; //meters



    public static final String VISUALIZATION_TYPE_KEY = "checkbox_visualization_type_preference";

    public Landmark(String lname, double lon, double lat, boolean isOnScreenOnly,
                    double range, Context context) {

        this.name = lname;
        this.location = new Location("Landmark");
        this.location.setLatitude(lat);
        this.location.setLongitude(lon);
        this.locationJTS = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createPoint(new Coordinate(lat, lon));
        this.locationLatLng = new LatLng(lat, lon);
        this.isOnScreenOnly = isOnScreenOnly;
        if (!isOnScreenOnly) {
            this.off_screen_icon = setBasicOffScreenMarkerIcon(context);
        }
        this.rangeToVisualize = range;
        this.on_screen_icon = createOnScreenMarkerIcon(context);
    }

    private void update(MapboxMap map) {
        double heading = SpatialUtils.bearing(map.getCameraPosition().target, this.locationLatLng);
        this.setHeading_from_map_center(heading);
    }

    public double getHeading_to_map_center() {
        return heading_from_map_center;
    }

    public void setHeading_from_map_center(double heading_to_map_center) {
        this.heading_from_map_center = heading_to_map_center;
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

        Log.d(this.getName(), name);
        this.update(map);

        removeVisualization(map);


        Polygon bbox_polygon = new GeometryFactory().createPolygon(
                SpatialUtils.getBboxPolygonCoordinates(map.getProjection()));

        Coordinate sl = latLngToSLCoordinate(this.locationLatLng, map.getProjection());
        PointF intersection = coordinateToPointF(DistanceOp.nearestPoints(
                bbox_polygon, this.locationJTS)[0]);
        LatLng intersectionLatLng = map.getProjection().fromScreenLocation(intersection);
        int positionToLocation = positionToLocation(map);
        setDISTANCE_THRESHOLD(positionToLocation);
        double true_distance_to_screen = this.getLocationLatLng().distanceTo(intersectionLatLng);
        boolean threshold = true_distance_to_screen >= DISTANCE_THRESHOLD;
        //Log.d("true_distance_to_screen", "Distance " + true_distance_to_screen + " th " + DISTANCE_THRESHOLD + " bth " + threshold);


        boolean isOffScreen = this.isOffScreen(map);

        //Log.d("isOffScreen", isOffScreen + "");

        if ((!isOffScreen) && threshold) {
                //Log.d("HELLO", "fewqgwe");
                this.visualization = drawOnScreenMarker(map);

        } else {

            boolean toVisualize = ((map.getCameraPosition().target.distanceTo(this.getLocationLatLng()))
                    <= this.rangeToVisualize) || this.rangeToVisualize == 0;

            if ((!this.isOnScreenOnly()) && toVisualize) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                String style = sharedPref.getString(VISUALIZATION_TYPE_KEY, "");

                switch (style) {
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
        }

    }

    private class onScreen extends Visualization {

        List<Annotation> visualization;

        public onScreen(MapboxMap mapboxMap, Landmark landmark) {
            this.visualization = new ArrayList<>();
            this.visualization.add(mapboxMap.addMarker(new MarkerViewOptions()
                    .position(landmark.getLocationLatLng())
                    .icon(landmark.on_screen_icon)
                    .anchor(0.5f, 1.0f)));
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



    public Wedge drawWedge(MapboxMap map, Context context) {
        return new Wedge(map, this, context);
    }


    private TangiblePointer drawTangiblePointer(MapboxMap map, Context c, boolean style) {
        return new TangiblePointer(map, this, c, style);
    }

    public static void initOnScreenAnchors(MapboxMap map, double offset_ratio, int step) {
        Globals globals = Globals.getInstance();
        if (globals.onScreenAnchorsTodo()) {
            globals.setOnScreenFrameCoords(Landmark.onScreenFrame(
                    SpatialUtils.getBboxPolygonCoordinates(map.getProjection()), offset_ratio));
            List<Landmark.OnScreenAnchor> onScreenAnchors = Landmark.onScreenAnchors(
                    globals.getOnScreenFrameCoords(), step);
            globals.setOnScreenAnchors(onScreenAnchors);
            globals.setOnScreenAnchorsTodo(false);
        }
    }

    /**
     * Calculating a map position to represent the off-screen position
     * @param map current mapboxmap object
     * @return LatLng the position
     */
    public LatLng onScreenAnchor(MapboxMap map, double offset_ratio, int step) {
        Globals globals = Globals.getInstance();
        Landmark.initOnScreenAnchors(map, offset_ratio, step);
        Projection proj = map.getProjection();
        LatLng userPosition = map.getCameraPosition().target;
        Coordinate[] connection_coordinates = new Coordinate[2];
        connection_coordinates[0] = latLngToSLCoordinate(userPosition, proj);
        connection_coordinates[1] = latLngToSLCoordinate(this.locationLatLng, proj);
        LineString connection = new GeometryFactory().createLineString(connection_coordinates);
        Coordinate[] onScreenFrameCoords = globals.getOnScreenFrameCoords();
        Polygon onScreenAnchorPolygon = new GeometryFactory().createPolygon(onScreenFrameCoords);
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


    public double distanceToScreen(MapboxMap map, LatLng intersection) {
        PointF landmark_sl = map.getProjection().toScreenLocation(this.locationLatLng);
        PointF intersection_sl = map.getProjection().toScreenLocation(intersection);
        return pointF2Coordinate(landmark_sl).distance(pointF2Coordinate(intersection_sl));
    }

    public static Geometry customIntersectionPoint(LineString ls, Polygon polygon) {

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

    public static Coordinate[] onScreenFrame(Coordinate[] coordinates, double offset_ratio) {
        double OFFSET = coordinates[1].x * offset_ratio;
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

    /**
     * Returns a list of OnScreenAnchor points that have a given space on a frame
     * @param coordinates screen frame as list of coordinates
     * @param space gap between anchor points in pixels
     * @return
     */
    public static List<OnScreenAnchor> onScreenAnchors(Coordinate[] coordinates, double space) {
        List<OnScreenAnchor> anchors = new ArrayList<>();

        //round values to fix different lengths for same resolution
        coordinates[0] = new Coordinate(Math.round(coordinates[0].x), Math.round(coordinates[0].y));
        coordinates[1] = new Coordinate(Math.round(coordinates[1].x), Math.round(coordinates[1].y));
        coordinates[2] = new Coordinate(Math.round(coordinates[2].x), Math.round(coordinates[2].y));
        coordinates[3] = new Coordinate(Math.round(coordinates[3].x), Math.round(coordinates[3].y));
        coordinates[4] = new Coordinate(Math.round(coordinates[4].x), Math.round(coordinates[4].y));


        int long_dist = (int) (coordinates[1].x - coordinates[0].x);
        int long_number = (int) Math.floor(long_dist / space);
        double long_space_increase = (long_dist % space) / long_number; //correct the space steps
        int short_dist = (int) (coordinates[2].y - coordinates[1].y);
        int short_number = (int) Math.floor(short_dist / space); //correct the space steps
        double short_space_increase = (short_dist % space) / short_number;

        //long top:
        double long_x = coordinates[0].x; //first anchor point top left
        anchors.add(new OnScreenAnchor(coordinates[0]));
        for (int i=1; i<=long_number; i++) {
            long_x += space + long_space_increase;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, coordinates[1].y)));
        }
        //short right:
        double short_y = coordinates[1].y;
        for (int i=1; i<=short_number; i++) {
            short_y += space + short_space_increase;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x , short_y)));
        }

        //long bottom:
        for (int i=1; i<=long_number; i++) {
            long_x -= space + long_space_increase;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, short_y)));
        }

        //short left:
        for (int i=1; i<short_number-1; i++) {
            short_y -= space + short_space_increase;
            anchors.add(new OnScreenAnchor(new Coordinate(long_x, short_y)));
        }
        anchors.add(anchors.get(0)); //take first anchor point to form a closed linestring

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

        if (points.get(position).isFree() && points.get(previousIndex(position, points.size())).isFree()) {
            return position;
        } else {
            position = positionIndex(position, points.size());
            while (!(points.get(position).isFree() && points.get(previousIndex(position, points.size())).isFree()))  {
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

    private static int previousIndex(int position, int max) {
        int index;
        if (position != 0) {
            index = position - 1;
        }else {
            index = max - 1;
        }
        return index;
    }


    public boolean isOffScreen(MapboxMap map) {
        Polygon bbox_polygon = new GeometryFactory().createPolygon(
                SpatialUtils.getBboxPolygonCoordinates(map.getProjection()));
        Coordinate sl = latLngToSLCoordinate(this.locationLatLng, map.getProjection());
        return (!bbox_polygon.contains(new GeometryFactory().createPoint(sl)));
    }




    private void setDISTANCE_THRESHOLD(int position) {
        this.DISTANCE_THRESHOLD = 0;
        switch (position) {
            case 0: //top
                this.DISTANCE_THRESHOLD = 25;

            case 1: //right
                this.DISTANCE_THRESHOLD = 15;

            case 2: //bottom
                this.DISTANCE_THRESHOLD = 0;

            case 3: //left
                this.DISTANCE_THRESHOLD = 15;
        }
    }

    /**
     * Check on what side of the mapbox the off screen location is
     * todo: considering intersection crosses a frame edge
     * @param map
     * @return
     */
    public int positionToLocation (MapboxMap map) {
        List<LineString> bbox_borders = bboxToLineStringsJTS(map.getProjection().getVisibleRegion());
        LineString connection = createLineStringFromLatLngs(map.getCameraPosition().target,
                this.locationLatLng);
        int position;
        for (position = 0; position < 4; position++) {
            if (connection.intersects(bbox_borders.get(position))) {
                return position;
            }
        }
        return -1;
    }


    /**
     * String representation of Landmark
     * @return
     */
    public String toString(){
        return this.name + " at " + this.location.toString();
    }
}
