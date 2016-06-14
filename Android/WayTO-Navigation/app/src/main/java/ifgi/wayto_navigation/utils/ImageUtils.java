package ifgi.wayto_navigation.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;

import ifgi.wayto_navigation.model.Landmark;

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

    public static Drawable rotateBitmapToDrawable(Bitmap source, float rotation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Drawable drawable = new BitmapDrawable(Resources.getSystem(),
                Bitmap.createBitmap(
                        source, 0, 0, source.getWidth(), source.getHeight(), matrix, true));

        return drawable;
    }

    public static Drawable rotateIconToDrawable(Icon icon, float rotation) {
        return rotateBitmapToDrawable(icon.getBitmap(), rotation);
    }
}
