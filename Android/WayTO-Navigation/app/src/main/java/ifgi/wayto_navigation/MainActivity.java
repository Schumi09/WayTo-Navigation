package ifgi.wayto_navigation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.hs.gpxparser.GPXParser;
import com.hs.gpxparser.modal.GPX;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.constants.MyBearingTracking;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;
import com.mapbox.mapboxsdk.location.LocationServices;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Projection;
import com.mapbox.services.Constants;
import com.mapbox.services.commons.ServicesException;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.directions.v4.DirectionsCriteria;
import com.mapbox.services.directions.v4.MapboxDirections;
import com.mapbox.services.directions.v4.models.DirectionsResponse;
import com.mapbox.services.directions.v4.models.DirectionsRoute;
import com.mapbox.services.directions.v4.models.Waypoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import ifgi.wayto_navigation.model.Globals;
import ifgi.wayto_navigation.model.Landmark;
import ifgi.wayto_navigation.utils.ImageUtils;
import ifgi.wayto_navigation.utils.SpatialUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    /**
     * Global variables
     */
    protected Globals globals;

    /**
     * Map features.
     */
    protected String MAPBOX_ACCESS_TOKEN = "";
    private MapView mapView = null;
    private MapboxMap mMapboxMap;
    private Marker currentPositionMarker;
    private Polyline currentRoutePolyline = null;

    private List<Location> simulation_locations;
    private boolean simulate;
    private Handler simulationHandler;
    private boolean isSimulationRunning = false;



    protected static final String TAG = "WayTO-Navigation";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 3000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    protected Icon mCurrentPositionIcon;
    protected Location mCurrentLocation;
    protected Location mCurrentLocationSnap;
    protected double mCurrentBearing = 361;
    protected final int BEARING_THRESHOLD = 30;
    protected String mLastUpdateTime;
    protected SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener;

    public static final String VISUALIZATION_TYPE_KEY = "checkbox_visualization_type_preference";
    public static final String SIMULATION_KEY = "checkbox_simulation";


    /**
     * Münster route waypoints
     */
    protected Waypoint origin = new Waypoint(7.61964, 51.95324);
    protected Waypoint destination = new Waypoint(7.62478, 51.96547);


    protected List<Landmark> landmarks = new ArrayList<Landmark>();

    protected DirectionsRoute currentRoute = null;
    protected LineString currentJtsRouteLs = null;

    protected ActionBar actionBar;

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;

    private String[] PERMISSIONS;
    private static int PERMISSION_ALL = 1;

    private static List<Polygon> frame;
    private Handler frameHandler;
    private Runnable frameRunnable;
    private boolean frameStatus;
    private static final int FRAME_UPDATE_INTERVAL = 220;//ms
    private static boolean toDrawFrame = false;

    protected String gps_track = "ms"; //default

    private Icon anchorIcon;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        anchorIcon = IconFactory.getInstance(this).fromDrawable(getDrawable(R.drawable.dot));
        landmarkSetup();
        globals = Globals.getInstance();
        mCurrentPositionIcon = ImageUtils.getIcon(R.drawable.position, this);
        MAPBOX_ACCESS_TOKEN = getResources().getString(R.string.accessToken);

        simulate = prefs.getBoolean("checkbox_simulation", true);
        final Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setLogo(R.drawable.logo_wayto);
        setSupportActionBar(myToolbar);
        actionBar = getSupportActionBar();
        frameHandler = new Handler();

        //todo: remove when included in MAS
        Locale locale = new Locale("en_US");
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());


        final ArrayList<Waypoint> positions = new ArrayList<>();
        positions.add(origin);
        positions.add(destination);

        /** Create a mapView and give it some properties */
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setStyleUrl("mapbox://styles/schumi91/cimm7mq0i009dzpmckjmo8u4u");
        mapView.onCreate(savedInstanceState);
        toggleFullscreen();
        myToolbar.bringToFront();
        PERMISSIONS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET};

        long time_of_permission_request = System.currentTimeMillis();
        do {
            checkPermissions();
        }
        while (awaitPermissionRequest(time_of_permission_request));
        if (!hasPermissions(this.getApplicationContext(), PERMISSIONS)) {
            moveTaskToBack(true);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                mMapboxMap = mapboxMap;
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(51.953780194, 7.619926209))
                        .zoom(16)
                        .build();

                mMapboxMap.setCameraPosition(cameraPosition);

                mMapboxMap.setOnMyLocationChangeListener(new MapboxMap.OnMyLocationChangeListener() {
                    @Override
                    public void onMyLocationChange(@Nullable Location location) {
                        if (location != null) {
                            onLocationChanged(location);
                        }
                    }
                });
                LocationServices.getLocationServices(MainActivity.this).toggleGPS(true);
                mMapboxMap.setMyLocationEnabled(true);
                mMapboxMap.getTrackingSettings().setMyLocationTrackingMode(MyLocationTracking.TRACKING_FOLLOW);
                mMapboxMap.getTrackingSettings().setMyBearingTrackingMode(MyBearingTracking.GPS);

                if (toDrawFrame) {
                    frameHandler.post(visualizeBorderFrame);
                }

                mMapboxMap.setOnCameraChangeListener(new MapboxMap.OnCameraChangeListener() {
                    @Override
                    public void onCameraChange(CameraPosition position) {
                        landmarkVisualization();
                    }
                });
                //startLocationUpdates();
                mMapboxMap.getUiSettings().setCompassEnabled(false);
                mMapboxMap.getUiSettings().setLogoEnabled(false); //needs to be enabled in production
                mMapboxMap.getUiSettings().setRotateGesturesEnabled(false);
                mMapboxMap.getUiSettings().setAttributionEnabled(false); //needs to be enabled in production
                mMapboxMap.getUiSettings().setZoomGesturesEnabled(false);



                try {
                    if(simulate) {
                        simulate_route();
                    }
                    else{
                        getRoute(positions);
                    }
                } catch (ServicesException e) {
                    e.printStackTrace();
                }



                mMapboxMap.setOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng point) {
                        toggleActionBar(actionBar);
                    }
                });
                onSharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                          String key) {
                        if (key.equals(VISUALIZATION_TYPE_KEY)) {
                            //mapboxMap.removeAnnotations();
                            if (currentRoute != null) {
                                drawRoute(currentRoute);
                            }
                        }else if(key.equals(SIMULATION_KEY)) {
                            simulate_route();
                        }
                    }
                };
                prefs.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
                mapView.setKeepScreenOn(true);
            }
        });
    }

    private void toggleActionBar(ActionBar actionBar) {
        if (actionBar.isShowing()) {
            actionBar.hide();
        } else {
            actionBar.show();
        }
    }

    private void toggleFullscreen() {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.flags ^= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setAttributes(attrs);
    }

    /**
     * execute on OnMyLocationChanged event.
     * Once the location has changed a marker displays the user's current position and updates the
     * mapview to the position as center.
     * todo: distinguish between snap and not snapped location in wedge call
     *
     * @param location
     */
    public void onLocationChanged(Location location) {

        if (mMapboxMap == null) return;

        if (location.hasSpeed()) {
            Log.d("Speed", location.getSpeed() * 3.6 + "");
        }
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        if (mCurrentBearing == 361) {
            mCurrentBearing = mCurrentLocation.getBearing();
        }

        if (currentJtsRouteLs != null) {
            mCurrentLocationSnap = snapLocation(mCurrentLocation);
            //moveCurrentPositionMarker(mCurrentLocationSnap);
        }

        if (location.hasSpeed() && location.getSpeed() > 2) {
            if (Math.abs(mCurrentBearing - mCurrentLocation.getBearing()) > BEARING_THRESHOLD) {
                mCurrentBearing = mCurrentLocation.getBearing();
            }
        }
        /**
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))
                .zoom(15)
                .bearing(mCurrentBearing)
                .tilt(0)
                .build();
        if (mMapboxMap != null) {
            mMapboxMap.setCameraPosition(cameraPosition);
        }*/
        //landmarkVisualization();
    }
    List<MarkerView> onscreen = new ArrayList<>();
    private void landmarkVisualization() {
        if (mMapboxMap != null) {
            globals.setOnScreenAnchorsTodo(true);
            for (int i = 0; i < landmarks.size(); i++) {
                Landmark l = landmarks.get(i);
                l.visualize(mMapboxMap, getApplicationContext());
            }
        }
    }


    private void getRoute(List<Waypoint> positions) throws ServicesException {

        MapboxDirections md = new MapboxDirections.Builder()
                .setAccessToken("pk.eyJ1Ijoic2NodW1pOTEiLCJhIjoiY2lsZ294Mmc2MDA1ZHZrbTR3aHd2NnhqbSJ9.YPm4QaT1_13qooe7XLBovA")
                .setWaypoints(positions)
                .setProfile(DirectionsCriteria.PROFILE_DRIVING)
                .build();

        md.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                // You can get the generic HTTP info about the response
                Log.d(TAG, "Response code: " + response.code());
                Log.d("Call", call.request().url().toString());

                if (response.body() == null) {
                    Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                    return;
                } else {

                    currentRoute = response.body().getRoutes().get(0);

                    LatLng start = new LatLng(
                            simulation_locations.get(0).getLatitude(),
                            simulation_locations.get(0).getLongitude());
                    LatLng bearingTo = new LatLng(
                            simulation_locations.get(1).getLatitude(),
                            simulation_locations.get(1).getLongitude());

                    double bearing = SpatialUtils.bearing(start, bearingTo);
                    mMapboxMap.setCameraPosition(new CameraPosition.Builder()
                            .target(start).bearing(bearing).build());
                    drawRoute(currentRoute);
                    if(simulate && !isSimulationRunning) {
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mockLocations(simulation_locations);
                            }
                        }, 3000);
                    }
                    Log.d(TAG, "Distance: " + currentRoute.getDistance());
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Log.e(TAG, "Error: " + t.getMessage());
            }
        });
    }

    private void drawRoute(DirectionsRoute route) {

        if (currentRoutePolyline != null) {
            mMapboxMap.removeAnnotation(currentRoutePolyline);
        }
        com.mapbox.services.commons.geojson.LineString lineString = com.mapbox.services.commons.geojson.LineString.fromPolyline(route.getGeometry(), Constants.OSRM_PRECISION_V4);
        List<Position> coordinates = lineString.getCoordinates();
        Coordinate[] coords = new Coordinate[coordinates.size()];
        LatLng[] points = new LatLng[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            points[i] = new LatLng(
                    coordinates.get(i).getLatitude(),
                    coordinates.get(i).getLongitude());
            coords[i] = new Coordinate(points[i].getLatitude(),
                    points[i].getLongitude());
        }

        currentJtsRouteLs = new GeometryFactory().createLineString(coords);
        PolylineOptions routeOptions = new PolylineOptions()
                .add(points)
                .color(Color.parseColor("#396F62"));
        currentRoutePolyline = mMapboxMap.addPolyline(routeOptions);
    }

    /**
     * Snapping location to the route line usings JTS Topology Suite
     * Considering nearest point on route to location
     * http://www.vividsolutions.com/jts/jtshome.htm
     *
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
     * Mock gps positions from gpx file
     */
    private void simulate_route() {
        toggleActionBar(actionBar);
        List<com.hs.gpxparser.modal.Waypoint> waypoints = loadGPX(gps_track);
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < waypoints.size(); i++) {
            Location mockLocation = new Location("mock");
            com.hs.gpxparser.modal.Waypoint wp = waypoints.get(i);
            mockLocation.setLatitude(wp.getLatitude());
            mockLocation.setLongitude(wp.getLongitude());
            long time = wp.getTime().getTime();
            mockLocation.setTime(time);
            float bearing;
            if (i != 0) {
                bearing = locations.get(i - 1).bearingTo(mockLocation);
                double elapsed_time = (mockLocation.getTime()
                        - locations.get(i - 1).getTime()) / 1000;
                double distance = locations.get(i - 1).distanceTo(mockLocation);
                float speed = (float) ((distance / elapsed_time));
                mockLocation.setSpeed(speed);
            } else {
                Location second = new Location("mock");
                second.setLatitude(wp.getLatitude());
                second.setLongitude(wp.getLongitude());
                bearing = mockLocation.bearingTo(second);;
            }
            mockLocation.setBearing(bearing);
            mockLocation.setAccuracy(1.0f);
            locations.add(mockLocation);
        }
        List<Waypoint> pts = locationsToWaypoints(locations);
        try {
            getRoute(pts);
        } catch (ServicesException e) {
            e.printStackTrace();
        }
        //todo: callback on route to start location mock from here
        simulation_locations = locations;
    }


    /**
     * Reading waypoints from a gpx file (ExternalStorageDirectory/waypoints.gpx) that are used
     * to request a route from the mapbox API
     * @return List of Waypoints
     */
    /**
    private List<Waypoint> getSimulationRouteWaypoints() {
        List<com.hs.gpxparser.modal.Waypoint> waypoints = loadGPX("waypoints");
        List<Waypoint> mapbox_wp = new ArrayList<>();
        for (int i=0; i<waypoints.size(); i++) {
            com.hs.gpxparser.modal.Waypoint current_wp = waypoints.get(i);
            mapbox_wp.add(new Waypoint(current_wp.getLongitude(), current_wp.getLatitude()));
        }
        return mapbox_wp;
    }*/

    private List<com.hs.gpxparser.modal.Waypoint> loadGPX(String filename) {
        List<com.hs.gpxparser.modal.Waypoint> waypoints = new ArrayList<>();
        File file = new File(Environment.getExternalStorageDirectory(), filename + ".gpx");
        GPXParser p = new GPXParser();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            GPX gpx = p.parseGPX(in);
            HashSet<com.hs.gpxparser.modal.Waypoint> wps = gpx.getWaypoints();
            Iterator it = wps.iterator();
            while (it.hasNext()) {
                waypoints.add((com.hs.gpxparser.modal.Waypoint) it.next());
            }
            Collections.sort(waypoints, new Comparator<com.hs.gpxparser.modal.Waypoint>() {
                @Override
                public int compare(com.hs.gpxparser.modal.Waypoint m1, com.hs.gpxparser.modal.Waypoint m2) {
                    return m1.getTime().compareTo(m2.getTime());
                }

            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return waypoints;
    }


    @Deprecated //use getSimulationRouteWaypoints instead
    private List<Waypoint> locationsToWaypoints(List<Location> locations) {
        List<com.mapbox.services.directions.v4.models.Waypoint> waypoints = new ArrayList<>();
        int max_waypoints = 7;
        int steps = (int) Math.ceil(locations.size() / max_waypoints);
        Location l;
        for (int i = 0; waypoints.size() < max_waypoints - 1; i += steps) {
            l = locations.get(i);
            waypoints.add(new Waypoint(l.getLongitude(), l.getLatitude()));
        }
        l = locations.get(locations.size() - 1);
        waypoints.add(new Waypoint(l.getLongitude(), l.getLatitude()));
        return waypoints;
    }


    public void mockLocations(final List<Location> locations) {
        simulationHandler = new Handler(Looper.getMainLooper());
        simulationHandler.post(new Runnable() {
            @Override
            public void run() {
                //Looper.prepare();
                Location first = locations.get(0);
                final Location previous;
                mock(first);
                previous = first;
                for (int i = 1; i < locations.size(); i+=2) {
                    final Location current = locations.get(i);
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            isSimulationRunning = true;
                            mock(current);
                        }
                    };

                    long diff = current.getTime() - first.getTime();
                    simulationHandler.postDelayed(runnable, diff);
                }

                final Location last = locations.get(locations.size()-1);
                Runnable runnable2 = new Runnable() {
                    @Override
                    public void run() {
                        isSimulationRunning = true;
                        mock(last);
                    }
                };
                long diff = last.getTime() - first.getTime();
                simulationHandler.postDelayed(runnable2, diff);
            }
        });
    }


    private void mock(final Location mockLocation) {
        com.mapzen.android.lost.api.LocationServices.FusedLocationApi.setMockMode(true);
        com.mapzen.android.lost.api.LocationServices.FusedLocationApi.setMockLocation(mockLocation);
    }

    /**
     * http://stackoverflow.com/a/34343101/3083611
     */
    private void checkPermissions() {

        if(!hasPermissions(this.getApplicationContext(), PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }

    /**
     * http://stackoverflow.com/a/34343101/3083611
     * @param context
     * @param permissions
     * @return
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean awaitPermissionRequest(long time_of_permission_request) {
        long time_to_wait = 15;
        long _time_of_permission_request = time_of_permission_request / (1000);
        return (System.currentTimeMillis() / (1000) - _time_of_permission_request)
                < time_to_wait
                && !hasPermissions(this.getApplicationContext(), PERMISSIONS);
    }

    /**
     * Drawing a transparent border frame to make distuingishing between on screen and off screen
     * landmarks easier
     * Setting up two polygons as Mapbox SDK does not support Polygons with holes yet
     * todo: update when above is possible with the SDK
     */
    private Runnable visualizeBorderFrame = new Runnable() {

        @Override
        public void run() {
            frameStatus = true;

            Projection projection = mMapboxMap.getProjection();
            VisibleRegion bbox = projection.getVisibleRegion();
            double overflow = 250; //overflow in px to avoid empty space close to frame
            double width = 0.11 * (
                    SpatialUtils.pointF2Coordinate(projection.toScreenLocation(bbox.farLeft))
                            .distance(SpatialUtils.pointF2Coordinate(
                                    projection.toScreenLocation(bbox.farRight))));
            width += overflow;

            List<Polygon> previous_frame = new ArrayList<>();
            if (frame != null) {
                previous_frame = frame;
            }

            PointF farLeft = projection.toScreenLocation(bbox.farLeft);
            farLeft.x -= overflow;
            farLeft.y -= overflow;
            PointF farRight = projection.toScreenLocation(bbox.farRight);
            farRight.x += overflow;
            farRight.y -= overflow;
            PointF nearRight = projection.toScreenLocation(bbox.nearRight);
            nearRight.x += overflow;
            nearRight.y += overflow;
            PointF nearLeft = projection.toScreenLocation(bbox.nearLeft);
            nearLeft.x -= overflow;
            nearLeft.y += overflow;

            PointF top_left_left_p = new PointF(farLeft.x, (float) (farLeft.y + width));
            LatLng top_left_left = projection.fromScreenLocation(top_left_left_p);

            PointF top_left_right_p = new PointF((float) (farLeft.x + width), (float) (farLeft.y + width));
            LatLng top_left_right = projection.fromScreenLocation(top_left_right_p);

            PointF top_right_right_p = new PointF(farRight.x, (float) (farRight.y + width));
            LatLng top_right_right = projection.fromScreenLocation(top_right_right_p);

            PointF top_right_left_p = new PointF((float) (farRight.x - width), (float) (farRight.y + width));
            LatLng top_right_left = projection.fromScreenLocation(top_right_left_p);

            /**
            PointF bottom_right_right_p = new PointF(nearRight.x, (float) (nearRight.y - width));
            LatLng bottom_right_right = projection.fromScreenLocation(bottom_right_right_p);*/

            PointF bottom_right_left_p = new PointF((float) (nearRight.x - width), (float) (nearRight.y - width));
            LatLng bottom_right_left = projection.fromScreenLocation(bottom_right_left_p);

            /**
            PointF bottom_left_left_p = new PointF(nearLeft.x, (float) (nearLeft.y - width));
            LatLng bottom_left_left = projection.fromScreenLocation(bottom_left_left_p);*/

            PointF bottom_left_right_p = new PointF((float) (nearLeft.x + width), (float) (nearLeft.y - width));
            LatLng bottom_left_right = projection.fromScreenLocation(bottom_left_right_p);


            List<LatLng> top = new ArrayList<>();
            top.add(projection.fromScreenLocation(farLeft));
            top.add(projection.fromScreenLocation(farRight));
            top.add(top_right_right);
            top.add(top_left_left);

            List<LatLng> bottom = new ArrayList<>();
            bottom.add(top_left_left);
            bottom.add(top_left_right);
            bottom.add(bottom_left_right);
            bottom.add(bottom_right_left);
            bottom.add(top_right_left);
            bottom.add(top_right_right);
            bottom.add(top_right_right);
            bottom.add(projection.fromScreenLocation(nearRight));
            bottom.add(projection.fromScreenLocation(nearLeft));

            List<PolygonOptions> frameOptions = new ArrayList<>();
            frameOptions.add(new PolygonOptions().addAll(top).alpha(0.1f));
            frameOptions.add(new PolygonOptions().addAll(bottom).alpha(0.1f));

            frame = (mMapboxMap.addPolygons(frameOptions));
            if (previous_frame != null) mMapboxMap.removeAnnotations(previous_frame);
            frameHandler.postDelayed(this, FRAME_UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (frameStatus) {
            frameHandler.removeCallbacks(visualizeBorderFrame);
            if (mMapboxMap != null) {
                mMapboxMap.removeAnnotations(frame);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (frameStatus) {
            frameHandler.removeCallbacks(visualizeBorderFrame);
            if (mMapboxMap != null) {
                mMapboxMap.removeAnnotations(frame);
            }
        }
        mapView.onPause();

    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        if (mMapboxMap != null) {
            mMapboxMap.removeAnnotations();
            if (toDrawFrame) {
                frameHandler.post(visualizeBorderFrame);
            }
        }
        if (currentRoute != null) {
            drawRoute(currentRoute);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (frameStatus) {
            frameHandler.removeCallbacks(visualizeBorderFrame);
            if (mMapboxMap != null) {
                mMapboxMap.removeAnnotations(frame);
            }
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_destination:

                return true;

            case R.id.action_info:

                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private void landmarkSetup() {
        String landmark_set = prefs.getString("landmark_location_preference", "");
        switch (landmark_set) {
            case "0": //Münster
                Landmark dome = new Landmark("dome", 7.625776, 51.962999, false, 0, this);
                Landmark train_station = new Landmark("station", 7.634615, 51.956593, false, 0, this);
                Landmark buddenturm = new Landmark("tower", 7.623099, 51.966311, false, 0, this);
                Landmark kapuzinerkloster = new Landmark("church", 7.606970, 51.970665, true, 0, this);
                Landmark castle = new Landmark("castle", 7.613166, 51.963613, false, 0, this);
                Landmark zoo = new Landmark("zoo", 7.586884, 51.948622, false, 0, this);

                landmarks.add(dome);
                landmarks.add(buddenturm);
                landmarks.add(kapuzinerkloster);
                landmarks.add(castle);
                landmarks.add(train_station);
                landmarks.add(zoo);
                gps_track = "ms";

                break;

            case "1": //Meckenheim
                Landmark fire_station = new Landmark("fire_station", 7.041904, 50.621435, false, 0, this);
                Landmark church = new Landmark("church", 7.055108, 50.630385, false, 0, this);
                Landmark supermarket = new Landmark("supermarket", 7.019256, 50.627224, false, 0, this);
                Landmark hospital = new Landmark("hospital", 7.023028, 50.619465, false, 0, this);
                Landmark factory = new Landmark("factory", 7.036848, 50.633346, false, 0, this);

                //on screen only:
                Landmark gym = new Landmark("gym", 7.038259, 50.625125, true, 0, this);
                Landmark parking = new Landmark("parking", 7.052686, 50.629195, true, 0, this);

                landmarks.add(gym);
                landmarks.add(parking);

                landmarks.add(supermarket);
                landmarks.add(church);
                landmarks.add(fire_station);
                landmarks.add(hospital);
                landmarks.add(factory);

                gps_track = "meckenheim";
                break;


            case "2": //Rheinbach
                //on screen only
                Landmark tower = new Landmark("tower", 6.946510, 50.624829, true, 0, this);
                Landmark gas_station = new Landmark("gas_station", 6.959922, 50.625273, true, 0, this);
                // off screen
                //Landmark car_dealership = new Landmark("workshop", 6.963145, 50.626406, false, 0, this);
                Landmark car_dealership = new Landmark("workshop", 6.963635,50.625962, false, 0, this);
                Landmark swimming = new Landmark("swimming", 6.933592,50.619270, false, 0, this);
                Landmark university = new Landmark("university", 6.947847, 50.632435, false, 0, this);
                Landmark zoo_rhb = new Landmark("zoo", 6.931504, 50.627639, false, 0, this);
                Landmark museum = new Landmark("museum", 6.951842, 50.620205, false, 0, this);


                landmarks.add(gas_station);
                landmarks.add(tower);
                landmarks.add(car_dealership);
                landmarks.add(swimming);
                landmarks.add(museum);
                landmarks.add(university);
                landmarks.add(zoo_rhb);
                gps_track = "rheinbach";
                break;
        }
    }

}
