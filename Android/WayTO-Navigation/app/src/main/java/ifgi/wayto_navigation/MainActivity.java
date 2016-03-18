package ifgi.wayto_navigation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;



import com.mapbox.directions.DirectionsCriteria;
import com.mapbox.directions.MapboxDirections;
import com.mapbox.directions.service.models.DirectionsResponse;
import com.mapbox.directions.service.models.DirectionsRoute;
import com.mapbox.directions.service.models.Waypoint;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;

import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapzen.android.lost.api.LocationListener;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;


public class MainActivity extends AppCompatActivity {

    /**
     * Map features.
     */
    protected String MAPBOX_ACCESS_TOKEN = "";
    private MapView mapView = null;
    private MapboxMap mMapboxMap;
    private Marker currentPositionMarker = null;
    private Polyline currentRoutePolyline = null;

    protected static final String TAG = "WayTO-Navigation";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 500;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    protected LostApiClient lostApiClient;

    protected LocationRequest mLocationRequest;
    protected LocationListener listener;
    protected Location mCurrentLocation;
    protected Location mCurrentLocationSnap;
    protected String mLastUpdateTime;
    protected Boolean mRequestingLocationUpdates = true;

    /**Münster route waypoints*/
    protected Waypoint origin = new Waypoint(7.60708, 51.93851);
    protected Waypoint destination = new Waypoint(7.61369, 51.96851);

    /**Meckenheim route waypoints
    protected Waypoint origin = new Waypoint(7.034790, 50.627801);
    protected Waypoint destination = new Waypoint(7.040636, 50.638532);*/
    protected DirectionsRoute currentRoute = null;
    protected LineString currentJtsRouteLs = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MAPBOX_ACCESS_TOKEN = getResources().getString(R.string.accessToken);

        Locale locale = new Locale("en_US");
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());

        buildLostApiClient();

        /** Create a mapView and give it some properties */
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                mMapboxMap = mapboxMap;
                getRoute(origin, destination);
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(50.632614, 7.039815)) // Münster, Germany
                        .zoom(13)
                        .build();
                mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        Log.d("locationchanged", location.toString());
                        mCurrentLocation = location;
                        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

                        if (currentJtsRouteLs != null) {
                            mCurrentLocationSnap = snapLocation(mCurrentLocation);
                            moveCurrentPositionMarker(mCurrentLocationSnap);
                        } else {
                            moveCurrentPositionMarker(mCurrentLocation);
                        }
                    }
                };
                createLocationRequest();
                startLocationUpdates();
            }
        });
    }

    private void routeToJtsLineString(DirectionsRoute route) {

        int currentRouteSize = route.getGeometry().getCoordinates().size();
        Coordinate[] coordinates = new Coordinate[currentRouteSize];

        for (int i = 0; i < currentRouteSize; i++) {
            List node = route.getGeometry().getCoordinates().get(i);
            coordinates[i] = new Coordinate((double) node.get(1), (double) node.get(0));
        }

        currentJtsRouteLs = new GeometryFactory().createLineString(coordinates);
    }

    private void getRoute(Waypoint origin, Waypoint destination) {
        MapboxDirections md = new MapboxDirections.Builder()
                .setAccessToken(MAPBOX_ACCESS_TOKEN)
                .setOrigin(origin)
                .setDestination(destination)
                .setProfile(DirectionsCriteria.PROFILE_DRIVING)
                .build();


        md.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Response<DirectionsResponse> response, Retrofit retrofit) {
                currentRoute = response.body().getRoutes().get(0);
                routeToJtsLineString(currentRoute);
                // Draw the route on the map
                drawRoute(currentRoute);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("Error", "Error: " + t.getMessage());
                showMessage("Error: " + t.getMessage());
            }
        });
    }

    private void drawRoute(DirectionsRoute route) {

        if (currentRoutePolyline != null) {
            mMapboxMap.removeAnnotation(currentRoutePolyline);
        }
        // Convert List<Waypoint> into LatLng[]
        List<Waypoint> waypoints = route.getGeometry().getWaypoints();
        LatLng[] point = new LatLng[waypoints.size()];
        for (int i = 0; i < waypoints.size(); i++) {
            point[i] = new LatLng(
                    waypoints.get(i).getLatitude(),
                    waypoints.get(i).getLongitude());
        }

        PolylineOptions routeOptions = new PolylineOptions()
                .add(point)
                .color(Color.parseColor("#3887be"));
        currentRoutePolyline = mMapboxMap.addPolyline(routeOptions);
    }

    /**
     * Snapping location to the route line usings JTS Topology Suite
     * Considering nearest point on route to location
     * http://www.vividsolutions.com/jts/jtshome.htm
     * @param location
     * @return Snapped location
     */
    private Location snapLocation(Location location) {

        Point mCurrentLocationJts = new GeometryFactory().createPoint(new Coordinate(location.getLatitude(), location.getLongitude()));

        Coordinate[] closestPoints = DistanceOp.nearestPoints(currentJtsRouteLs, mCurrentLocationJts);
        location.setLatitude(closestPoints[0].x);
        location.setLongitude(closestPoints[0].y);

        return location;
    }


    /**
     * Moving mapview to current position
     * Current position as center, bearing from location
     * @param location
     */
    private void moveCurrentPositionMarker(Location location) {

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))
                .zoom(18)
                .tilt(60)
                .bearing(mCurrentLocation.getBearing())
                .build();

        if (currentPositionMarker != null) {
            mMapboxMap.removeMarker(currentPositionMarker);
            currentPositionMarker = mMapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(location.getLatitude(), location.getLongitude())));
        } else {
            currentPositionMarker = mMapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(location.getLatitude(), location.getLongitude())));
        }
        mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    /**
     * Builds a GoogleApiClient.
     */
    protected synchronized void buildLostApiClient() {
        Log.i(TAG, "Building LostApiClient");
        lostApiClient = new LostApiClient.Builder(this).build();
        lostApiClient.connect();
        createLocationRequest();
    }

    /**
     * Sets up the location request.
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setSmallestDisplacement(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mLocationRequest, listener);
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(listener);
    }

    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */


    @Override
    protected void onStart() {
        lostApiClient.connect();
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        lostApiClient.disconnect();
        super.onStop();
         mapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        // Stop location updates to save battery
        if (lostApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (lostApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }


    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
