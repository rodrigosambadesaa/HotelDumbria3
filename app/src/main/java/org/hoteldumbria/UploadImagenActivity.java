package org.hoteldumbria;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;

public class UploadImagenActivity extends AppCompatActivity {
    private boolean internetVerified;
    private ConnectivityAndInternetAccess.Request internetAccessRequest;

    private JsonObjectRequest jsObjRequest;
    private static final String COLA_PETICIONES_LOGIN = "PeticionesPrueba";
    private PeticionesRed peticionesRed;

    private String urlPeticion = WebService.getURL_Usuarios();

    public static String getColaPeticionesLogin() {
        return COLA_PETICIONES_LOGIN;
    }

    public static String getColaPeticionesUploadImagen() {
        return COLA_PETICIONES_UPLOAD_IMAGEN;
    }

    public String getUrlPeticion() {
        return urlPeticion;
    }

    public void setUrlPeticion(String urlPeticion) {
        this.urlPeticion = urlPeticion;
    }

    public JsonObjectRequest getJsObjRequest() {
        return jsObjRequest;
    }

    public void setJsObjRequest(JsonObjectRequest jsObjRequest) {
        this.jsObjRequest = jsObjRequest;
    }

    public PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    public void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    public int getPETICION_SELECCIONAR_IMAGEN() {
        return PETICION_SELECCIONAR_IMAGEN;
    }

    public int getPETICION_SACAR_FOTO() {
        return PETICION_SACAR_FOTO;
    }

    public int getCALIDAD_IMAGEN() {
        return CALIDAD_IMAGEN;
    }

    public int getDIMENSION_REQUERIDA() {
        return DIMENSION_REQUERIDA;
    }

    public Button getBtn_SeleccionaImagen() {
        return btn_SeleccionaImagen;
    }

    public void setBtn_SeleccionaImagen(Button btn_SeleccionaImagen) {
        this.btn_SeleccionaImagen = btn_SeleccionaImagen;
    }

    public Button getBtn_UploadImagen() {
        return btn_UploadImagen;
    }

    public void setBtn_UploadImagen(Button btn_UploadImagen) {
        this.btn_UploadImagen = btn_UploadImagen;
    }

    public Button getBnt_sacarFoto() {
        return bnt_sacarFoto;
    }

    public void setBnt_sacarFoto(Button bnt_sacarFoto) {
        this.bnt_sacarFoto = bnt_sacarFoto;
    }

    public ImageView getImgv_imagen() {
        return imgv_imagen;
    }

    public void setImgv_imagen(ImageView imgv_imagen) {
        this.imgv_imagen = imgv_imagen;
    }

    public TextView getTv_datos_imagen() {
        return tv_datos_imagen;
    }

    public void setTv_datos_imagen(TextView tv_datos_imagen) {
        this.tv_datos_imagen = tv_datos_imagen;
    }

    public ProgressDialog getPd() {
        return pd;
    }

    public void setPd(ProgressDialog pd) {
        this.pd = pd;
    }

    public Bitmap getBitmapImagenSeleccionada() {
        return bitmapImagenSeleccionada;
    }

    public void setBitmapImagenSeleccionada(Bitmap bitmapImagenSeleccionada) {
        this.bitmapImagenSeleccionada = bitmapImagenSeleccionada;
    }

    public String getNombreFicheroSeleccionado() {
        return nombreFicheroSeleccionado;
    }

    public void setNombreFicheroSeleccionado(String nombreFicheroSeleccionado) {
        this.nombreFicheroSeleccionado = nombreFicheroSeleccionado;
    }

    public static class DatosImagenSubida implements Serializable {
        private String url,ruta,nombre_original;
        private long id;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getRuta() {
            return ruta;
        }

        public void setRuta(String ruta) {
            this.ruta = ruta;
        }

        public String getNombre_original() {
            return nombre_original;
        }

        public void setNombre_original(String nombre_original) {
            this.nombre_original = nombre_original;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "DatosImagenSubida{" +
                    "url='" + url + '\'' +
                    ", ruta='" + ruta + '\'' +
                    ", nombre_original='" + nombre_original + '\'' +
                    ", id=" + id +
                    '}';
        }
    }

