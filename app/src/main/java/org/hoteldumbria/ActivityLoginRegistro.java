package org.hoteldumbria;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.HashMap;

public class ActivityLoginRegistro extends AppCompatActivity {

    private ConnectivityAndInternetAccess.Request internetAccessRequest;
    private Button btn_entrar;
    private EditText edt_email;
    private EditText edt_clave;
    private TextView tv_registro;
    private CheckBox chk_guardar_login;
    private ProgressBar pg;
    private static final String COLA_PETICIONES_LOGIN = "PeticionesLogin";
    private PeticionesRed peticionesRed;
    private SharedPreferences preferencias;
    private AlertDialog.Builder datosDialog;

    private static final int SOLICITUD_REGISTRO = 2;

    public static String getColaPeticionesLogin() {
        return COLA_PETICIONES_LOGIN;
    }

    public static int getSolicitudRegistro() {
        return SOLICITUD_REGISTRO;
    }

    public void login(){

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        setBtn_entrar(findViewById(R.id.bt_login_entrar));
        setEdt_email(findViewById(R.id.et_login_email));
        setEdt_clave(findViewById(R.id.edt_login_clave));
        setChk_guardar_login(findViewById(R.id.chk_guardar_login));
        setTv_registro(findViewById(R.id.tv_login_registrarse));
        setPg(findViewById(R.id.pgb_confirmacion_reservas));
        getPg().setVisibility(View.GONE);

        setPeticionesRed(PeticionesRed.getInstancia(this));

        setPreferencias(ActivityLoginRegistro.this.getPreferences(MODE_PRIVATE));
        Intent intent = getIntent();
        if (intent != null) {
            String accion = intent.getAction();
            if ("cierre_sesion".equals(accion)) {
                if (getPreferencias() != null) {
                    getPreferencias().edit().clear().apply();
                    SessionManager.clear(this);
                    //getEdt_email().requestFocus();
                }
            }
        }

        setPreferencias(ActivityLoginRegistro.this.getPreferences(MODE_PRIVATE));
        String email_recuperado = getPreferencias().getString("email", "");
        getEdt_email().setText(email_recuperado);

        String refreshToken = SessionManager.loadRefreshToken(this);
        if (email_recuperado.length() > 0 && refreshToken != null) {
            getChk_guardar_login().setChecked(true);
            getPg().setVisibility(View.VISIBLE);
            refrescarSesion();
        }


        getBtn_entrar().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Utilidades.ocultarTeclado(ActivityLoginRegistro.this);
                if (!getChk_guardar_login().isChecked()) {
                    setPreferencias(ActivityLoginRegistro.this.getPreferences(MODE_PRIVATE));
                    Intent intent = getIntent();
                    if (intent != null) {
                        String accion = intent.getAction();
                        if ("cierre_sesion".equals(accion)) {
                            if (getPreferencias() != null) {
                                getPreferencias().edit().clear().commit();
                                //getEdt_email().requestFocus();
                            }
                        } else {
                            Toast.makeText(ActivityLoginRegistro.this, "No había guardado la sesión", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                String email = getEdt_email().getText().toString();
                final String clave = getEdt_clave().getText().toString();
                if (email.length() == 0 || clave.length() == 0) {
                    Toast.makeText(getApplicationContext(), "Debe introducir email y contraseña", Toast.LENGTH_LONG).show();
                } else {
                    if (!Utilities.isValidEmail(email)) {
                        Toast.makeText(getApplicationContext(), "Email no válido", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getPg().setVisibility(View.VISIBLE);
                    comprobarInternetYEntrar(
                            email,
                            clave,
                            getChk_guardar_login().isChecked(),
                            false
                    );
                }
            }
        });

        getTv_registro().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentTerceraActivity = new Intent(ActivityLoginRegistro.this, ActivityDatosFragment.class);
                intentTerceraActivity.setAction("registro");
                startActivityForResult(intentTerceraActivity, getSolicitudRegistro());
            }
        });

        /*edt_clave.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(!Utilidades.esClaveFuerte(edt_clave.getText().toString())){
                    Toast.makeText(ActivityLoginRegistro.this, "La contraseña no es fuerte", Toast.LENGTH_LONG).show();
                }
            }
        });*/
    }

