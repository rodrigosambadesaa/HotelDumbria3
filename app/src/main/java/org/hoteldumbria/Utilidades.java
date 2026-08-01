package org.hoteldumbria;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utilidades {

    public interface InternetAccessCallback {
        void onResult(boolean reachable, String reachedEndpoint);
    }

    private static final int LONGITUD_MINIMA_CLAVE = 8;

    public static boolean validarNif(String nif) {
        boolean correcto;
        Pattern pattern = Pattern.compile("(\\d{1,8})([TRWAGMYFPDXBNJZSQVHLCKEtrwagmyfpdxbnjzsqvhlcke])");
        Matcher matcher = pattern.matcher(nif);
        if (matcher.matches()) {
            String letra = matcher.group(2);
            String letras = "TRWAGMYFPDXBNJZSQVHLCKE";
            int index = Integer.parseInt(matcher.group(1));
            index = index % 23;
            String reference = letras.substring(index, index + 1);
            if (reference.equalsIgnoreCase(letra)) {
                correcto = true;
            } else {
                correcto = false;
            }
        } else {
            correcto = false;
        }
        return correcto;
    }

    public static void ocultarTeclado(Context c, View v) {
        InputMethodManager inputManager = (InputMethodManager)
                c.getSystemService(Context.INPUT_METHOD_SERVICE);

        if (inputManager != null) {
            inputManager.hideSoftInputFromWindow(v.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /* Genera los parámatros a añadir a una url teniendo en cuenta símbolos ?, & y condificación utf-8 */
    public static String generaParametrosURL(HashMap < String, String > listaParametros) {

        String parametrosURL = "";

        if (!listaParametros.isEmpty()) {
            parametrosURL += "?";
            try {
                Iterator it = listaParametros.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry p = (Map.Entry) it.next();

                    String parametroString = p.getKey() + "=" +
                            URLEncoder.encode(p.getValue().toString(), "UTF-8");
                    if (parametrosURL.length() > 1) {
                        parametrosURL += "&" + parametroString;
                    } else {
                        parametrosURL += parametroString;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return parametrosURL;
    }





    /**** Otros métodos útiles ****/

    // Oculta el Teclado virtual
    public static void ocultarTeclado(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                activity.getCurrentFocus().getWindowToken(), 0);
    }

    // Prerequisito: Usar Justo antes de setContentView
    public static void eliminarBarraSuperior(AppCompatActivity a) {
        a.requestWindowFeature(Window.FEATURE_NO_TITLE); // Sin ActionBar
        ActionBar actionBar = a.getSupportActionBar(); // Con ActionBar
        if (actionBar != null) actionBar.hide();
    }

    // Evita que la Activity se cierre y se vuelva a abrir (perdiendo los datos en memoria) cuando el dispositivo gira a posición horiontal
    public static void evitarCambioOrientacionApaisado(Activity a) {
        a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }


    public static void visualizar_url(Context contexto, String url) {
        Uri uri_url = Uri.parse(url);
        Intent it = new Intent(Intent.ACTION_VIEW, uri_url);
        contexto.startActivity(it);
    }


    public static void enviaEmail(Context contexto, File fichero, String destinatario, String asunto, String cuerpo) {
        Uri uri_fichero = Uri.fromFile(fichero); // Obtenemos ruta en formato uri
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        String to[] = {
                destinatario
        };
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
        // adjuntamos el archivo
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri_fichero);
        // el asunto y el cuerpo
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, asunto);
        emailIntent.putExtra(Intent.EXTRA_TEXT, cuerpo);

        // Buscamos Aplicaciones que puedan enviar emails
        contexto.startActivity(Intent.createChooser(emailIntent, "Enviar email..."));
    }

    public static Calendar aCalendar(Date fecha_entrada) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(fecha_entrada);
        return calendar;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static ZonedDateTime aZonedDateTime(Calendar fecha_entrada) {
        return ZonedDateTime.ofInstant(fecha_entrada.toInstant(), ZoneId.systemDefault());
    }

    //______________________________________________________________________________
    /**
     * Validation Password     */
    //______________________________________________________________________________
    private static boolean validation_Password(final String PASSWORD_Arg) {
        boolean result = false;
        try {
            if (PASSWORD_Arg != null) {
                //_________________________
                //Parameteres
                final String MIN_LENGHT = "8";
                final String MAX_LENGHT = String.valueOf(new BigInteger(
                        String.valueOf(Integer.MAX_VALUE)));
                final boolean SPECIAL_CHAR_NEEDED = true;

                //_________________________
                //Modules
                final String ONE_DIGIT = "(?=.*[0-9])"; //(?=.*[0-9]) a digit must occur at least once
                final String LOWER_CASE = "(?=.*[a-z])"; //(?=.*[a-z]) a lower case letter must occur at least once
                final String UPPER_CASE = "(?=.*[A-Z])"; //(?=.*[A-Z]) an upper case letter must occur at least once
                final String NO_SPACE = "(?=\\S+$)"; //(?=\\S+$) no whitespace allowed in the entire string
                //final String MIN_CHAR = ".{" + MIN_LENGHT + ",}";  //.{8,} at least 8 characters
                final String MIN_MAX_CHAR = ".{" + MIN_LENGHT + "," + MAX_LENGHT + "}"; //.{5,10} represents minimum of 5 characters and maximum of 10 characters

                final String SPECIAL_CHAR;
                if (SPECIAL_CHAR_NEEDED == true) SPECIAL_CHAR = "(?=.*[@#$%^&+=])"; //(?=.*[@#$%^&+=]) a special character must occur at least once
                else SPECIAL_CHAR = "";
                //_________________________
                //Pattern
                //String pattern = "(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}";
                final String PATTERN = ONE_DIGIT + LOWER_CASE + UPPER_CASE + SPECIAL_CHAR + NO_SPACE + MIN_MAX_CHAR;
                //_________________________
                result = PASSWORD_Arg.matches(PATTERN);
                //_________________________
            }

        } catch (Exception ex) {
            result = false;
        }

        return result;
    }



    /* Prerequisito en Activity:
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            // requestCode = Código de Petición, permissions=Permisos solicitados, grantResults=array paralelo de concesiones o rechazos de permisos.
            if (requestCode == CODIGO_PETICION) {
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido.
                     llamadaAMetodo();
                }
                else { // El usuario ha rechazado el permiso.
                    Toast.makeText(getApplicationContext(),"Permiso para XXXX  rechazado",Toast.LENGTH_LONG).show();
                }
            }
        }

     */
    // Manifest.permission.
    public boolean PermisoConcedido(final Activity a, final String permiso, final String textoAclaratorio, final int codigoSolicitudPermiso) {
        // Build.VERSION.SDK_INT  => Versión Android del dispositivo en el que se está ejecutando la app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // Build.VERSION_CODES.M => Versión 6 (M - Marshmallow) de Android. https://developer.android.com/reference/android/os/Build.VERSION_CODES.html
            return true;
        }
        if (ActivityCompat.checkSelfPermission(a, permiso) == PackageManager.PERMISSION_GRANTED) { // Permiso ya previamente concedido.
            return true;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(a, permiso)) { // Es necesario dar una explicación (textoAclaratorio) acerca de por qué nuestra aplicacion necesita el permiso.
            AlertDialog.Builder datosDialog = new AlertDialog.Builder(a);
            datosDialog.setTitle("Solicitud de permiso");
            datosDialog.setMessage(textoAclaratorio);
            datosDialog.setPositiveButton("Entendido", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String[] arrayPermisos = {
                            permiso
                    }; // Se usa un array porque es posible solicitar varios permisos al mismo tiempo
                    ActivityCompat.requestPermissions(a, arrayPermisos, codigoSolicitudPermiso); // muestra una ventana al usuario
                }
            });
            datosDialog.show();

        } else {
            // new String[]{permiso} ==> Atajo para crear e inicilizar el array en una sola instrucción
            ActivityCompat.requestPermissions(a, new String[] {
                    permiso
            }, codigoSolicitudPermiso); // muestra una ventana al usuario
        }
        return false;
    }

    /* Upload fichero */

    public static Bitmap getBitMapReducido(String ruta_fichero,
                                           int dimension_requerida) {
        InputStream entrada_fichero = null;
        try {
            entrada_fichero = new FileInputStream(new File(ruta_fichero));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        BitmapFactory.Options opicionesBitmap = new BitmapFactory.Options();
        opicionesBitmap.inJustDecodeBounds = true;
        /* Con esta opción a true, no se carga la imagen en memoria y se obtienen
        sus dimensiones, que se guardarán en el propio objeto de opciones,
        en los atributos outWidth y outHeight
        */

        // Obtenemos el tamaño original de la imagen, sin cargarla en memoria
        BitmapFactory.decodeStream(entrada_fichero, null, opicionesBitmap);

        /* Encontramos la escala correcta según dimensión reducida requerida.
        Por razones de eficiencia debe ser una potencia de 2.
        La escala indica 1/2 del tamaño original, 1/4, 1/8...
        */

        int ancho_tmp = opicionesBitmap.outWidth, alto_tmp = opicionesBitmap.outHeight;
        int escala = 1;
        while ((ancho_tmp >= dimension_requerida) && (alto_tmp >= dimension_requerida)) {
            escala = escala * 2;
            ancho_tmp = ancho_tmp / 2;
            alto_tmp = alto_tmp / 2;
        }

        opicionesBitmap.inJustDecodeBounds = false; // Cargamos la imagen en memoria
        opicionesBitmap.inSampleSize = escala; // Reducimos su tamaño antes de cargarla
        try { // Es necesario volver a abrir el fichero
            entrada_fichero = new FileInputStream(new File(ruta_fichero));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return BitmapFactory.decodeStream(entrada_fichero, null, opicionesBitmap);
    }

    /* Precondición: Permiso de escritura en almacenamiento externo concedido */
    public static String crearFicheroExternoTemporal(String extension, String carpeta, boolean publico, Context contexto) throws IOException {
        // Creamos un nombre de fichero único.
        String instanteActual = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String nombreFichero = instanteActual + "_";
        File fcarpeta = null;
        if (publico)
            fcarpeta = Environment.getExternalStoragePublicDirectory(carpeta);
        else
            fcarpeta = contexto.getExternalFilesDir(carpeta);

        File fichero = File.createTempFile(nombreFichero, extension, fcarpeta);

        return fichero.getAbsolutePath();
    }

    /*
     * El método toma Bitmap como argumento
     * luego devolverá el array de bytes [] para el mapa de bits dado
     * aquí estamos usando Compresión PNG con calidad% de calidad
     * puedes dar calidad entre 0 y 100
     * 0 significa peor calidad
     * 100 significa la mejor calidad
     * */
    public static byte[] getBytesPNGDeBitmap(Bitmap bitmap, int calidad /* 80 es una buena medida */ ) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, calidad, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static void anhadirAGaleria(String rutaFicheo, Context contexto) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(rutaFicheo);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        contexto.sendBroadcast(mediaScanIntent);
    }





    public static String getRutaRealDeImagenURI(Uri contentUri, ContentResolver contentResolver) {

        String[] proj = {
                MediaStore.Images.Media.DATA
        };
        Cursor cursor = contentResolver.query(contentUri,
                proj, // columnas a entregar
                null, // WHERE filas
                null, // WHERE argumentos
                null); // order by
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();

        return cursor.getString(column_index);
    }

    public static Date aDate(Calendar calendar) {
        return calendar.getTime();
    }

    // Manifest.permission.
    public static boolean permisoConcedido(final Activity a, final String permiso, final String textoAclaratorio, final int codigoSolicitudPermiso) {
        // Build.VERSION.SDK_INT  => Versión Android del dispositivo en el que se está ejecutando la app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // Build.VERSION_CODES.M => Versión 6 (M - Marshmallow) de Android. https://developer.android.com/reference/android/os/Build.VERSION_CODES.html
            return true;
        }
        if (ActivityCompat.checkSelfPermission(a, permiso) == PackageManager.PERMISSION_GRANTED) { // Permiso ya previamente concedido.
            return true;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(a, permiso)) { // Es necesario dar una explicación (textoAclaratorio) acerca de por qué nuestra aplicacion necesita el permiso.
            AlertDialog.Builder datosDialog = new AlertDialog.Builder(a);
            datosDialog.setTitle("Solicitud de permiso");
            datosDialog.setMessage(textoAclaratorio);
            datosDialog.setPositiveButton("Entendido", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String[] arrayPermisos = {
                            permiso
                    }; // Se usa un array porque es posible solicitar varios permisos al mismo tiempo
                    ActivityCompat.requestPermissions(a, arrayPermisos, codigoSolicitudPermiso); // muestra una ventana al usuario
                }
            });
            datosDialog.show();

        } else {
            // new String[]{permiso} ==> Atajo para crear e inicilizar el array en una sola instrucción
            ActivityCompat.requestPermissions(a, new String[] {
                    permiso
            }, codigoSolicitudPermiso); // muestra una ventana al usuario
        }
        return false;
    }

    public static String superindice(String str) {
        str = str.replaceAll("0", "⁰");
        str = str.replaceAll("1", "¹");
        str = str.replaceAll("2", "²");
        str = str.replaceAll("3", "³");
        str = str.replaceAll("4", "⁴");
        str = str.replaceAll("5", "⁵");
        str = str.replaceAll("6", "⁶");
        str = str.replaceAll("7", "⁷");
        str = str.replaceAll("8", "⁸");
        str = str.replaceAll("9", "⁹");
        return str;
    }

    public static String subindice(String str) {
        str = str.replaceAll("0", "₀");
        str = str.replaceAll("1", "₁");
        str = str.replaceAll("2", "₂");
        str = str.replaceAll("3", "₃");
        str = str.replaceAll("4", "₄");
        str = str.replaceAll("5", "₅");
        str = str.replaceAll("6", "₆");
        str = str.replaceAll("7", "₇");
        str = str.replaceAll("8", "₈");
        str = str.replaceAll("9", "₉");
        return str;
    }

    public static ConnectivityAndInternetAccess.Request comprobarAccesoAInternet(
            Context contexto,
            InternetAccessCallback callback
    ) {
        return ConnectivityAndInternetAccess.checkInternetAsync(
                contexto,
                result -> callback.onResult(
                        result.isReachable(),
                        result.getReachedHost()
                )
        );
    }

}
