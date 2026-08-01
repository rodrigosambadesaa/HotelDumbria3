package org.hoteldumbria;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.hoteldumbria.ui.PoblacionesProvincias;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class ActivityDatos extends Fragment {
    private boolean internetVerified;
    private ConnectivityAndInternetAccess.Request internetAccessRequest;



    private EditText edt_nif;
    private EditText edt_email;
    private EditText edt_clave;
    private EditText edt_nombre;
    private EditText edt_apellido_1;
    private EditText edt_apellido_2;
    private EditText edt_calle;
    private EditText edt_numero;
    private EditText edt_piso;
    private EditText edt_puerta;
    private EditText edt_escalera;
    private EditText edt_bloque;
    private EditText edt_codigo_postal;
    private TextView tv_poblacion;
    private TextView tv_provincia;
    private RadioGroup rdg_sexo;
    private Button btn_aceptar;
    private Button btn_cancelar;
    private TextView tv_cabecera;
    private Persona persona;
    private Usuario usuario;
    private Intent intentRecibido;
    private String accion;
    private ProgressBar pg;
    private static final String COLA_PETICIONES_LOGIN = "PeticionesPrueba";
    private static final String COLA_PETICIONES_POBLACIONES_PROVINCIAS = "PeticionesPoblacionesProvincias";
    private PeticionesRed peticionesRed;
    private String nif;
    private String email;
    private String nombre;
    private String apellido1;
    private String apellido2;
    private String calle;
    private int numero;
    private int piso;
    private String puerta;
    private String escalera;
    private int bloque;
    private String imagen;
    private String codigoPostal = "";
    private String tipoPersona = Constantes.getTipoPersonaUsuario();
    private String clave;
    private static final String COLA_PETICIONES_PRUEBA = "PeticionesPrueba";
    private JsonObjectRequest jsObjRequest;
    private String urlPeticion = WebService.getURL_Usuarios();
    private List < PoblacionesProvincias > poblacionesProvinciasDescargadas;
    private ArrayAdapterPoblacionesProvincias arrayAdapterPoblacionesProvincias;
    private ListView lv_poblaciones_provincias;
    private AlertDialog.Builder datosDialog;
    private String sexo;
    private Usuario usuarioAInsertar;
    private double latitude;
    private double longitude;


    public ActivityDatos() {
        super();
    }

    private static String getColaPeticionesLogin() {
        return COLA_PETICIONES_LOGIN;
    }

    public static String getColaPeticionesPoblacionesProvincias() {
        return COLA_PETICIONES_POBLACIONES_PROVINCIAS;
    }

    private static String getColaPeticionesPrueba() {
        return COLA_PETICIONES_PRUEBA;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    private EditText getEdt_email() {
        return edt_email;
    }

    private void setEdt_email(EditText edt_email) {
        this.edt_email = edt_email;
    }

    private EditText getEdt_nombre() {
        return edt_nombre;
    }

    private void setEdt_nombre(EditText edt_nombre) {
        this.edt_nombre = edt_nombre;
    }

    private RadioGroup getRdg_sexo() {
        return rdg_sexo;
    }

    private void setRdg_sexo(RadioGroup rdg_sexo) {
        this.rdg_sexo = rdg_sexo;
    }

    private Button getBtn_aceptar() {
        return btn_aceptar;
    }

    private void setBtn_aceptar(Button btn_aceptar) {
        this.btn_aceptar = btn_aceptar;
    }

    private Button getBtn_cancelar() {
        return btn_cancelar;
    }

    private void setBtn_cancelar(Button btn_cancelar) {
        this.btn_cancelar = btn_cancelar;
    }

    private TextView getTv_cabecera() {
        return tv_cabecera;
    }

    private void setTv_cabecera(TextView tv_cabecera) {
        this.tv_cabecera = tv_cabecera;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.activity_datos, container, false);


        setEdt_nif(layout.findViewById(R.id.edt_nif));
        setEdt_email(layout.findViewById(R.id.edt_email));
        setEdt_clave(layout.findViewById(R.id.edt_clave));
        setEdt_nombre(layout.findViewById(R.id.edt_nombre));
        setEdt_apellido_1(layout.findViewById(R.id.edt_apellido_1));
        setEdt_apellido_2(layout.findViewById(R.id.edt_apellido_2));
        setEdt_calle(layout.findViewById(R.id.edt_calle));
        setEdt_numero(layout.findViewById(R.id.edt_numero));
        setEdt_piso(layout.findViewById(R.id.edt_piso));
        setEdt_puerta(layout.findViewById(R.id.edt_puerta));
        setEdt_escalera(layout.findViewById(R.id.edt_escalera));
        setEdt_bloque(layout.findViewById(R.id.edt_bloque));
        setEdt_codigo_postal(layout.findViewById(R.id.edt_codigo_postal));
        setTv_poblacion(layout.findViewById(R.id.tv_poblacion));
        setTv_provincia(layout.findViewById(R.id.tv_provincia));
        setLv_poblaciones_provincias(layout.findViewById(R.id.lv_poblaciones_provincias));
        getLv_poblaciones_provincias().setVisibility(View.GONE);

        getTv_poblacion().setEnabled(false);
        getTv_provincia().setEnabled(false);

        //getTv_poblacion().setBackgroundResource();

        getEdt_codigo_postal().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                HashMap < String, String > parametros = new HashMap<>();
                parametros.put("codigo_postal", getEdt_codigo_postal().getText().toString());

                String urlPeticion = WebService.getURL_poblaciones_provincias() + Utilidades.generaParametrosURL(parametros);

                JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.GET, urlPeticion, null,
                        response -> {

                            getPg().setVisibility(View.INVISIBLE);

                            // Convertimos JSON a Objetos JAVA
                            try {
                                Gson gson = new Gson();
                                if (response.length() == 0) {
                                    Log.e("Login", "Sin datos de respuesta del servidor");
                                } else {
                                    String estado = response.getString(WebService.JSON.STATUS);
                                    switch (estado) {
                                        case WebService.JSON.SUCCESS:
              /*Usuario usuarioRecibido = gson.fromJson(response.getString(WebService.JSON.DATA), Usuario.class);
              if (usuarioRecibido != null) {

                  Toast.makeText(getApplicationContext(), "Login Correcto", Toast.LENGTH_LONG).show();
                  Intent intentActivityReservas = new Intent(ActivityLoginRegistro.this, ActivityReservas.class);
                  intentActivityReservas.putExtra("usuario", usuarioRecibido);
                  startActivity(intentActivityReservas);
                  finish();
              } else {
                  Toast.makeText(getApplicationContext(), "El Usuario No Existe ", Toast.LENGTH_LONG).show();
              }*/

                                            TypeToken < List < PoblacionesProvincias >> token = new TypeToken < List < PoblacionesProvincias >> () {};
                                            setPoblacionesProvinciasDescargadas(gson.fromJson(response.getString(WebService.JSON.DATA), token.getType()));
                                            if (getPoblacionesProvinciasDescargadas() != null) {
                                                getLv_poblaciones_provincias().setVisibility(View.VISIBLE);
                                                if (getPoblacionesProvinciasDescargadas().size() == 1) {
                                                    getTv_poblacion().setText(getPoblacionesProvinciasDescargadas().get(0).getPoblacion());
                                                    getTv_provincia().setText(getPoblacionesProvinciasDescargadas().get(0).getProvincia());
                                                } else {
                                                    setArrayAdapterPoblacionesProvincias(new ArrayAdapterPoblacionesProvincias(getActivity(), R.layout.layout_poblacionesprovincias, getPoblacionesProvinciasDescargadas(), getPeticionesRed()));
                                                    setLv_poblaciones_provincias(layout.findViewById(R.id.lv_poblaciones_provincias));
                                                    getLv_poblaciones_provincias().setAdapter(getArrayAdapterPoblacionesProvincias());
                                                    setDatosDialog(new AlertDialog.Builder(
                                                            Objects.requireNonNull(
                                                                    getActivity())));
                                                    getDatosDialog().setTitle("Seleccione población y provincia");
                                                    getDatosDialog().setAdapter(getArrayAdapterPoblacionesProvincias(),
                                                            (dialog, which) -> {
                                                                getTv_poblacion().setText(getPoblacionesProvinciasDescargadas().get(which).getPoblacion());
                                                                getTv_provincia().setText(getPoblacionesProvinciasDescargadas().get(which).getProvincia());
                                                            });
                                                    getDatosDialog().create().show();
                                                }
                                                Log.i("PoblacionesProvincias", "Descarga de poblaciones y provincias correcta");

                                            }

                                            break;
                                        case WebService.JSON.ERROR:
                                            Log.e("Login", "Error:" + response.getString(WebService.JSON.MESSAGE));
                                            break;
                                        case WebService.JSON.FAIL:
                                            Log.e("Login", "Fallo: " + response.getString(WebService.JSON.DATA));
                                            break;
                                    }
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e("Login", "Error al convertir JSON a Objetos JAVA");
                            }

                        }, error -> {

                            getPg().setVisibility(View.INVISIBLE);
                            Log.e("Login", "Error en la petición JSON: " + error.toString());

                        });

                jsObjRequest.setTag(getColaPeticionesLogin()); // nombramos la petición
                getPeticionesRed().anhadirPeticionACola(jsObjRequest); // Lanzamos la petición

            }
        });

        setRdg_sexo(layout.findViewById(R.id.rdg_sexo));

        setBtn_aceptar(layout.findViewById(R.id.bt_reservar_habitacion));
        setBtn_cancelar(layout.findViewById(R.id.bt_cancelar));
        setTv_cabecera(layout.findViewById(R.id.cabecera));

        setPg(layout.findViewById(R.id.pgb_confirmacion_reservas));

        getPg().setVisibility(View.INVISIBLE);

        setPeticionesRed(PeticionesRed.getInstancia(getActivity()));

        if (Objects.requireNonNull(getActivity()).findViewById(R.id.contenedor_fragment_activity_datos)!=null) {
            //Activity
            setIntentRecibido(getActivity().getIntent());
            setAccion(getIntentRecibido().getAction());

        }
        else{
            //Fragment
            setAccion("actualizacion_perfil");
            setUsuario(MainActivity.getUsuarioRecibido());
        }

        if ("actualizacion_perfil".equals(getAccion())) {
            getTv_cabecera().setText("Actualizar perfil");

            getEdt_nif().setText(getUsuario().getNif());
            getEdt_nif().setEnabled(false);
            getEdt_email().setText(getUsuario().getEmail());
            getEdt_nombre().setText(getUsuario().getNombre());
            getEdt_apellido_1().setText(getUsuario().getApellido_1());
            getEdt_apellido_2().setText(getUsuario().getApellido_2());
            getEdt_calle().setText(getUsuario().getCalle());
            getEdt_numero().setText(String.valueOf(getUsuario().getNumero()));
            getEdt_piso().setText(String.valueOf(getUsuario().getPiso()));
            getEdt_puerta().setText(getUsuario().getPuerta());
            getEdt_escalera().setText(getUsuario().getEscalera());
            getEdt_bloque().setText(String.valueOf(getUsuario().getBloque()));
            getEdt_codigo_postal().setText(getUsuario().getCodigo_postal());
            setSexo(getUsuario().getSexo());
            switch (getSexo()) {
                case "hombre":
                    getRdg_sexo().check(R.id.rb_hombre);
                    break;
                case "mujer":
                    getRdg_sexo().check(R.id.rb_mujer);
                    break;
            }
        }



        getBtn_aceptar().setOnClickListener(v -> {
            getPg().setVisibility(View.VISIBLE);
            switch (getAccion()) {
                case "registro":
                    setNif(getEdt_nif().getText().toString());
                    setEmail(getEdt_email().getText().toString());
                    setNombre(getEdt_nombre().getText().toString());
                    setApellido1(getEdt_apellido_1().getText().toString());
                    setApellido2(getEdt_apellido_2().getText().toString());
                    setCalle(getEdt_calle().getText().toString());
                    setPuerta(getEdt_puerta().getText().toString());
                    setEscalera(getEdt_escalera().getText().toString());
                    setCodigoPostal(getEdt_codigo_postal().getText().toString().trim());
                    // Argon2id se aplica exclusivamente en el servidor.
                    setClave(getEdt_clave().getText().toString());
                    setLatitude(0);
                    setLongitude(0);
                    if (Objects.requireNonNull(getActivity()).findViewById(R.id.contenedor_fragment_activity_datos)!=null) {
                        //Activity
                        setImagen("imagen");
                    }
                    else {
                        //Fragment
                        setImagen(MainActivity.getUsuarioRecibido().getImagen());
                    }

                    try {
                        setNumero(Integer.parseInt(getEdt_numero().getText().toString()));
                        setPiso(Integer.parseInt(getEdt_piso().getText().toString()));
                        setBloque(Integer.parseInt(getEdt_bloque().getText().toString()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }


                   int sexoEntero = getRdg_sexo().getCheckedRadioButtonId();

                    String sexoStr = null;

                    if (sexoEntero == R.id.rb_hombre) {
                        sexoStr = "hombre";
                    } else if (sexoEntero == R.id.rb_mujer) {
                        sexoStr = "mujer";
                    }

                    setSexo(sexoStr);

                    if (getNif().length() <= 0 || !Utilidades.validarNif(getNif()) || getEmail().length() <= 0 || !Utilities.isValidEmail(getEmail()) || getNombre().length() <= 0 || getApellido1().length() <= 0 || getApellido2().length() <= 0 || getCalle().length() <= 0 || getClave().length() <= 0 || getNumero() <= 0 || !isValidPostalCode(getCodigoPostal()) || sexoEntero == -1) {
                        Toast.makeText(getActivity(), "Revise los datos introducidos", Toast.LENGTH_LONG).show();
                        getPg().setVisibility(View.GONE);
                    } else {
                        setUsuario(new Usuario(getNif(), getEmail(), getNombre(),
                                getApellido1(), getApellido2(), getSexo(), getCalle()
                                , getNumero(), getPiso(), getPuerta(),
                                getEscalera(), getBloque(), getCodigoPostal(),
                                getTipoPersona(), getLatitude(), getLongitude(),
                                getClave(),
                                getImagen()));
                        getPg().setVisibility(View.VISIBLE);
                        if (!internetVerified) {
                            internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, reachedHost) -> {
                                if (reachable) {
                                    internetVerified = true;
                                    getBtn_aceptar().performClick();
                                } else {
                                    getPg().setVisibility(View.INVISIBLE);
                                    Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }
                        internetVerified = false;

                        if (!Utilities.isValidEmail(getEmail())) {
                            Toast.makeText(getActivity(), "Email no válido", Toast.LENGTH_LONG);
                            return;
                        }

                        JSONObject registration = new JSONObject();
                        try {
                            setUsuarioAInsertar(getUsuario());
                            registration.put("nationalId", getNif());
                            registration.put("email", getEmail());
                            registration.put("password", getClave());
                            registration.put("firstName", getNombre());
                            registration.put("lastName1", getApellido1());
                            registration.put("lastName2", getApellido2());
                            registration.put("gender", getSexo());
                            registration.put("street", getCalle());
                            registration.put("streetNumber", String.valueOf(getNumero()));
                            registration.put("postalCode", getCodigoPostal());
                        } catch (Exception error) {
                            getPg().setVisibility(View.INVISIBLE);
                            Toast.makeText(getActivity(), "No se pudieron preparar los datos", Toast.LENGTH_LONG).show();
                            return;
                        }

                        setJsObjRequest(new JsonObjectRequest(
                                Request.Method.POST,
                                WebService.getRegistrationUrl(),
                                registration,
                                response -> {
                                    getPg().setVisibility(View.INVISIBLE);
                                    finalizarRegistro(true);
                                },
                                error -> {
                                    getPg().setVisibility(View.INVISIBLE);
                                    Log.e("ActivityDatos", "Registro rechazado", error);
                                    Toast.makeText(getActivity(),
                                            "No se pudo registrar. Revisa los datos o si la cuenta ya existe.",
                                            Toast.LENGTH_LONG).show();
                                }));

                        getJsObjRequest().setTag(getColaPeticionesPrueba()); // nombramos la petición
                        getPeticionesRed().anhadirPeticionACola(getJsObjRequest()); // Lanzamos la petición
                    }

                    break;
                case "actualizacion_perfil":
                   /* Intent intentRecogido = getActivity().getIntent();
                    Usuario usuarioLogueado = (Usuario) intentRecogido.getSerializableExtra("usuario");

                    */
                    setNif(getUsuario().getNif());
                    getEdt_nif().setText(getUsuario().getNif());
                    getEdt_nif().setEnabled(false);
                    //edt_nif.setVisibility(View.INVISIBLE); // No se permite actualizar el campo clave, ni que introduzca un NIF porque podría actualizar datos de otro usuario
                    setEmail(getEdt_email().getText().toString());
                    setNombre(getEdt_nombre().getText().toString());
                    setApellido1(getEdt_apellido_1().getText().toString());
                    setApellido2(getEdt_apellido_2().getText().toString());
                    setCalle(getEdt_calle().getText().toString());
                    setCodigoPostal(getEdt_codigo_postal().getText().toString().trim());
                    setClave("");
                    if (getActivity().findViewById(R.id.contenedor_fragment_activity_datos)!=null) {
                        setImagen("imagen");
                    }
                    else {
                        setImagen(MainActivity.getUsuarioRecibido().getImagen());

                    }
                    try {
                        setNumero(Integer.parseInt(getEdt_numero().getText().toString()));
                        setPiso(Integer.parseInt(getEdt_piso().getText().toString()));
                        setPuerta(getEdt_puerta().getText().toString());
                        setEscalera(getEdt_escalera().getText().toString());
                        setBloque(Integer.parseInt(getEdt_bloque().getText().toString()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }


                    sexoEntero = getRdg_sexo().getCheckedRadioButtonId();

                    sexoStr = null;

                    if (sexoEntero == R.id.rb_hombre) {
                        sexoStr = "hombre";
                    } else if (sexoEntero == R.id.rb_mujer) {
                        sexoStr = "mujer";
                    }

                    if (getEmail().length() <= 0 || !Utilities.isValidEmail(getEmail()) || getNombre().length() <= 0 || getApellido1().length() <= 0 || getApellido2().length() <= 0 || getCalle().length() <= 0 || getNumero() <= 0 || !isValidPostalCode(getCodigoPostal()) || sexoEntero == -1) {
                        Toast.makeText(getActivity(), "Revise los datos introducidos", Toast.LENGTH_LONG).show();
                        getPg().setVisibility(View.GONE);
                    } else {
                        getPg().setVisibility(View.VISIBLE);
                        /*
                                                    setUsuario(new Usuario(getNif(), getEmail(), getNombre(), getApellido1(), getApellido2(), sexoStr, getCalle(), getNumero(), getPiso(), getPuerta(), getEscalera(), getBloque(),getCodigoPostal(), getTipoPersona(), getClave(), getImagen()));
                        */

                        getPg().setVisibility(View.VISIBLE);
                        if (!internetVerified) {
                            internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, reachedHost) -> {
                                if (reachable) {
                                    internetVerified = true;
                                    getBtn_aceptar().performClick();
                                } else {
                                    getPg().setVisibility(View.INVISIBLE);
                                    Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }
                        internetVerified = false;

                        if (!Utilities.isValidEmail(getEmail())) {
                            Toast.makeText(getActivity(), "Email no válido",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        JSONObject jsonUsuarioAActualizar;

                        try {
                            setUsuarioAInsertar(new Usuario(getNif(), getEmail(),
                                    getNombre(), getApellido1(), getApellido2(),
                                    sexoStr, getCalle(), getNumero(), getPiso(),
                                    getPuerta(),getEscalera(),getBloque(),
                                    getCodigoPostal(), getTipoPersona(), 0d,
                                    0d, getClave(), getImagen()));
                            jsonUsuarioAActualizar = new JSONObject();
                            jsonUsuarioAActualizar.put("firstName", getNombre());
                            jsonUsuarioAActualizar.put("lastName1", getApellido1());
                            jsonUsuarioAActualizar.put("lastName2", getApellido2());
                            jsonUsuarioAActualizar.put("gender", sexoStr);
                            jsonUsuarioAActualizar.put("street", getCalle());
                            jsonUsuarioAActualizar.put("streetNumber", String.valueOf(getNumero()));
                            jsonUsuarioAActualizar.put("postalCode", getCodigoPostal());
                        } catch (Exception e) {
                            getPg().setVisibility(View.INVISIBLE);
                            Toast.makeText(getActivity(), "Error al creaer JSON a enviar", Toast.LENGTH_LONG).show();
                            return;
                        }



                        setJsObjRequest(new AuthenticatedJsonRequest(Request.Method.PUT, WebService.getMeUrl(), jsonUsuarioAActualizar,
                                response -> {
                                    getPg().setVisibility(View.INVISIBLE);
                                    ((MainActivity) getActivity()).setUsuarioRecibido(getUsuarioAInsertar());
                                    Toast.makeText(getActivity(), "Perfil actualizado", Toast.LENGTH_LONG).show();
                                    Navigation.findNavController(getBtn_aceptar()).popBackStack();
                                }, error -> {
                            getPg().setVisibility(View.GONE);
                            Log.e("Actualización", "Error en la petición JSON: " + error.toString());
                            Toast.makeText(getActivity(), "No se pudo completar la actualización", Toast.LENGTH_SHORT).show();

                        }));

                        getJsObjRequest().setTag(getColaPeticionesLogin()); // nombramos la petición
                        getPeticionesRed().anhadirPeticionACola(getJsObjRequest()); // Lanzamos la petición

                    }


            }
        });

        getBtn_cancelar().setOnClickListener(v -> {
            if (getActivity().findViewById(R.id.contenedor_fragment_activity_datos)!=null) {
                getActivity().setResult(RESULT_CANCELED);
                getActivity().finish();
            }
            else {
                Navigation.findNavController(getBtn_aceptar()).popBackStack();
            }
        });

        return layout;
    }

    private void finalizarRegistro(boolean emailEnviado) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(
                getActivity(),
                emailEnviado
                        ? "Registro completado. Revisa tu correo."
                        : "Registro completado, pero no se pudo enviar el email de confirmación.",
                Toast.LENGTH_LONG
        ).show();
        // La cuenta debe confirmarse antes del primer login; no se crea una
        // sesión implícita ni se conserva la contraseña.
        getActivity().setResult(RESULT_CANCELED);
        getActivity().finish();
    }

    private void setLatitude(double i) {
        this.latitude = i;
    }

    private static boolean isValidPostalCode(String postalCode) {
        return postalCode != null && postalCode.matches("\\d{5}");
    }


    private String getImagen() {
        return imagen;
    }

    private EditText getEdt_nif() {
        return edt_nif;
    }

    private void setEdt_nif(EditText edt_nif) {
        this.edt_nif = edt_nif;
    }

    private EditText getEdt_clave() {
        return edt_clave;
    }

    private void setEdt_clave(EditText edt_clave) {
        this.edt_clave = edt_clave;
    }

    private EditText getEdt_apellido_1() {
        return edt_apellido_1;
    }

    private void setEdt_apellido_1(EditText edt_apellido_1) {
        this.edt_apellido_1 = edt_apellido_1;
    }

    private EditText getEdt_apellido_2() {
        return edt_apellido_2;
    }

    private void setEdt_apellido_2(EditText edt_apellido_2) {
        this.edt_apellido_2 = edt_apellido_2;
    }

    private EditText getEdt_calle() {
        return edt_calle;
    }

    private void setEdt_calle(EditText edt_calle) {
        this.edt_calle = edt_calle;
    }

    private EditText getEdt_numero() {
        return edt_numero;
    }

    private void setEdt_numero(EditText edt_numero) {
        this.edt_numero = edt_numero;
    }

    private EditText getEdt_codigo_postal() {
        return edt_codigo_postal;
    }

    private void setEdt_codigo_postal(EditText edt_codigo_postal) {
        this.edt_codigo_postal = edt_codigo_postal;
    }

    private TextView getTv_poblacion() {
        return tv_poblacion;
    }

    private void setTv_poblacion(TextView tv_poblacion) {
        this.tv_poblacion = tv_poblacion;
    }

    private TextView getTv_provincia() {
        return tv_provincia;
    }

    private void setTv_provincia(TextView tv_provincia) {
        this.tv_provincia = tv_provincia;
    }

    private Intent getIntentRecibido() {
        return intentRecibido;
    }

    private void setIntentRecibido(Intent intentRecibido) {
        this.intentRecibido = intentRecibido;
    }

    private String getAccion() {
        return accion;
    }

    private void setAccion(String accion) {
        this.accion = accion;
    }

    private ProgressBar getPg() {
        return pg;
    }

    private void setPg(ProgressBar pg) {
        this.pg = pg;
    }

    private PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    private void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    private String getNif() {
        return nif;
    }

    private void setNif(String nif) {
        this.nif = nif;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    private String getNombre() {
        return nombre;
    }

    private void setNombre(String nombre) {
        this.nombre = nombre;
    }

    private String getApellido1() {
        return apellido1;
    }

    private void setApellido1(String apellido1) {
        this.apellido1 = apellido1;
    }

    private String getApellido2() {
        return apellido2;
    }

    private void setApellido2(String apellido2) {
        this.apellido2 = apellido2;
    }

    private String getCalle() {
        return calle;
    }

    private void setCalle(String calle) {
        this.calle = calle;
    }

    private int getNumero() {
        return numero;
    }

    private void setNumero(int numero) {
        this.numero = numero;
    }

    private String getCodigoPostal() {
        return codigoPostal;
    }

    private void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = codigoPostal;
    }

    private String getTipoPersona() {
        return tipoPersona;
    }

    private void setTipoPersona(String tipoPersona) {
        this.tipoPersona = tipoPersona;
    }

    private String getClave() {
        return clave;
    }

    private void setClave(String clave) {
        this.clave = clave;
    }

    private JsonObjectRequest getJsObjRequest() {
        return jsObjRequest;
    }

    private void setJsObjRequest(JsonObjectRequest jsObjRequest) {
        this.jsObjRequest = jsObjRequest;
    }

    private String getUrlPeticion() {
        return urlPeticion;
    }

    private void setUrlPeticion(String urlPeticion) {
        this.urlPeticion = urlPeticion;
    }

    private List<PoblacionesProvincias> getPoblacionesProvinciasDescargadas() {
        return poblacionesProvinciasDescargadas;
    }

    private void setPoblacionesProvinciasDescargadas(List<PoblacionesProvincias> poblacionesProvinciasDescargadas) {
        this.poblacionesProvinciasDescargadas = poblacionesProvinciasDescargadas;
    }

    private ArrayAdapterPoblacionesProvincias getArrayAdapterPoblacionesProvincias() {
        return arrayAdapterPoblacionesProvincias;
    }

    private void setArrayAdapterPoblacionesProvincias(ArrayAdapterPoblacionesProvincias arrayAdapterPoblacionesProvincias) {
        this.arrayAdapterPoblacionesProvincias = arrayAdapterPoblacionesProvincias;
    }

    private ListView getLv_poblaciones_provincias() {
        return lv_poblaciones_provincias;
    }

    private void setLv_poblaciones_provincias(ListView lv_poblaciones_provincias) {
        this.lv_poblaciones_provincias = lv_poblaciones_provincias;
    }

    private AlertDialog.Builder getDatosDialog() {
        return datosDialog;
    }

    private void setDatosDialog(AlertDialog.Builder datosDialog) {
        this.datosDialog = datosDialog;
    }

    private String getSexo() {
        return sexo;
    }

    private void setSexo(String sexo) {
        this.sexo = sexo;
    }

    private void setImagen(String imagen) {
        this.imagen = imagen;
    }

    private Usuario getUsuarioAInsertar() {
        return usuarioAInsertar;
    }

    private void setUsuarioAInsertar(Usuario usuarioAInsertar) {
        this.usuarioAInsertar = usuarioAInsertar;
    }

    private int getPiso() {
        return piso;
    }

    private void setPiso(int piso) {
        this.piso = piso;
    }

    private String getPuerta() {
        return puerta;
    }

    private void setPuerta(String puerta) {
        this.puerta = puerta;
    }

    private String getEscalera() {
        return escalera;
    }

    private void setEscalera(String escalera) {
        this.escalera = escalera;
    }

    private int getBloque() {
        return bloque;
    }

    private void setBloque(int bloque) {
        this.bloque = bloque;
    }

    private EditText getEdt_piso() {
        return edt_piso;
    }

    private void setEdt_piso(EditText edt_piso) {
        this.edt_piso = edt_piso;
    }

    private EditText getEdt_puerta() {
        return edt_puerta;
    }

    private void setEdt_puerta(EditText edt_puerta) {
        this.edt_puerta = edt_puerta;
    }

    private EditText getEdt_escalera() {
        return edt_escalera;
    }

    private void setEdt_escalera(EditText edt_escalera) {
        this.edt_escalera = edt_escalera;
    }

    private EditText getEdt_bloque() {
        return edt_bloque;
    }

    private void setEdt_bloque(EditText edt_bloque) {
        this.edt_bloque = edt_bloque;
    }

    private double getLatitude() {
        return latitude;
    }

    private double getLongitude() {
        return longitude;
    }

    private void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public void onDestroyView() {
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
        internetVerified = false;
        super.onDestroyView();
    }
    /*
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datos);


    }*/


}