    private void comprobarInternetYEntrar(
            String email,
            String clave,
            boolean recordar,
            boolean limpiarPreferenciasSiFalla
    ) {
        internetAccessRequest = Utilidades.comprobarAccesoAInternet(this, (reachable, reachedHost) -> {
            if (!reachable) {
                getPg().setVisibility(View.INVISIBLE);
                Log.w("ActivityLoginRegistro", "Ningún servidor de comprobación es alcanzable");
                Toast.makeText(this, "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                return;
            }
            enviarLogin(email, clave, recordar, limpiarPreferenciasSiFalla);
        });
    }

    private void enviarLogin(
            String email,
            String clave,
            boolean recordar,
            boolean limpiarPreferenciasSiFalla
    ) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email);
            payload.put("password", clave);
        } catch (Exception error) {
            getPg().setVisibility(View.INVISIBLE);
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, WebService.getLoginUrl(), payload,
                response -> {
                    try {
                        String accessToken = response.getString("accessToken");
                        String refreshToken = response.getString("refreshToken");
                        SessionManager.startSession(
                                this,
                                accessToken,
                                refreshToken,
                                recordar
                        );
                        if (recordar) {
                            getPreferencias().edit().putString("email", email).apply();
                        } else {
                            getPreferencias().edit().clear().apply();
                        }
                        cargarPerfilYAbrir();
                    } catch (Exception error) {
                        getPg().setVisibility(View.INVISIBLE);
                        Log.e("ActivityLoginRegistro", "Respuesta de login inválida", error);
                        Toast.makeText(this, "Respuesta del servidor no válida", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    getPg().setVisibility(View.INVISIBLE);
                    if (limpiarPreferenciasSiFalla) {
                        getPreferencias().edit().clear().apply();
                        SessionManager.clear(this);
                    }
                    Log.e("ActivityLoginRegistro", "Error en la petición JSON", error);
                    Toast.makeText(this, "Email/contraseña incorrectos o correo sin confirmar", Toast.LENGTH_LONG).show();
                });
        request.setTag(getColaPeticionesLogin());
        getPeticionesRed().anhadirPeticionACola(request);
    }

    private void refrescarSesion() {
        internetAccessRequest = Utilidades.comprobarAccesoAInternet(this, (reachable, host) -> {
            if (!reachable) {
                getPg().setVisibility(View.INVISIBLE);
                Toast.makeText(this, "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                return;
            }
            SessionManager.refreshAccessToken(this, refreshed -> {
                if (!getLifecycle().getCurrentState().isAtLeast(
                        androidx.lifecycle.Lifecycle.State.STARTED)) return;
                if (refreshed) {
                    cargarPerfilYAbrir();
                } else {
                    getPg().setVisibility(View.INVISIBLE);
                    getPreferencias().edit().remove("email").apply();
                }
            });
        });
    }

    private void cargarPerfilYAbrir() {
        AuthenticatedJsonRequest request = new AuthenticatedJsonRequest(
                this, Request.Method.GET, WebService.getMeUrl(), null,
                response -> {
                    getPg().setVisibility(View.INVISIBLE);
                    Usuario usuario = ApiMapper.userFromProfile(response);
                    Intent main = new Intent(this, MainActivity.class);
                    main.putExtra("usuario", usuario);
                    startActivity(main);
                    finish();
                },
                error -> {
                    getPg().setVisibility(View.INVISIBLE);
                    Toast.makeText(this, "No se pudo cargar el perfil", Toast.LENGTH_LONG).show();
                });
        request.setTag(getColaPeticionesLogin());
        getPeticionesRed().anhadirPeticionACola(request);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == getSolicitudRegistro()) {
            if (resultCode == RESULT_OK) {
                Log.i("ActivityLoginRegistro", "Registro completado con éxito");
                Toast.makeText(getApplicationContext(), "Registro completado con éxito", Toast.LENGTH_SHORT).show();
                Intent intentMainActivity = new Intent(ActivityLoginRegistro.this, MainActivity.class);
                Usuario usuario = (Usuario) data.getSerializableExtra("usuario");
                intentMainActivity.putExtra("usuario", usuario);
                startActivity(intentMainActivity);
                finish();
            } else if (resultCode == RESULT_CANCELED) {
                //Toast.makeText(this, "Registro cancelado", Toast.LENGTH_SHORT).show();
                Log.i("Registro", "Registro cancelado");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
        if (getPeticionesRed() != null) { // Finalizamos peticiones pendientes
            getPeticionesRed().getColaPeticiones().cancelAll(getColaPeticionesLogin());
        }
    }

    public Button getBtn_entrar() {
        return btn_entrar;
    }

    public void setBtn_entrar(Button btn_entrar) {
        this.btn_entrar = btn_entrar;
    }

    public EditText getEdt_email() {
        return edt_email;
    }

    public void setEdt_email(EditText edt_email) {
        this.edt_email = edt_email;
    }

    public EditText getEdt_clave() {
        return edt_clave;
    }

    public void setEdt_clave(EditText edt_clave) {
        this.edt_clave = edt_clave;
    }

    public TextView getTv_registro() {
        return tv_registro;
    }

    public void setTv_registro(TextView tv_registro) {
        this.tv_registro = tv_registro;
    }

    public CheckBox getChk_guardar_login() {
        return chk_guardar_login;
    }

    public void setChk_guardar_login(CheckBox chk_guardar_login) {
        this.chk_guardar_login = chk_guardar_login;
    }

    public ProgressBar getPg() {
        return pg;
    }

    public void setPg(ProgressBar pg) {
        this.pg = pg;
    }

    public PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    public void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    public SharedPreferences getPreferencias() {
        return preferencias;
    }

    public void setPreferencias(SharedPreferences preferencias) {
        this.preferencias = preferencias;
    }

    public AlertDialog.Builder getDatosDialog() {
        return datosDialog;
    }

    public void setDatosDialog(AlertDialog.Builder datosDialog) {
        this.datosDialog = datosDialog;
    }
}
