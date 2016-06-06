package ifgi.wayto_navigation.model;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.Gravity;

import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.ArrayList;
import java.util.List;

import ifgi.wayto_navigation.ImageUtils;

/**
 * Created by Daniel on 06.06.2016.
 */
public class WiFiPointer extends Visualization{

    private List<Annotation> visualization;
    private LatLng onScreenAnchor;
    private Landmark landmark;
    private Context context;
    private static int step = 500; //meter

    public WiFiPointer(MapboxMap map, Landmark landmark, Context context) {
        this.context = context;
        this.visualization = new ArrayList<>();
        this.landmark = landmark;
        this.onScreenAnchor = this.landmark.onScreenAnchor(map);
        setVisualization(map);
    }

    @Override
    public List<Annotation> getVisualization() {
        return this.visualization;
    }

    @Override
    public void remove(MapboxMap mapboxMap) {
        mapboxMap.removeAnnotations(this.getVisualization());
    }

    private void setVisualization(MapboxMap map) {
        float angle = (float) this.landmark.heading(
                this.onScreenAnchor, this.landmark.getLocationLatLng());;
        double distance = this.landmark.getLocationLatLng().distanceTo(this.onScreenAnchor);
        int arrow_number = (int) (distance / this.step);
        arrow_number++;
        if (arrow_number > 10) { arrow_number = 10;}
        String drawable_name = "landmark_wifi_" + arrow_number;
        int drawable_id = ImageUtils.getIconID(drawable_name, this.context);
        Log.d("ArrowNUMBER", drawable_name);
        Drawable[] layers = new Drawable[2];
        layers[0] = ImageUtils.rotateIconToDrawable(this.landmark.getOff_screen_icon(), -angle);
        layers[1] = this.context.getDrawable(drawable_id);
        layers[1].setAlpha(255 - (25 * arrow_number - 1));
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        layerDrawable.setLayerGravity(0, Gravity.CENTER);
        layerDrawable.setLayerGravity(1, Gravity.CENTER);
        Icon icon = IconFactory.getInstance(this.context).fromDrawable(layerDrawable.getCurrent());
        MarkerView markerView;
        MarkerViewOptions markerViewOptions = new MarkerViewOptions();
        markerViewOptions.icon(icon);
        markerViewOptions.rotation((int) angle); //todo: float tomorrow
        markerViewOptions.position(onScreenAnchor);
        markerViewOptions.anchor(0.5f, 0.5f); //center
        markerViewOptions.flat(true);
        markerView = map.addMarker(markerViewOptions);
        this.visualization.add(markerView);
    }
}
