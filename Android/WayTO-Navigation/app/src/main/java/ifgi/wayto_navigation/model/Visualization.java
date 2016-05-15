package ifgi.wayto_navigation.model;

import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.List;

/**
 * Created by Daniel on 15.05.2016.
 */
public abstract class Visualization{
    private List<Annotation> visualization;
    public abstract List<Annotation> getVisualization();
    public abstract void remove(MapboxMap mapboxMap);
}