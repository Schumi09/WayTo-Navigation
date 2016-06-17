package ifgi.wayto_navigation.utils;

import android.graphics.PointF;
import android.util.Log;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Projection;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Daniel Schumacher on 14.06.2016.
 */
public class SpatialUtils {

    /**
     * Calculates the heading from one coordinate to another
     * @author Cameron Mace
     * https://github.com/cammace/mapbox-utils-android
     * Todo: replace with own implementation, use his library or include to mapbox api
     * @param from
     * @param to
     * @return
     */
    public static double bearing(LatLng from, LatLng to){
        double fromLat = Math.toRadians(from.getLatitude());
        double fromLng = Math.toRadians(from.getLongitude());
        double toLat = Math.toRadians(to.getLatitude());
        double toLng = Math.toRadians(to.getLongitude());
        double dLng = toLng - fromLng;
        double heading = Math.atan2(Math.sin(dLng) * Math.cos(toLat),
                Math.cos(fromLat) * Math.sin(toLat) - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(dLng));
        return (Math.toDegrees(heading) >= -180 && Math.toDegrees(heading) < 180) ?
                Math.toDegrees(heading) : ((((Math.toDegrees(heading) + 180) % 360) + 360) % 360 + -180);
    }

    /**
     * Calculates the mid point between two points
     * Todo: replace google stuff, Implement own SphericalUtil.computeOffset
     * @param p1 LatLng 1
     * @param p2 LatLng 2
     * @return
     */
    public static LatLng calculateMidPoint(LatLng p1, LatLng p2){
        double heading = SpatialUtils.bearing(p1, p2);
        double half_distance = p1.distanceTo(p2) / 2;
        com.google.android.gms.maps.model.LatLng gLatLngLandmark = new
                com.google.android.gms.maps.model.LatLng(
                p1.getLatitude(), p1.getLongitude());
        com.google.android.gms.maps.model.LatLng googleEdge = SphericalUtil.computeOffset(
                gLatLngLandmark, half_distance, heading);
        return new LatLng(googleEdge.latitude, googleEdge.longitude);
    }

    /**
     * Calculate a target LatLng from given origin, heading, offset, distance
     * @param origin
     * @param heading
     * @param angle
     * @param dist
     * @return
     */
    public static LatLng calculateTargetLatLng(LatLng origin, double heading, double angle, double dist) {
        com.google.android.gms.maps.model.LatLng gLatLngLandmark = new
                com.google.android.gms.maps.model.LatLng(
                origin.getLatitude(), origin.getLongitude());
        com.google.android.gms.maps.model.LatLng googleEdge = SphericalUtil.computeOffset(
                gLatLngLandmark, dist, heading + angle);
        return new LatLng(googleEdge.latitude, googleEdge.longitude);
    }

    public static Coordinate pointF2Coordinate(PointF pointF) {
        return new Coordinate(pointF.x, pointF.y);
    }

    public static PointF coordinateToPointF(Coordinate coordinate) {
        return new PointF((float) coordinate.x, (float) coordinate.y);
    }

    public static Coordinate latLngToSLCoordinate(LatLng latLng, Projection projection) {
        PointF p = projection.toScreenLocation(latLng);
        return pointF2Coordinate(p);
    }

    public static LatLng SLCoordinateToLatLng(Coordinate coordinate, Projection projection) {
        return new LatLng(projection.fromScreenLocation(coordinateToPointF(coordinate)));
    }



    public static List<LineString> bboxToLineStringsJTS(VisibleRegion bbox) {
        List<LineString> bbox_borders = new ArrayList<LineString>();
        LineString top = createLineStringFromLatLngs(bbox.farLeft, bbox.farRight);
        LineString right = createLineStringFromLatLngs(bbox.farRight, bbox.nearRight);
        LineString bottom = createLineStringFromLatLngs(bbox.nearRight, bbox.nearLeft);
        LineString left = createLineStringFromLatLngs(bbox.nearLeft, bbox.farLeft);

        bbox_borders.add(top);
        bbox_borders.add(right);
        bbox_borders.add(bottom);
        bbox_borders.add(left);
        return bbox_borders;
    }

    public static LineString createLineStringFromLatLngs(LatLng p1, LatLng p2) {
        Coordinate coord1 = new Coordinate(p1.getLatitude(),p1.getLongitude());
        Coordinate coord2 = new Coordinate(p2.getLatitude(),p2.getLongitude());
        Coordinate[] coords = new Coordinate[2];
        coords[0] = coord1;
        coords[1] = coord2;
        LineString ls = new GeometryFactory(new PrecisionModel(
                PrecisionModel.FLOATING), 4326).createLineString(coords);
        return ls;
    }

    public static Coordinate[] getBboxPolygonCoordinates(Projection projection) {
        VisibleRegion bbox = projection.getVisibleRegion();
        Coordinate[] coordinates = new Coordinate[5];
        coordinates[0] = latLngToSLCoordinate(bbox.farLeft, projection);
        coordinates[1] = latLngToSLCoordinate(bbox.farRight, projection);
        coordinates[2] = latLngToSLCoordinate(bbox.nearRight, projection);
        coordinates[3] = latLngToSLCoordinate(bbox.nearLeft, projection);
        coordinates[4] = coordinates[0];
        return coordinates;
    }

}
