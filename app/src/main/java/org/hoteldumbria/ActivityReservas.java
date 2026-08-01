package org.hoteldumbria;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ActivityReservas extends Fragment implements DatePickerDialog.OnDateSetListener {
    private static final int MAX_RESERVATION_NIGHTS = 30;
    private static final int BOOKING_HORIZON_MONTHS = 18;
    private static final TimeZone HOTEL_TIME_ZONE = TimeZone.getTimeZone("Europe/Madrid");
    private boolean internetVerified;
    private ConnectivityAndInternetAccess.Request internetAccessRequest;

    private static final int SOLICITUD_CONFIRMACION_RESERVA = 2;
    private static final int SOLICITUD_CIERRE_SESION = 3;
    private TextView tv_titulo;

    private static final int SOLICITUD_ACTUALIZACION = 1;

    private Usuario usuarioRecibido;
    private Button btn_reservar;
    private Button imbtn_confirmar_reservas;
    private ListView listViewHabitaciones;
    private ArrayAdapterHabitaciones arrayAdapterHabitaciones;
    private List < Habitacion > habitacionesAReservar;
    private List < Habitacion > habitacionesDescargadas;
    private int scrollVertical;
    private TextView tv_seleccion_fecha_entrada;
    private TextView tv_seleccion_fecha_salida;
    private DatePickerDialog dpd;

    private static final String COLA_RESERVAS = "ActivityReservas";
    private static final String COLA_PETICIONES_CONFIRMACION_RESERVA = "PeticionesConfirmacionReserva";
    private PeticionesRed peticionesRed;
    private ProgressBar pg;
    private Calendar c;
    private Calendar fecha_entrada;
    private Calendar fecha_salida;
    private boolean selectingCheckIn;

    private AlertDialog.Builder datosDialog;

    public static int getSolicitudConfirmacionReserva() {
        return SOLICITUD_CONFIRMACION_RESERVA;
    }

    private String formatDate(Calendar date) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH));
    }

    private void reservarHabitaciones(List<Habitacion> habitaciones) {
        JSONObject body = new JSONObject();
        try {
            JSONArray reservations = new JSONArray();
            for (Habitacion habitacion : habitaciones) {
                JSONObject reservation = new JSONObject();
                reservation.put("roomId", habitacion.getNumero());
                reservation.put("checkIn", formatDate(getFecha_entrada()));
                reservation.put("checkOut", formatDate(getFecha_salida()));
                reservation.put("guests", 1);
                reservations.put(reservation);
            }
            body.put("reservations", reservations);
        } catch (Exception error) {
            Toast.makeText(getActivity(), "No se pudo preparar la reserva", Toast.LENGTH_SHORT).show();
            return;
        }
        AuthenticatedJsonRequest request = new AuthenticatedJsonRequest(
                requireContext(),
                Request.Method.POST,
                WebService.getBulkReservationsUrl(),
                body,
                response -> {
                    getPg().setVisibility(View.INVISIBLE);
                    Toast.makeText(getActivity(), "Reservas realizadas correctamente", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    getPg().setVisibility(View.INVISIBLE);
                    Log.e("ActivityReservas", "Reserva rechazada", error);
                    Toast.makeText(getActivity(),
                            "No se pudo completar; la habitación puede haberse ocupado.",
                            Toast.LENGTH_LONG).show();
                });
        request.setTag(getColaPeticionesConfirmacionReserva());
        getPeticionesRed().anhadirPeticionACola(request);
    }



    public static int getSolicitudCierreSesion() {
        return SOLICITUD_CIERRE_SESION;
    }

    public static int getSolicitudActualizacion() {
        return SOLICITUD_ACTUALIZACION;
    }

    private static String getColaReservas() {
        return COLA_RESERVAS;
    }

    private static String getColaPeticionesConfirmacionReserva() {
        return COLA_PETICIONES_CONFIRMACION_RESERVA;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.activity_reservas,container,false);
        setPeticionesRed(PeticionesRed.getInstancia(getActivity()));
        if (savedInstanceState != null) {
            setHabitacionesDescargadas((List < Habitacion > ) savedInstanceState.getSerializable("habitaciones"));
            setScrollVertical(savedInstanceState.getInt("scroll_vertical"));
            if (savedInstanceState.containsKey("check_in")) {
                fecha_entrada = hotelCalendar();
                fecha_entrada.setTimeInMillis(savedInstanceState.getLong("check_in"));
            }
            if (savedInstanceState.containsKey("check_out")) {
                fecha_salida = hotelCalendar();
                fecha_salida.setTimeInMillis(savedInstanceState.getLong("check_out"));
            }
        } else {
            setHabitacionesDescargadas(null);
        }

        setUsuarioRecibido(MainActivity.getUsuarioRecibido());
        setBtn_reservar(layout.findViewById(R.id.bt_reservar_habitacion));
        setImbtn_confirmar_reservas(layout.findViewById(R.id.bt_confirmar_reservas));
        setPg(layout.findViewById(R.id.pgb_confirmacion_reservas));
        setTv_seleccion_fecha_entrada(layout.findViewById(R.id.tv_fecha_entrada));
        setTv_seleccion_fecha_salida(layout.findViewById(R.id.tv_fecha_salida));

        getTv_seleccion_fecha_entrada().setOnClickListener(v -> {
            selectingCheckIn = true;
            showDatePickerDialog();
        });

        getTv_seleccion_fecha_salida().setOnClickListener(v -> {
            selectingCheckIn = false;
            showDatePickerDialog();
        });
        renderSelectedDates();

        getPg().setVisibility(View.INVISIBLE);

        getImbtn_confirmar_reservas().setOnClickListener(v -> {
            if (!validateSelectedDates()) return;
            if (!internetVerified) {
                internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, reachedHost) -> {
                    if (reachable && isAdded()) {
                        internetVerified = true;
                        getImbtn_confirmar_reservas().performClick();
                    } else if (isAdded()) {
                        getPg().setVisibility(View.INVISIBLE);
                        Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            internetVerified = false;

            setHabitacionesAReservar(new ArrayList < > ());
            int indice = 0;
            if(getArrayAdapterHabitaciones()!=null)
                for (Habitacion h: getArrayAdapterHabitaciones().getHabitaciones()) {
                    if (getArrayAdapterHabitaciones().getSelectionStates()[indice]) {
                        getHabitacionesAReservar().add(h);
                    }
                    indice++;
                }
            else {
                Toast.makeText(getActivity(), "Debe seleccionar alguna habitación", Toast.LENGTH_SHORT).show();
                return;
            }
            if (getHabitacionesAReservar().size() > 0) {
               /* Intent intentActivityConfirmacionReservas = new Intent(getActivity(), ActivityConfirmacionReservas.class);
                intentActivityConfirmacionReservas.putExtra("habitaciones", (Serializable) habitacionesAReservar);
                intentActivityConfirmacionReservas.putExtra("usuario", usuarioRecibido);
                intentActivityConfirmacionReservas.putExtra("fecha_entrada", fecha_entrada);
                intentActivityConfirmacionReservas.putExtra("fecha_salida", fecha_salida);
                intentActivityConfirmacionReservas.setAction("confirmacion_reservas");
                startActivityForResult(intentActivityConfirmacionReservas, SOLICITUD_CONFIRMACION_RESERVA);

                */

                setDatosDialog(new AlertDialog.Builder(getActivity()));
                //datosDialog.setIcon(R.drawable.ic_launcher_foreground);
                getDatosDialog().setTitle("¿Desea reservar estas habitaciones?");

                final ArrayAdapterHabitaciones arrayAdapterHabitaciones = new ArrayAdapterHabitaciones(getActivity(), R.layout.layout_habitaciones, getHabitacionesAReservar(), getPeticionesRed());

                getDatosDialog().setAdapter(arrayAdapterHabitaciones, (dialog, which) -> {

                });

                arrayAdapterHabitaciones.setOcultarCheckboxes(true);

                getDatosDialog().setPositiveButton("Confirmar Reserva",
                        (dialog, which) -> reservarHabitaciones(getHabitacionesAReservar()));
                getDatosDialog().setNegativeButton("Cancelar Reserva",
                        (dialog, which) -> Toast.makeText(getActivity(), "Reservas canceladas", Toast.LENGTH_SHORT).show());

                getDatosDialog().create().show();

            } else {
                Toast.makeText(getActivity(), "Seleccione alguna habitación", Toast.LENGTH_SHORT).show();
            }

        });



        getBtn_reservar().setOnClickListener(v -> {
            if (!validateSelectedDates()) return;
            if (!internetVerified) {
                internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, reachedHost) -> {
                    if (reachable && isAdded()) {
                        internetVerified = true;
                        getBtn_reservar().performClick();
                    } else if (isAdded()) {
                        getPg().setVisibility(View.INVISIBLE);
                        Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            internetVerified = false;
            if (getFecha_entrada()!= null && getFecha_salida() != null) {
                setListViewHabitaciones(layout.findViewById(R.id.listViewHabitaciones));
                // Se lanza la tarea que descarga, generara la lista de noticias y al finalizar entregara al metodo onRecibeHabitaciones
                getPg().setVisibility(View.VISIBLE);
                setArrayAdapterHabitaciones(new ArrayAdapterHabitaciones(getActivity(), R.layout.layout_habitaciones,
                        new ArrayList<>(), getPeticionesRed()));
                setListViewHabitaciones(layout.findViewById(R.id.listViewHabitaciones));
                getListViewHabitaciones().setAdapter(getArrayAdapterHabitaciones());

                String urlPeticion = WebService.getAvailabilityUrl()
                        + "?checkIn=" + formatDate(getFecha_entrada())
                        + "&checkOut=" + formatDate(getFecha_salida())
                        + "&guests=1";
                JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.GET, urlPeticion, null,
                        response -> {
                            if (!isAdded() || getView() == null) return;
                            getPg().setVisibility(View.INVISIBLE);

                            try {
                                List<Habitacion> rooms = new ArrayList<>();
                                org.json.JSONArray data = response.getJSONArray("data");
                                for (int index = 0; index < data.length(); index++) {
                                    JSONObject item = data.getJSONObject(index);
                                    Habitacion room = new Habitacion(
                                            item.getInt("id"),
                                            new BigDecimal(item.getString("pricePerNight")),
                                            item.getInt("areaM2"),
                                            item.getInt("maxGuests"));
                                    room.setImagen(item.optString("imageUrl", ""));
                                    rooms.add(room);
                                }
                                setHabitacionesDescargadas(rooms);
                                setArrayAdapterHabitaciones(new ArrayAdapterHabitaciones(
                                        getActivity(), R.layout.layout_habitaciones, rooms, getPeticionesRed()));
                                getListViewHabitaciones().setAdapter(getArrayAdapterHabitaciones());
                                getListViewHabitaciones().setDivider(null);
                                getListViewHabitaciones().setDividerHeight(0);
                                if (rooms.isEmpty()) Toast.makeText(getActivity(),
                                        "No hay habitaciones disponibles para esas fechas",
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e("DescargaHabitaciones", "Error al convertir JSON a Objetos JAVA");
                                setHabitacionesDescargadas(null);
                            }

                        }, error -> {
                    if (!isAdded() || getView() == null) return;

                    //pg.setVisibility(View.INVISIBLE);
                    Log.e("DescargaHabitaciones", "Error en la petición JSON: " + error.toString());
                    setHabitacionesDescargadas(null);


                });

                jsObjRequest.setTag(getColaReservas()); // nombramos la petición
                getPeticionesRed().anhadirPeticionACola(jsObjRequest); // Lanzamos la petición



 /*listViewHabitaciones.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          ArrayList < Habitacion > poblacionesProvincias = arrayAdapterHabitaciones.getPoblacionesProvincias();
          Habitacion habitacion = poblacionesProvincias.get(position);
          // habitacionesAReservar.add(habitacion);
          // Toast.makeText(getActivity(), "Habitación añadida a la lista de reserva", Toast.LENGTH_SHORT).show();
          ArrayAdapterHabitaciones.ReferenciasLayoutPoblacionesProvincias referenciasLayoutHabitaciones = (ArrayAdapterHabitaciones.ReferenciasLayoutPoblacionesProvincias) view.getTag();
          referenciasLayoutHabitaciones.chk_reservar_habitacion.setChecked(true);
      }
  });
  */

            } else {
                Toast.makeText(getActivity(), "Debe seleccionar las fechas de entrada y salida", Toast.LENGTH_SHORT).show();
            }
        });



        String mensajeBienvenida = " · bienvenido/a";
        String sexo = getUsuarioRecibido().getSexo();
        if ("hombre".equalsIgnoreCase(sexo)) {
            mensajeBienvenida = " · bienvenido";
        } else if ("mujer".equalsIgnoreCase(sexo)) {
            mensajeBienvenida = " · bienvenida";
        }

        setTv_titulo(layout.findViewById(R.id.tv_hotel_dumbria_texto_bienvenida));
        getTv_titulo().setText(getUsuarioRecibido().getNombre() + " ("
                + getUsuarioRecibido().getEmail() + ")" + mensajeBienvenida);

        return  layout;
    }

    private void showDatePickerDialog() {
        Calendar today = hotelCalendar();
        Calendar maximum = hotelCalendar();
        maximum.add(Calendar.MONTH, BOOKING_HORIZON_MONTHS);
        Calendar initial = selectingCheckIn ? fecha_entrada : fecha_salida;
        if (initial == null) initial = today;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                getActivity(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = hotelCalendar();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    if (selectingCheckIn) {
                        fecha_entrada = selected;
                    } else {
                        fecha_salida = selected;
                    }
                    renderSelectedDates();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(today.getTimeInMillis());
        datePickerDialog.getDatePicker().setMaxDate(maximum.getTimeInMillis());
        datePickerDialog.show();
    }

    private Calendar hotelCalendar() {
        Calendar value = Calendar.getInstance(HOTEL_TIME_ZONE, Locale.US);
        value.set(Calendar.HOUR_OF_DAY, 0);
        value.set(Calendar.MINUTE, 0);
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return value;
    }

    private void renderSelectedDates() {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        format.setTimeZone(HOTEL_TIME_ZONE);
        getTv_seleccion_fecha_entrada().setText(fecha_entrada == null
                ? getString(R.string.check_in) : format.format(fecha_entrada.getTime()));
        getTv_seleccion_fecha_salida().setText(fecha_salida == null
                ? getString(R.string.check_out) : format.format(fecha_salida.getTime()));
    }

    private boolean validateSelectedDates() {
        if (fecha_entrada == null || fecha_salida == null) {
            Toast.makeText(getActivity(), "Debe seleccionar entrada y salida", Toast.LENGTH_SHORT).show();
            return false;
        }
        Calendar today = hotelCalendar();
        if (fecha_entrada.before(today)) {
            Toast.makeText(getActivity(), "No se puede reservar en el pasado", Toast.LENGTH_SHORT).show();
            return false;
        }
        int nights = daysBetween(fecha_entrada, fecha_salida);
        if (nights <= 0) {
            Toast.makeText(getActivity(), "La salida debe ser posterior a la entrada", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (nights > MAX_RESERVATION_NIGHTS) {
            Toast.makeText(getActivity(), "La estancia máxima es de 30 noches", Toast.LENGTH_SHORT).show();
            return false;
        }
        Calendar horizon = hotelCalendar();
        horizon.add(Calendar.MONTH, BOOKING_HORIZON_MONTHS);
        if (fecha_entrada.after(horizon)) {
            Toast.makeText(getActivity(), "La fecha supera el horizonte de reserva", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private int daysBetween(Calendar start, Calendar end) {
        return (int) ((utcDay(end) - utcDay(start)) / 86_400_000L);
    }

    private long utcDay(Calendar source) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }



/*
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservas);

    }

*/

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getArrayAdapterHabitaciones() != null) {
            int scrollVertical = getListViewHabitaciones().getScrollY();
            outState.putSerializable(
                    "habitaciones",
                    new ArrayList<>(getArrayAdapterHabitaciones().getHabitaciones())
            );
            outState.putInt("scroll_vertical", scrollVertical);
        }
        if (fecha_entrada != null) outState.putLong("check_in", fecha_entrada.getTimeInMillis());
        if (fecha_salida != null) outState.putLong("check_out", fecha_salida.getTimeInMillis());
    }

    private TextView getTv_titulo() {
        return tv_titulo;
    }

    private void setTv_titulo(TextView tv_titulo) {
        this.tv_titulo = tv_titulo;
    }

    private Usuario getUsuarioRecibido() {
        return usuarioRecibido;
    }

    private void setUsuarioRecibido(Usuario usuarioRecibido) {
        this.usuarioRecibido = usuarioRecibido;
    }

    private Button getBtn_reservar() {
        return btn_reservar;
    }

    private void setBtn_reservar(Button btn_reservar) {
        this.btn_reservar = btn_reservar;
    }

    private Button getImbtn_confirmar_reservas() {
        return imbtn_confirmar_reservas;
    }

    private void setImbtn_confirmar_reservas(Button imbtn_confirmar_reservas) {
        this.imbtn_confirmar_reservas = imbtn_confirmar_reservas;
    }

    private ListView getListViewHabitaciones() {
        return listViewHabitaciones;
    }

    private void setListViewHabitaciones(ListView listViewHabitaciones) {
        this.listViewHabitaciones = listViewHabitaciones;
    }

    private ArrayAdapterHabitaciones getArrayAdapterHabitaciones() {
        return arrayAdapterHabitaciones;
    }

    private void setArrayAdapterHabitaciones(
            ArrayAdapterHabitaciones arrayAdapterHabitaciones) {
        this.arrayAdapterHabitaciones = arrayAdapterHabitaciones;
    }

    private List<Habitacion> getHabitacionesAReservar() {
        return habitacionesAReservar;
    }

    private void setHabitacionesAReservar(List<Habitacion> habitacionesAReservar) {
        this.habitacionesAReservar = habitacionesAReservar;
    }

    private List<Habitacion> getHabitacionesDescargadas() {
        return habitacionesDescargadas;
    }

    private void setHabitacionesDescargadas(List<Habitacion> habitacionesDescargadas) {
        this.habitacionesDescargadas = habitacionesDescargadas;
    }

    public int getScrollVertical() {
        return scrollVertical;
    }

    private void setScrollVertical(int scrollVertical) {
        this.scrollVertical = scrollVertical;
    }

    private TextView getTv_seleccion_fecha_entrada() {
        return tv_seleccion_fecha_entrada;
    }

    private void setTv_seleccion_fecha_entrada(TextView tv_seleccion_fecha_entrada) {
        this.tv_seleccion_fecha_entrada = tv_seleccion_fecha_entrada;
    }

    private TextView getTv_seleccion_fecha_salida() {
        return tv_seleccion_fecha_salida;
    }

    private void setTv_seleccion_fecha_salida(TextView tv_seleccion_fecha_salida) {
        this.tv_seleccion_fecha_salida = tv_seleccion_fecha_salida;
    }

    public DatePickerDialog getDpd() {
        return dpd;
    }

    public void setDpd(DatePickerDialog dpd) {
        this.dpd = dpd;
    }

    private PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    private void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    private ProgressBar getPg() {
        return pg;
    }

    private void setPg(ProgressBar pg) {
        this.pg = pg;
    }

    public Calendar getC() {
        return c;
    }

    public void setC(Calendar c) {
        this.c = c;
    }

    private Calendar getFecha_entrada() {
        return fecha_entrada;
    }

    public void setFecha_entrada(Calendar fecha_entrada) {
        this.fecha_entrada = fecha_entrada;
    }

    private Calendar getFecha_salida() {
        return fecha_salida;
    }

    public void setFecha_salida(Calendar fecha_salida) {
        this.fecha_salida = fecha_salida;
    }

    private AlertDialog.Builder getDatosDialog() {
        return datosDialog;
    }

    private void setDatosDialog(AlertDialog.Builder datosDialog) {
        this.datosDialog = datosDialog;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {

    }

    @Override
    public void onDestroyView() {
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
        internetVerified = false;
        if (getPeticionesRed() != null) {
            getPeticionesRed().getColaPeticiones().cancelAll(getColaReservas());
            getPeticionesRed().getColaPeticiones().cancelAll(
                    getColaPeticionesConfirmacionReserva());
        }
        super.onDestroyView();
    }

 /*@Override
 protected void onRestoreInstanceState(Bundle savedInstanceState) {
 	super.onRestoreInstanceState(savedInstanceState);
 	List<NoticiaRSS> noticias = (List<NoticiaRSS>) savedInstanceState.getSerializable("noticias");
 	arrayAdapterHabitaciones = new ArrayAdapterNoticias(this, R.layout.layout_noticias, noticias);
 	listViewNoticias = layout.findViewById(R.id.listViewNoticias);
 	listViewNoticias.setAdapter(arrayAdapterHabitaciones);
 }*/
}
