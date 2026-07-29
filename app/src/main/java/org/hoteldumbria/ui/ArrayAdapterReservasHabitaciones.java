package org.hoteldumbria.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


import org.hoteldumbria.PeticionesRed;
import org.hoteldumbria.R;
import org.hoteldumbria.ReservaListado;

import java.text.SimpleDateFormat;
import java.util.List;

public class ArrayAdapterReservasHabitaciones extends ArrayAdapter <ReservaListado> {

    private Activity activity;
    private int idLayoutHabitaciones;
    private List <ReservaListado> habitaciones;
    private PeticionesRed peticionesRed;

    public void setOcultarCheckboxes(boolean ocultarCheckboxes) {
        this.ocultarCheckboxes = ocultarCheckboxes;
    }

    private boolean ocultarCheckboxes;

    public ArrayAdapterReservasHabitaciones(Context contexto, int indice, List < ReservaListado > listaReservas, PeticionesRed peticionesRed) {
        super(contexto, indice, listaReservas);

        setActivity((Activity) contexto);
        setIdLayoutHabitaciones(indice);
        setReservas(listaReservas);
        this.setPeticionesRed(peticionesRed);
    }

    public List<ReservaListado> getReservas() {
        return getHabitaciones();
    }

    @Override
    public View getView(final int posicion, View convertView, ViewGroup padre) {
        //Añadidas las optimizaciones de memoria y CPU

        View layoutReservas;
        ReferenciasLayoutHabitaciones referenciasLayoutHabitaciones;
        if (convertView == null) {
            LayoutInflater inflador = getActivity().getLayoutInflater();
            layoutReservas = inflador.inflate(getIdLayoutHabitaciones(), null);

            referenciasLayoutHabitaciones = new ReferenciasLayoutHabitaciones();
            referenciasLayoutHabitaciones.setTv_numero_habitacion_reservada(layoutReservas.findViewById(R.id.tv_numero_habitacion_reservada));
            referenciasLayoutHabitaciones.setTv_fecha_entrada(layoutReservas.findViewById(R.id.tv_fecha_entrada));
            referenciasLayoutHabitaciones.setTv_fecha_salida(layoutReservas.findViewById(R.id.tv_fecha_salida));

            referenciasLayoutHabitaciones.getTv_fecha_salida().setTextColor(Color.parseColor("#ff7315"));
            referenciasLayoutHabitaciones.getTv_fecha_entrada().setTextColor(Color.parseColor("#ff7315"));
            referenciasLayoutHabitaciones.getTv_numero_habitacion_reservada().setTextColor(Color.parseColor("#ff7315"));

            layoutReservas.setTag(referenciasLayoutHabitaciones);

        } else {
            layoutReservas = convertView;
            referenciasLayoutHabitaciones = (ReferenciasLayoutHabitaciones) layoutReservas.getTag();
            //referenciasLayoutHabitaciones.tarea.cancel(true);
        }

        ReservaListado reserva = getReservas().get(posicion);
        referenciasLayoutHabitaciones.getTv_numero_habitacion_reservada().setText(
                "Habitación reservada: " + reserva.getNumero());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        referenciasLayoutHabitaciones.getTv_fecha_entrada().setText("Fecha de entrada: " + simpleDateFormat.format(reserva.getFecha_entrada().getTime()));
        referenciasLayoutHabitaciones.getTv_fecha_salida().setText("Fecha de salida: " + simpleDateFormat.format(reserva.getFecha_salida().getTime()));

        /*referenciasLayoutHabitaciones.tarea = new AsignaImagenDeURL(referenciasLayoutHabitaciones.imageViewImagenAsociada, activity);
        String descripcion = reserva.getDescripcion();
        referenciasLayoutHabitaciones.tv_descripcion_noticia.setText(descripcion);
        referenciasLayoutHabitaciones.tarea.execute(reserva.getUrlImagen());*/

        /*referenciasLayoutHabitaciones.tv_numero_habitacion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = reserva.getEnlace();
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                activity.startActivity(i);
            }
        });*/

        return layoutReservas;

    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public int getIdLayoutHabitaciones() {
        return idLayoutHabitaciones;
    }

    public void setIdLayoutHabitaciones(int idLayoutHabitaciones) {
        this.idLayoutHabitaciones = idLayoutHabitaciones;
    }

    public void setReservas(List<ReservaListado> habitaciones) {
        this.setHabitaciones(habitaciones);
    }



    public PeticionesRed getPeticionesRed() {
        return peticionesRed;
    }

    public void setPeticionesRed(PeticionesRed peticionesRed) {
        this.peticionesRed = peticionesRed;
    }

    public boolean isOcultarCheckboxes() {
        return ocultarCheckboxes;
    }

    public List<ReservaListado> getHabitaciones() {
        return habitaciones;
    }

    public void setHabitaciones(List<ReservaListado> habitaciones) {
        this.habitaciones = habitaciones;
    }

    static class ReferenciasLayoutHabitaciones {

        private TextView tv_numero_habitacion_reservada;
        private TextView tv_fecha_entrada;
        private TextView tv_fecha_salida;

        public TextView getTv_numero_habitacion_reservada() {
            return tv_numero_habitacion_reservada;
        }

        public void setTv_numero_habitacion_reservada(TextView tv_numero_habitacion_reservada) {
            this.tv_numero_habitacion_reservada = tv_numero_habitacion_reservada;
        }

        public TextView getTv_fecha_entrada() {
            return tv_fecha_entrada;
        }

        public void setTv_fecha_entrada(TextView tv_fecha_entrada) {
            this.tv_fecha_entrada = tv_fecha_entrada;
        }

        public TextView getTv_fecha_salida() {
            return tv_fecha_salida;
        }

        public void setTv_fecha_salida(TextView tv_fecha_salida) {
            this.tv_fecha_salida = tv_fecha_salida;
        }
    }
}