    private final int PETICION_SELECCIONAR_IMAGEN= 20;
    private final int PETICION_SACAR_FOTO= 30;
    private final int CALIDAD_IMAGEN=80;
    private final int DIMENSION_REQUERIDA=250;

        private static final String COLA_PETICIONES_UPLOAD_IMAGEN = "PeticionesUploadImagen";

    private Button btn_SeleccionaImagen;
    private Button btn_UploadImagen;
    private Button bnt_sacarFoto;
    private ImageView imgv_imagen;
    private TextView tv_datos_imagen;
    private ProgressDialog pd;

    private Bitmap bitmapImagenSeleccionada;
    private String nombreFicheroSeleccionado;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_imagen);

        setPeticionesRed(PeticionesRed.getInstancia(this));

        setBtn_SeleccionaImagen(findViewById(R.id.btn_SeleccionaImagen));
        setBnt_sacarFoto(findViewById(R.id.btn_sacarFoto));
        setBtn_UploadImagen(findViewById(R.id.btn_UploadImagen));
        setTv_datos_imagen(findViewById(R.id.tv_datos_imagen));
        setImgv_imagen(findViewById(R.id.imgv_imagen));


        getBtn_UploadImagen().setEnabled(false);
        setPd(new ProgressDialog(this));
        getPd().setMessage("Subiendo la imagen....");
        getPd().setProgressStyle(ProgressDialog.STYLE_SPINNER);
        getPd().setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (getPeticionesRed() !=null) {  // Finalizamos peticiones pendientes
                    getPeticionesRed().getColaPeticiones().cancelAll(getColaPeticionesUploadImagen());
                }
            }
        });


        getBtn_SeleccionaImagen().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seleccionarImagen();
            }
        });



        getBnt_sacarFoto().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sacarFoto();
            }
        });

        getBtn_UploadImagen().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImagen(MainActivity.getUsuarioRecibido().getNif(), getBitmapImagenSeleccionada(), getNombreFicheroSeleccionado());
            }
        });


    }

    void seleccionarImagen() {
        getTv_datos_imagen().setText("");
        setNombreFicheroSeleccionado("");
        setBitmapImagenSeleccionada(null);
        Intent i;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
        }
        i.setType("image/*");
        startActivityForResult(i, getPETICION_SELECCIONAR_IMAGEN());
    }

    void sacarFoto() {
        getTv_datos_imagen().setText("");
        setNombreFicheroSeleccionado("");
        setBitmapImagenSeleccionada(null);
        try {
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(it, getPETICION_SACAR_FOTO()); // Para obtener la imagen con calidad completa hay que enviar commo parámetro extra la ruta hacia un fichero.
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getApplicationContext(),"Error al lanzar la cámara", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ( requestCode == getPETICION_SELECCIONAR_IMAGEN() && resultCode == RESULT_OK && data != null) {
            Uri imagenUri = data.getData();
            if (imagenUri == null) {
                mostrarErrorImagen();
                return;
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT
                        && (data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(
                            imagenUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                }
                setNombreFicheroSeleccionado(obtenerNombre(imagenUri));
                setBitmapImagenSeleccionada(decodificarImagen(imagenUri, getDIMENSION_REQUERIDA()));
            } catch (Exception error) {
                Log.e("UploadImagen", "No se pudo leer la imagen seleccionada", error);
                mostrarErrorImagen();
                return;
            }
            getImgv_imagen().setImageBitmap(getBitmapImagenSeleccionada());
            getBtn_UploadImagen().setEnabled(getBitmapImagenSeleccionada() != null);
        }
        else if (requestCode == getPETICION_SACAR_FOTO() && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap bitmap = extras == null ? null : (Bitmap) extras.get("data");
            if (bitmap == null) {
                mostrarErrorImagen();
                return;
            }
            setBitmapImagenSeleccionada(redimensionar(bitmap, getDIMENSION_REQUERIDA()));
            setNombreFicheroSeleccionado("foto_perfil_" + System.currentTimeMillis() + ".png");
            getImgv_imagen().setImageBitmap(getBitmapImagenSeleccionada());
            getBtn_UploadImagen().setEnabled(true);
        }

    }

    private Bitmap decodificarImagen(Uri uri, int maxDimension) throws IOException {
        ContentResolver resolver = getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDimension * 2
                || bounds.outHeight / sample > maxDimension * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream stream = resolver.openInputStream(uri)) {
            Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
            if (decoded == null) {
                throw new IOException("El proveedor no devolvió una imagen");
            }
            return redimensionar(decoded, maxDimension);
        }
    }

    private Bitmap redimensionar(Bitmap source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min(1f, (float) maxDimension / Math.max(width, height));
        if (scale == 1f) {
            return source;
        }
        return Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private String obtenerNombre(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        }
        return name == null || name.trim().isEmpty() ? "foto_perfil.png" : name;
    }

    private void mostrarErrorImagen() {
        setBitmapImagenSeleccionada(null);
        getBtn_UploadImagen().setEnabled(false);
        Toast.makeText(this, "No se pudo abrir la imagen", Toast.LENGTH_LONG).show();
    }

    /*
     * El método toma Bitmap como argumento
     * luego devolverá el array de bytes [] para el mapa de bits dado
     * aquí estamos usando Compresión PNG con calidad% de calidad
     * puedes dar calidad entre 0 y 100
     * 0 significa peor calidad
     * 100 significa la mejor calidad
     * */
    public static byte[] getBytesPNGDeBitmap(Bitmap bitmap, int calidad /* 80 es una buena medida */) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, calidad, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }



    void uploadImagen(String identificadorUsuario, Bitmap bitmap, String nombreFichero) {

        Intent it_recibido = getIntent();
        if (identificadorUsuario == null) {
            Toast.makeText(getApplicationContext(),"Identificador de usuario no recibido", Toast.LENGTH_LONG).show();
            return;
        }

        if (bitmap==null || nombreFichero==null){
            Toast.makeText(getApplicationContext(),"Imagen no recibida", Toast.LENGTH_LONG).show();
            return;
        }

        getPd().show();

        if (!internetVerified) {
            final String nombreOriginal = nombreFichero;
            internetAccessRequest = Utilidades.comprobarAccesoAInternet(this, (reachable, reachedHost) -> {
                if (reachable) {
                    internetVerified = true;
                    uploadImagen(identificadorUsuario, bitmap, nombreOriginal);
                } else {
                    getPd().dismiss();
                    Toast.makeText(this, "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        internetVerified = false;

        HashMap<String,String> parametros = new HashMap<>();

        // Cambiamos la extension a PNG
        int i = nombreFichero.lastIndexOf('.');
        if (i>0) nombreFichero= nombreFichero.substring(0,i) + ".png";
        else nombreFichero = nombreFichero + ".png";

        VolleyMultipartRequest peticionUploadFichero = new VolleyMultipartRequest(
                Request.Method.POST,
                WebService.getAvatarUrl(),
                parametros,
                nombreFichero,
                Utilidades.getBytesPNGDeBitmap(bitmap, getCALIDAD_IMAGEN()),
                8000,
                response -> {
                    getPd().dismiss();
                    getBtn_UploadImagen().setEnabled(false);
                    getImgv_imagen().setImageBitmap(null);
                    MainActivity.getUsuarioRecibido().setImagen(WebService.getAvatarUrl());
                    Toast.makeText(this, "Imagen de perfil actualizada", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                },
                error -> {
                    getPd().dismiss();
                    Log.e("UploadAvatar", "Subida rechazada", error);
                    Toast.makeText(this, "No se pudo subir la imagen", Toast.LENGTH_SHORT).show();
                });

        peticionUploadFichero.setTag(getColaPeticionesUploadImagen()); // nombramos la petición
        getPeticionesRed().anhadirPeticionACola(peticionUploadFichero); // Lanzamos la petición
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
        if (getPeticionesRed() !=null) {  // Finalizamos peticiones pendientes
            getPeticionesRed().getColaPeticiones().cancelAll(getColaPeticionesUploadImagen());
        }

    }
}
