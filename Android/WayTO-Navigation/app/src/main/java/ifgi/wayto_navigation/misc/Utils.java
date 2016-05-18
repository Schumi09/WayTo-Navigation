package ifgi.wayto_navigation.misc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;

/**
 * Created by Daniel on 17.05.2016.
 */
public class Utils {
    public Icon bmpToIcon(Bitmap bmp, double angle) {
        Icon icon;
        return icon;
    }

    public Drawable bmpToDrawable(Bitmap bmp, double angle, double alpha, Context context) {
        Bitmap bmp_tmp = rotateBitmap(bmp, angle);
        Drawable mIconDrawable = new BitmapDrawable(context.getResources(), bmp_tmp);
        mIconDrawable.setAlpha(((int) Math.round(alpha)));
        return mIconDrawable;
    }

    public Icon updateIcon(Icon icon, double angle, double alpha, Context context) {
        Drawable drawable = new BitmapDrawable(icon.getBitmap());
        IconFactory mIconFactory = IconFactory.getInstance(context);
        return mIconFactory.fromDrawable(drawable);
    }

    public Bitmap rotateBitmap(Bitmap bmp, double angle) {
        Bitmap bmp_tmp;
        Matrix matrix = new Matrix();
        matrix.postRotate((float)angle);
        bmp_tmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        return bmp_tmp;
    }

}
