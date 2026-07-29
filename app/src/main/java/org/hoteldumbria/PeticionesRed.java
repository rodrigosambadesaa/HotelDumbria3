package org.hoteldumbria;


import android.content.Context;
import android.graphics.Bitmap;

import androidx.collection.LruCache;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;


public class PeticionesRed {
    private static PeticionesRed mInstance;
    private RequestQueue mcolaPeticiones;
    private ImageLoader mImageLoader;
    private static Context mCtx;

    private PeticionesRed(Context context) {
        setmCtx(context);
        setMcolaPeticiones(getColaPeticiones());

        setmImageLoader(new ImageLoader(getMcolaPeticiones(),
                new ImageLoader.ImageCache() {
                    private final LruCache<String, Bitmap>
                            cache = new LruCache<String, Bitmap>(20);

                    @Override
                    public Bitmap getBitmap(String url) {
                        return cache.get(url);
                    }

                    @Override
                    public void putBitmap(String url, Bitmap bitmap) {
                        cache.put(url, bitmap);
                    }
                }));
    }

    public static synchronized PeticionesRed getInstancia(Context context) {
        if (getmInstance() == null) {
            setmInstance(new PeticionesRed(context));
        }
        return getmInstance();
    }

    public static PeticionesRed getmInstance() {
        return mInstance;
    }

    public static void setmInstance(PeticionesRed mInstance) {
        PeticionesRed.mInstance = mInstance;
    }

    public static Context getmCtx() {
        return mCtx;
    }

    public static void setmCtx(Context mCtx) {
        PeticionesRed.mCtx = mCtx;
    }

    public RequestQueue getColaPeticiones() {
        if (getMcolaPeticiones() == null) {
            /* getApplicationContext() es importante, evita perder el objeto si el
              contexto es activity o BroadcastReceiver
             */
            setMcolaPeticiones(Volley.newRequestQueue(getmCtx().getApplicationContext()));
        }
        return getMcolaPeticiones();
    }

    public <T> void anhadirPeticionACola(Request<T> req) {
        getColaPeticiones().add(req);
    }

    public ImageLoader getImageLoader() {
        return getmImageLoader();
    }

    public RequestQueue getMcolaPeticiones() {
        return mcolaPeticiones;
    }

    public void setMcolaPeticiones(RequestQueue mcolaPeticiones) {
        this.mcolaPeticiones = mcolaPeticiones;
    }

    public ImageLoader getmImageLoader() {
        return mImageLoader;
    }

    public void setmImageLoader(ImageLoader mImageLoader) {
        this.mImageLoader = mImageLoader;
    }
}
