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
import androidx.appcompat.app.AlertDialog;

import com.android.volley.Request;

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
        if (getPeticionesRed() != null) {
            getPeticionesRed().getColaPeticiones().cancelAll(getColaReservas());
        }
        super.onDestroyView();
    }

    private void cargarReservas(View layout) {
        AuthenticatedJsonRequest jsObjRequest = new AuthenticatedJsonRequest(
                requireContext(), Request.Method.GET, WebService.getMyReservationsUrl(), null,
                response -> {
                    if (!isAdded() || getView() == null) return;
                    try {
                        ArrayList<ReservaListado> reservations = new ArrayList<>();
                        org.json.JSONArray data = response.getJSONArray("data");
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        for (int index = 0; index < data.length(); index++) {
                            org.json.JSONObject item = data.getJSONObject(index);
                            reservations.add(new ReservaListado(
                                    item.getString("id"),
                                    item.getInt("roomId"),
                                    item.optString("roomName", ""),
                                    item.getString("status"),
                                    parser.parse(item.getString("checkIn")),
                                    parser.parse(item.getString("checkOut"))));
                        }
                        setHabitacionesDescargadas(reservations);
                        setArrayAdapterReservasHabitaciones(new ArrayAdapterReservasHabitaciones(
                                getActivity(), R.layout.layout_reservas, reservations,
                                getPeticionesRed(), this::confirmarCancelacion));
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
                    if (!isAdded() || getView() == null) return;
                    Log.e("DescargaReservas", "Error en la petición", error);
                    setHabitacionesDescargadas(null);
                });

        jsObjRequest.setTag(getColaReservas()); // nombramos la petición
        getPeticionesRed().anhadirPeticionACola(jsObjRequest); // Lanzamos la petición

    }

    private void confirmarCancelacion(ReservaListado reservation) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Cancelar reserva")
                .setMessage("La cancelación está sujeta a la ventana configurada por el hotel.")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Cancelar reserva", (dialog, which) -> cancelarReserva(reservation))
                .show();
    }

    private void cancelarReserva(ReservaListado reservation) {
        internetAccessRequest = Utilidades.comprobarAccesoAInternet(requireContext(), (reachable, host) -> {
            internetAccessRequest = null;
            if (!isAdded()) return;
            if (!reachable) {
                Toast.makeText(getActivity(), "Sin acceso real a Internet", Toast.LENGTH_SHORT).show();
                return;
            }
            AuthenticatedJsonRequest request = new AuthenticatedJsonRequest(
                    requireContext(),
                    Request.Method.POST,
                    WebService.getCancelReservationUrl(reservation.getId()),
                    null,
                    response -> {
                        if (!isAdded()) return;
                        reservation.setStatus("cancelled");
                        getArrayAdapterReservasHabitaciones().notifyDataSetChanged();
                        Toast.makeText(getActivity(), "Reserva cancelada", Toast.LENGTH_SHORT).show();
                    },
                    error -> {
                        if (!isAdded()) return;
                        Toast.makeText(getActivity(),
                                "La reserva no puede cancelarse en este momento.",
                                Toast.LENGTH_LONG).show();
                    }
            );
            request.setTag(getColaReservas());
            getPeticionesRed().anhadirPeticionACola(request);
        });
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
