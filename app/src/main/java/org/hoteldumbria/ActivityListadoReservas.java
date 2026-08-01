package org.hoteldumbria;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.hoteldumbria.ui.ArrayAdapterReservasHabitaciones;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActivityListadoReservas extends Fragment {
    private ConnectivityAndInternetAccess.Request internetAccessRequest;
    private ListView lv_reservas;
    private ArrayAdapterReservasHabitaciones arrayAdapterReservasHabitaciones;
    private List<ReservaListado> habitacionesDescargadas;
    private PeticionesRed peticionesRed;
    private static final String COLA_RESERVAS = "ActivityListadoReservas";

    private static String getColaReservas() {
        return COLA_RESERVAS;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.activity_listado_reservas, container, false);
        setLv_reservas(layout.findViewById(R.id.lv_reservas));

        setPeticionesRed(PeticionesRed.getInstancia(getActivity()));

        internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, reachedHost) -> {
            if (reachable && isAdded()) {
                cargarReservas(layout);
            } else if (isAdded()) {
                Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_SHORT).show();
            }
        });
        return layout;
    }

    @Override
    public void onDestroyView() {
        if (internetAccessRequest != null) {
            internetAccessRequest.cancel();
            internetAccessRequest = null;
        }
        super.onDestroyView();
    }

    private void cargarReservas(View layout) {
        AuthenticatedJsonRequest jsObjRequest = new AuthenticatedJsonRequest(
                Request.Method.GET, WebService.getMyReservationsUrl(), null,
                response -> {
                    try {
                        ArrayList<ReservaListado> reservations = new ArrayList<>();
                        org.json.JSONArray data = response.getJSONArray("data");
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        for (int index = 0; index < data.length(); index++) {
                            org.json.JSONObject item = data.getJSONObject(index);
                            reservations.add(new ReservaListado(
                                    item.getInt("roomId"),
                                    "",
                                    parser.parse(item.getString("checkIn")),
                                    parser.parse(item.getString("checkOut"))));
                        }
                        setHabitacionesDescargadas(reservations);
                        setArrayAdapterReservasHabitaciones(new ArrayAdapterReservasHabitaciones(
                                getActivity(), R.layout.layout_reservas, reservations, getPeticionesRed()));
                        getLv_reservas().setAdapter(getArrayAdapterReservasHabitaciones());
                        getLv_reservas().setDivider(null);
                        getLv_reservas().setDividerHeight(0);
                        if (reservations.isEmpty()) Toast.makeText(getActivity(),
                                "Todavía no ha realizado ninguna reserva", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("DescargaReservas", "Respuesta no válida", e);
                        setHabitacionesDescargadas(null);
                    }
                }, error -> {
                    Log.e("DescargaReservas", "Error en la petición", error);
                    setHabitacionesDescargadas(null);
                });

        jsObjRequest.setTag(getColaReservas()); // nombramos la petición
        getPeticionesRed().anhadirPeticionACola(jsObjRequest); // Lanzamos la petición

        getLv_reservas().setAdapter(getArrayAdapterReservasHabitaciones());
    }

    private ListView getLv_reservas() {
        return lv_reservas;
    }

    private void setLv_reservas(ListView lv_reservas) {
        this.lv_reservas = lv_reservas;
    }

    private ArrayAdapterReservasHabitaciones getArrayAdapterReservasHabitaciones() {
        return arrayAdapterReservasHabitaciones;
    }

    private void setArrayAdapterReservasHabitaciones(
            ArrayAdapterReservasHabitaciones arrayAdapterReservasHabitaciones) {
        this.arrayAdapterReservasHabitaciones = arrayAdapterReservasHabitaciones;
    }

    private List<ReservaListado> getHabitacionesDescargadas() {
        return habitacionesDescargadas;
    }

    private void setHabitacionesDescargadas(List<ReservaListado> habitacionesDescargadas) {
        this.habitacionesDescargadas = habitacionesDescargadas;
    }

    private PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    private void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }
}
