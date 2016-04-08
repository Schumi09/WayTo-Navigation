package ifgi.wayto_navigation;

import android.Manifest;
import android.app.ActionBar;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.directions.DirectionsCriteria;
import com.mapbox.directions.MapboxDirections;
import com.mapbox.directions.service.models.DirectionsResponse;
import com.mapbox.directions.service.models.DirectionsRoute;
import com.mapbox.directions.service.models.Waypoint;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
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

import ifgi.wayto_navigation.model.Landmark;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class MainActivity extends android.support.v7.app.ActionBarActivity implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

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
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 5000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;


    protected GoogleApiClient mGoogleApiClient;

    protected LocationRequest mLocationRequest;
    protected Location mCurrentLocation;
    protected Location mCurrentLocationSnap;
    protected double mCurrentBearing = 361;
    protected final int BEARING_THRESHOLD = 30;
    protected String mLastUpdateTime;
    protected Boolean mRequestingLocationUpdates = true;


    protected List<Landmark> offscreen_landmarks = new ArrayList<>();
    protected List<Polygon> wedges = new ArrayList<>();
    protected List<Marker> wedge_markers = new ArrayList<>();
    private List<Polygon> wedges_old;
    private List<Marker> wedge_markers_old;
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
    protected List<Marker> on_screen_markers = new ArrayList<Marker>();

    protected DirectionsRoute currentRoute = null;
    protected LineString currentJtsRouteLs = null;

    protected android.support.v7.app.ActionBar actionBar;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MAPBOX_ACCESS_TOKEN = getResources().getString(R.string.accessToken);

        final Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setLogo(R.drawable.logo_wayto);
        setSupportActionBar(myToolbar);
        actionBar = getSupportActionBar();


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


        buildGoogleApiClient();
        createLocationRequest();

        /** Create a mapView and give it some properties */
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setStyleUrl("mapbox://styles/schumi91/cimm7mq0i009dzpmckjmo8u4u");
        mapView.onCreate(savedInstanceState);
        toggleFullscreen();
        myToolbar.bringToFront();

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                mMapboxMap = mapboxMap;

                mapboxMap.getUiSettings().setCompassEnabled(false);
                mapboxMap.getUiSettings().setLogoEnabled(false); //needs to be enabled in production
                mapboxMap.getUiSettings().setRotateGesturesEnabled(false);
                mapboxMap.getUiSettings().setAttributionEnabled(false);
                getRoute(origin, destination);
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(51.96937, 7.60937))
                        .zoom(12)
                        .build();

                mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                startLocationUpdates();
                mMapboxMap.setOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(@NonNull LatLng point) {
                        toggleActionBar(actionBar);
                    }
                });
            }
        });
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void toggleActionBar(android.support.v7.app.ActionBar actionBar) {
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
            String name = landmarks.get(i).name;
            landmarks.get(i).setOff_screen_icon(getIcon(getIconID(name)));
        }
    }

    /**
     * onLocationChanged event.
     * Once the location has changed a marker displays the user's current position and updates the
     * mapview to the position as center.
     * todo: distinguish between snap and not snapped location in wedge call
     *
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        if (currentJtsRouteLs != null) {
            mCurrentLocationSnap = snapLocation(mCurrentLocation);
            moveCurrentPositionMarker(mCurrentLocationSnap);
        }

        if (mCurrentBearing == 361) {
            mCurrentBearing = mCurrentLocation.getBearing();
        }

        if (mMapboxMap != null) {

            /**
             * VisibleRegion area = mMapboxMap.getProjection().getVisibleRegion();
             if (bbox !=null){
             mMapboxMap.removePolygon(bbox);
             }
             bbox = mMapboxMap.addPolygon(new PolygonOptions().add(area.farLeft)
             .add(area.farRight).add(area.nearRight).add(area.nearLeft)
             .fillColor(Color.parseColor("#00000000")).strokeColor(Color.parseColor("#990000")));*/

            offscreen_landmarks = new ArrayList<>();

            if (wedges.size() != 0 || on_screen_markers.size() != 0) {
                wedges_old = wedges;
                wedge_markers_old = wedge_markers;
                wedges = new ArrayList<>();
                wedge_markers = new ArrayList<>();
                mMapboxMap.removeAnnotations(on_screen_markers);
                on_screen_markers = new ArrayList<>();

            }

            for (int i = 0; i < landmarks.size(); i++) {
                Landmark l = landmarks.get(i);
                if (!l.isOffScreen(mMapboxMap)) {
                    on_screen_markers.add(mMapboxMap.addMarker(l.drawMarker()));
                } else {
                    offscreen_landmarks.add(l);
                }
            }
            new drawWedgesTask().execute(offscreen_landmarks);
        }
    }

    private class drawWedgesTask extends AsyncTask<List<Landmark>, Void, List<PolygonOptions>> {

        @Override
        protected List<PolygonOptions> doInBackground(List<Landmark>... params) {
            Log.d("Start", ".");
            List<Landmark> ls = params[0];
            List<PolygonOptions> list = new ArrayList<>();
            for (int i = 0; i < ls.size(); i++) {
                list.add(ls.get(i).drawWedge(mMapboxMap));
            }
            return list;
        }

        protected void onPostExecute(List<PolygonOptions> list) {
            removeWedges();
            for (int i = 0; i < list.size(); i++) {
                Polygon wedge = mMapboxMap.addPolygon(list.get(i));
                wedges.add(wedge);
                wedge_markers.add(mMapboxMap.addMarker(
                        offscreen_landmarks.get(i).drawWedgeMarker(wedge)));
            }
        }
    }

    private void removeWedges() {
        if (wedges_old != null && wedge_markers_old != null) {
            mMapboxMap.removeAnnotations(wedges_old);
            mMapboxMap.removeAnnotations(wedge_markers_old);
        }
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
     * Moving mapview to current position
     * Current position as center, bearing from location
     *
     * @param location
     */
    private void moveCurrentPositionMarker(Location location) {

        if (Math.abs(mCurrentBearing - location.getBearing()) > BEARING_THRESHOLD) {
            mCurrentBearing = location.getBearing();
        }

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                .zoom(15)
                .bearing(mCurrentBearing)
                .build();

        MarkerOptions options = new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .icon(getIcon(R.drawable.my_location));

        if (currentPositionMarker != null) {
            mMapboxMap.removeMarker(currentPositionMarker);
            currentPositionMarker = mMapboxMap.addMarker(options);
        } else {
            currentPositionMarker = mMapboxMap.addMarker(options);
        }
        mMapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
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
     * Builds a GoogleApiClient.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    /**
     * Sets up the location request.
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
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
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */


    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        mapView.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://ifgi.wayto_navigation/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://ifgi.wayto_navigation/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        mapView.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        // Stop location updates to save battery
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
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

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to GoogleApiClient");

        if (mCurrentLocation == null) {
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
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }

        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
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
                // User chose the "Settings" item, show the app settings UI...
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
