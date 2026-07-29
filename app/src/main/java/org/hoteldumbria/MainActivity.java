package org.hoteldumbria;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private boolean internetVerified;
    private static final int SOLICITUD_CONFIRMACION_RESERVA = 2;
    private static final int SOLICITUD_CIERRE_SESION = 3;

    private static final int SOLICITUD_ACTUALIZACION = 1;
    private static final int SOLICITUD_ACTUALIZACION_IMAGEN_PERFIL = 1000;

    private AppBarConfiguration mAppBarConfiguration;
    private static Usuario usuarioRecibido;
    private PeticionesRed peticionesRed;
    private NavigationView navigationView;
    private AlertDialog.Builder datosDialog;

    public static int getSolicitudConfirmacionReserva() {
        return SOLICITUD_CONFIRMACION_RESERVA;
    }

    public static int getSolicitudCierreSesion() {
        return SOLICITUD_CIERRE_SESION;
    }

    public static int getSolicitudActualizacion() {
        return SOLICITUD_ACTUALIZACION;
    }

    public static int getSolicitudActualizacionImagenPerfil() {
        return SOLICITUD_ACTUALIZACION_IMAGEN_PERFIL;
    }

    public static Usuario getUsuarioRecibido() {
        return usuarioRecibido;
    }

    public void setUsuarioRecibido(Usuario usuarioRecibido) {
        this.usuarioRecibido = usuarioRecibido;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intentRecogido = getIntent();
        setUsuarioRecibido((Usuario) intentRecogido.getSerializableExtra("usuario"));
        if (getUsuarioRecibido() == null) {
            startActivity(new Intent(this, ActivityLoginRegistro.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        setPeticionesRed(PeticionesRed.getInstancia(getApplicationContext()));
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
       /* FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        */

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        setNavigationView(findViewById(R.id.nav_view));
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        setmAppBarConfiguration(new AppBarConfiguration.Builder(R.id.nav_activityDatos, R.id.nav_activityReservas,
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow,
                R.id.nav_tools, R.id.nav_share, R.id.nav_send, R.id.nav_contacto)
                .setDrawerLayout(drawer)
                .build());
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, getmAppBarConfiguration());
        NavigationUI.setupWithNavController(getNavigationView(), navController);

        //navigationView.getHeaderView(0).setVisibility(View.GONE);

        getNavigationView().setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                Bundle argumentos = null;

                NavigationView navigationView = findViewById(R.id.nav_view);
                long idMenuSeleccionado = -1;
                for(int i = 0; i < navigationView.getMenu().size(); i++){
                    if(navigationView.getMenu().getItem(i).isChecked()){
                        idMenuSeleccionado = navigationView.getMenu().getItem(i).getItemId();
                    }
                }
                if(idMenuSeleccionado == R.id.nav_home && menuItem.getItemId() == R.id.nav_send){
                    argumentos = new Bundle();
                    //argumentos.putString();
                }

                NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment);
                NavOptions.Builder opcionesBuilder = new NavOptions.Builder();
                opcionesBuilder.setPopUpTo(menuItem.getItemId(), true);
                navController.navigate(menuItem.getItemId(),argumentos,opcionesBuilder.build());

                ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawers();

                return true;
            }
        });

        ((TextView)(getNavigationView().getHeaderView(0).findViewById(R.id.tv_usuario))).setText(getUsuarioRecibido().getNombre());
        ((TextView)(getNavigationView().getHeaderView(0).findViewById(R.id.tv_email_usuario))).setText(getUsuarioRecibido().getEmail());
        cargarAvatarAutenticado();
        ((NetworkImageView) (getNavigationView().getHeaderView(0).findViewById(R.id.imv_imagen_usuario))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentUpload = new Intent(MainActivity.this, UploadImagenActivity.class);
                startActivityForResult(intentUpload, getSolicitudActualizacionImagenPerfil());
            }
        });
        Utilidades.comprobarAccesoAInternet(this, (reachable, reachedHost) ->
                Log.d("Internet", reachable
                        ? "Acceso verificado mediante " + reachedHost
                        : "Ningún servidor de comprobación alcanzable"));
    }

    private PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, getmAppBarConfiguration())
                || super.onSupportNavigateUp();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == getSolicitudActualizacion()) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Datos actualizados exitosamente", Toast.LENGTH_SHORT).show();
                setUsuarioRecibido((Usuario) data.getSerializableExtra("usuario"));

                String mensajeBienvenida = null;
                switch (getUsuarioRecibido().getSexo()) {
                    case "hombre":
                        mensajeBienvenida = "bienvenido";
                        break;

                    case "mujer":
                        mensajeBienvenida = "bienvenida";
                        break;
                }
              //  tv_titulo.setText(usuarioRecibido.getNombre() + "(" + usuarioRecibido.getEmail() + ") " + mensajeBienvenida + ". Sus pedidos");
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Actualización cancelada", Toast.LENGTH_SHORT).show();
                Log.i("Actualización", "Actualización cancelada");
            }


        } else if (requestCode == getSolicitudConfirmacionReserva()) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Reservas realizadas correctamente", Toast.LENGTH_SHORT).show();
                setUsuarioRecibido((Usuario) data.getSerializableExtra("usuario"));

                String mensajeBienvenida = null;
                switch (getUsuarioRecibido().getSexo()) {
                    case "hombre":
                        mensajeBienvenida = "bienvenido";
                        break;

                    case "mujer":
                        mensajeBienvenida = "bienvenida";
                        break;
                }
               // tv_titulo.setText(usuarioRecibido.getNombre() + "(" + usuarioRecibido.getEmail() + ") " + mensajeBienvenida + ". Sus pedidos");
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Confirmación de reserva de habitaciones cancelada", Toast.LENGTH_SHORT).show();
                Log.i("ConfirmacionReservas", "Confirmación de reservas cancelada");
            }
        } else if (requestCode == getSolicitudCierreSesion()) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Cierre de sesión cancelado", Toast.LENGTH_SHORT).show();
            }
        }

        else if (requestCode == getSolicitudActualizacionImagenPerfil()){
            if(resultCode == RESULT_OK){
                cargarAvatarAutenticado();
            }
        }



    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
           /* case R.id.actualizar_perfil:
                Intent intentActivityDatos = new Intent(this, ActivityDatos.class);
                intentActivityDatos.putExtra("usuario", usuarioRecibido);
                intentActivityDatos.setAction("actualizacion_perfil");
                startActivityForResult(intentActivityDatos, SOLICITUD_ACTUALIZACION);
                break;
            */
            case R.id.cerrar_sesion:
                Intent intentCierresesion = new Intent(this, ActivityLoginRegistro.class);
                //intentActivityDatos.putExtra("usuario", usuarioRecibido);
                intentCierresesion.setAction("cierre_sesion");
                startActivity(intentCierresesion);
                finish();
                break;

            case R.id.borrar_perfil:
                if (!internetVerified) {
                    Utilidades.comprobarAccesoAInternet(this, (reachable, reachedHost) -> {
                        if (reachable) {
                            internetVerified = true;
                            onOptionsItemSelected(item);
                        } else {
                            Toast.makeText(this, "Sin acceso real a Internet", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                }
                internetVerified = false;
                setDatosDialog(new AlertDialog.Builder(MainActivity.this));
               // getDatosDialog().setIcon(R.drawable.ic_launcher_foreground);
                getDatosDialog().setTitle("¿Realmente desea borrar su perfil?");

                //final ArrayAdapterHabitaciones arrayAdapterHabitaciones = new ArrayAdapterHabitaciones(MainActivity.this, R.layout.layout_habitaciones, getHabitacionesAReservar(), getPeticionesRed());

                /*getDatosDialog().setAdapter(arrayAdapterHabitaciones, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });*/

               // arrayAdapterHabitaciones.setOcultarCheckboxes(true);

                getDatosDialog().setPositiveButton("Sí", (dialog, which) -> {
                    final EditText password = new EditText(MainActivity.this);
                    password.setHint("Contraseña actual");
                    password.setInputType(InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Confirma tu identidad")
                            .setMessage("Esta acción elimina el perfil, la cuenta, reservas y avatar.")
                            .setView(password)
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Eliminar definitivamente", (confirmation, selected) -> {
                                JSONObject body = new JSONObject();
                                try {
                                    body.put("password", password.getText().toString());
                                } catch (Exception ignored) {
                                    return;
                                }
                                AuthenticatedJsonRequest request = new AuthenticatedJsonRequest(
                                        Request.Method.DELETE,
                                        WebService.getMeUrl(),
                                        body,
                                        response -> cerrarTrasBorrado(),
                                        error -> {
                                            Log.e("BorradoPerfil", "Borrado rechazado", error);
                                            Toast.makeText(this,
                                                    "No se pudo eliminar. Comprueba la contraseña.",
                                                    Toast.LENGTH_LONG).show();
                                        });
                                getPeticionesRed().anhadirPeticionACola(request);
                            }).show();
                });
                getDatosDialog().setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplicationContext(), "Borrado cancelado", Toast.LENGTH_SHORT).show();
                    }
                });

                getDatosDialog().create().show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public AppBarConfiguration getmAppBarConfiguration() {
        return mAppBarConfiguration;
    }

    private void cerrarTrasBorrado() {
        SessionManager.clear(this);
        Intent login = new Intent(this, ActivityLoginRegistro.class);
        login.setAction("cierre_sesion");
        startActivity(login);
        finish();
    }

    private void cargarAvatarAutenticado() {
        NetworkImageView avatar = getNavigationView().getHeaderView(0)
                .findViewById(R.id.imv_imagen_usuario);
        String accessToken = SessionManager.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            return;
        }
        ImageRequest request = new ImageRequest(
                WebService.getAvatarUrl() + "?v=" + System.currentTimeMillis(),
                avatar::setImageBitmap,
                512,
                512,
                android.widget.ImageView.ScaleType.CENTER_CROP,
                android.graphics.Bitmap.Config.RGB_565,
                error -> Log.w("Avatar", "No hay avatar o no se pudo descargar", error)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        request.setShouldCache(false);
        getPeticionesRed().anhadirPeticionACola(request);
    }

    public void setmAppBarConfiguration(AppBarConfiguration mAppBarConfiguration) {
        this.mAppBarConfiguration = mAppBarConfiguration;
    }

    public void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    public NavigationView getNavigationView() {
        return navigationView;
    }

    public void setNavigationView(NavigationView navigationView) {
        this.navigationView = navigationView;
    }

    public AlertDialog.Builder getDatosDialog() {
        return datosDialog;
    }

    public void setDatosDialog(AlertDialog.Builder datosDialog) {
        this.datosDialog = datosDialog;
    }

  /*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {


        return true;
    }
    */
}

