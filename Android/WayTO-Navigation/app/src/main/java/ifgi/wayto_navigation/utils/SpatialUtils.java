package ifgi.wayto_navigation.utils;

import android.graphics.PointF;

import com.google.maps.android.SphericalUtil;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.vividsolutions.jts.geom.Coordinate;

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


}
