package ifgi.wayto_navigation;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationServices;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ifgi.wayto_navigation.model.Globals;
import ifgi.wayto_navigation.model.Landmark;
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
    protected Boolean mRequestingLocationUpdates = true;
    protected SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener;

    public static final String VISUALIZATION_TYPE_KEY = "checkbox_visualization_type_preference";
    protected List<Landmark> offscreen_landmarks = new ArrayList<>();

    /**
     * Münster route waypoints
     */
    protected Waypoint origin = new Waypoint(7.61964, 51.95324);
    protected Waypoint destination = new Waypoint(7.62478, 51.96547);

    /**
     * Münster Landmarks
     */
    protected Landmark dome = new Landmark("dome", 7.625776, 51.962999);
    protected Landmark train_station = new Landmark("station", 7.634615, 51.956593);
    protected Landmark buddenturm = new Landmark("buddenturm", 7.623099, 51.966311);
    protected Landmark kapuzinerkloster = new Landmark("kapuzinerkloster", 7.606970, 51.970665);
    protected Landmark castle = new Landmark("castle", 7.613166, 51.963613);
    protected Landmark zoo = new Landmark("zoo", 7.586884, 51.948622);
    protected List<Landmark> landmarks = new ArrayList<Landmark>();

    protected DirectionsRoute currentRoute = null;
    protected LineString currentJtsRouteLs = null;

    protected ActionBar actionBar;

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        globals = Globals.getInstance();
        mCurrentPositionIcon = getIcon(R.drawable.position_marker);
        MAPBOX_ACCESS_TOKEN = getResources().getString(R.string.accessToken);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setLogo(R.drawable.logo_wayto);
        setSupportActionBar(myToolbar);
        actionBar = getSupportActionBar();

        //todo: remove when included in MAS
        Locale locale = new Locale("en_US");
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());

        landmarks.add(dome);
        landmarks.add(buddenturm);
        landmarks.add(kapuzinerkloster);
        landmarks.add(castle);
        landmarks.add(train_station);
        landmarks.add(zoo);

        setIcons();

        /** Create a mapView and give it some properties */
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setStyleUrl("mapbox://styles/schumi91/cimm7mq0i009dzpmckjmo8u4u");
        mapView.onCreate(savedInstanceState);
        toggleFullscreen();
        LocationServices.getLocationServices(this).toggleGPS(true);
        myToolbar.bringToFront();


        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                mMapboxMap = mapboxMap;

                mMapboxMap.setOnMyLocationChangeListener(new MapboxMap.OnMyLocationChangeListener() {
                    @Override
                    public void onMyLocationChange(@Nullable Location location) {
                        if (location != null) {
                            onLocationChanged(location);
                        }
                    }
                });

                mMapboxMap.setOnCameraChangeListener(new MapboxMap.OnCameraChangeListener() {

                    @Override
                    public void onCameraChange(CameraPosition position) {
                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());



                        if (mMapboxMap != null && mapboxMap != null) {
                            //MapboxMap map_temp = mMapboxMap;
                            /**
                             bbox = mMapboxMap.addPolygon(new PolygonOptions().add(area.farLeft)
                             .add(area.farRight).add(area.nearRight).add(area.nearLeft)
                             .fillColor(Color.parseColor("#00000000")).strokeColor(Color.parseColor("#990000")));
                             */


                            offscreen_landmarks = new ArrayList<>();


                            for (int i = 0; i < landmarks.size(); i++) {
                                Landmark l = landmarks.get(i);
                                l.removeVisualization(mMapboxMap);
                                if (!l.isOffScreen(mMapboxMap)) {
                                    l.drawOnScreenMarker(mMapboxMap);
                                } else {
                                    offscreen_landmarks.add(l);
                                }
                            }

                            String visualisationType = sharedPref.getString(VISUALIZATION_TYPE_KEY, "");
                            boolean tp_help = true;
                            for (int i = 0; i < offscreen_landmarks.size(); i++) {
                                Landmark lm = offscreen_landmarks.get(i);
                                switch (visualisationType) {
                                    case "0": //Wedges
                                        lm.drawWedge(mMapboxMap, getApplicationContext());

                                        break;
                                    case "1": //Tangible Pointer
                                        if (tp_help) {
                                            globals.setOnScreenFrameCoords(Landmark.onScreenFrame(
                                                    Landmark.getBboxPolygonCoordinates(mMapboxMap)));
                                            List<Landmark.OnScreenAnchor> onScreenAnchors = Landmark.onScreenAnchors(
                                                    globals.getOnScreenFrameCoords());
                                            globals.setOnScreenAnchors(onScreenAnchors);
                                            tp_help = false;
                                        }
                                        lm.drawTangiblePointer(mMapboxMap, getApplicationContext());
                                        break;

                                    default:
                                        break;
                                }
                            }
                        }
                    }
                });
                //startLocationUpdates();
                mapboxMap.getUiSettings().setCompassEnabled(false);
                mapboxMap.getUiSettings().setLogoEnabled(false); //needs to be enabled in production
                mapboxMap.getUiSettings().setRotateGesturesEnabled(false);
                mapboxMap.getUiSettings().setAttributionEnabled(false);
                try {
                    getRoute();
                } catch (ServicesException e) {
                    e.printStackTrace();
                }
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(51.96937, 7.60937))
                        .zoom(12)
                        .build();

                mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

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
                            mapboxMap.removeAnnotations();
                            if (currentRoute != null) {
                                drawRoute(currentRoute);
                            }
                        }
                    }
                };
                prefs.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
                mapView.setKeepScreenOn(true);
            }
        });
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
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


    private void setIcons() {
        int i;
        for (i = 0; i < landmarks.size(); i++) {
            String name = landmarks.get(i).getName();
            landmarks.get(i).setOff_screen_icon(getIcon(getIconID(name)));
        }
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

        Log.i("location", location.toString());

        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        if (mCurrentBearing == 361) {
            mCurrentBearing = mCurrentLocation.getBearing();
        }

        if (currentJtsRouteLs != null) {
            mCurrentLocationSnap = snapLocation(mCurrentLocation);
            moveCurrentPositionMarker(mCurrentLocationSnap);
        }

        if (Math.abs(mCurrentBearing - mCurrentLocation.getBearing()) > BEARING_THRESHOLD) {
            mCurrentBearing = mCurrentLocation.getBearing();
        }

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))
                .zoom(15)
                .bearing(mCurrentBearing)
                .tilt(0)
                .build();
        if (mMapboxMap != null) {
            mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    private void getRoute() throws ServicesException {
        ArrayList<Waypoint> positions = new ArrayList<>();
        positions.add(origin);
        positions.add(destination);
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

                    // Print some info about the route
                    currentRoute = response.body().getRoutes().get(0);
                    drawRoute(currentRoute);
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
                .color(Color.parseColor("#3887be"));
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
     * shows current user position
     * @param location
     */
    private void moveCurrentPositionMarker(Location location) {

        if (mMapboxMap == null) {return;}

        if (currentPositionMarker != null) {
            currentPositionMarker.setPosition(
                    new LatLng(location.getLatitude(), location.getLongitude()));
        } else {
            MarkerOptions options = new MarkerOptions()
                    .position(new LatLng(location.getLatitude(), location.getLongitude()))
                    .icon(mCurrentPositionIcon);
            currentPositionMarker = mMapboxMap.addMarker(options);
        }
    }


    private int getIconID(String name) {
        try {
            return getResources().getIdentifier(name, "drawable", getApplicationContext().getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public Icon getIcon(int id) {
        IconFactory mIconFactory = IconFactory.getInstance(this);
        Drawable mIconDrawable = ContextCompat.getDrawable(this, id);
        return mIconFactory.fromDrawable(mIconDrawable);
    }


    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        //mMapboxMap.setMyLocationEnabled(true);
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        if (mMapboxMap != null) {
            mMapboxMap.removeAnnotations();

        }
        if (currentRoute != null) {
            drawRoute(currentRoute);
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

}
