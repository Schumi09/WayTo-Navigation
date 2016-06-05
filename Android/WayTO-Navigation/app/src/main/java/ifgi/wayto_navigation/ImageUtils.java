package ifgi.wayto_navigation;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;

/**
 * Created by Daniel Schumacher on 05.06.2016.
 * Provides some useful methods to deal with different types
 * of images like Drawables, Icons etc.
 */
public class ImageUtils {

    public static int getIconID(String name, Context context) {
        try {
            return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static Icon getIcon(int id, Context context) {
        IconFactory mIconFactory = IconFactory.getInstance(context);
        return mIconFactory.fromResource(id);
    }

    public static int pxToDp(Context context, int px) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        float logicalDensity = metrics.density;
        return (int) Math.ceil(logicalDensity / px);
    }

    public static int dpToPx(Context context, int dp) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        float logicalDensity = metrics.density;
        return (int) Math.ceil(dp * logicalDensity);
    }
}
