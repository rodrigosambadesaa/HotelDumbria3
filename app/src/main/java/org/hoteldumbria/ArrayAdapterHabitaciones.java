package org.hoteldumbria;



import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;


import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.List;

public class ArrayAdapterHabitaciones extends ArrayAdapter < Habitacion > {

    private Activity activity;
    private int idLayoutHabitaciones;
    private List < Habitacion > habitaciones;
    private List<CheckBox> checkBoxes;
    private PeticionesRed peticionesRed;

    public void setOcultarCheckboxes(boolean ocultarCheckboxes) {
        this.ocultarCheckboxes = ocultarCheckboxes;
    }

    private boolean ocultarCheckboxes;

    public List<CheckBox> getCheckBoxes() {
        return checkBoxes;
    }

    public boolean[] getSelectionStates() {
        return selectionStates;
    }

    private boolean[] selectionStates;

    public ArrayAdapterHabitaciones(Context contexto, int indice, List < Habitacion > listaNoticias, PeticionesRed peticionesRed) {
        super(contexto, indice, listaNoticias);

        setActivity((Activity) contexto);
        setIdLayoutHabitaciones(indice);
        setHabitaciones(listaNoticias);
        setSelectionStates(new boolean[listaNoticias.size()]);
        this.setPeticionesRed(peticionesRed);
    }

    public List<Habitacion> getHabitaciones() {
        return habitaciones;
    }

    @Override
    public View getView(final int posicion, View convertView, ViewGroup padre) {
        //Añadidas las optimizaciones de memoria y CPU

        View layoutHabitaciones;
        ReferenciasLayoutHabitaciones referenciasLayoutHabitaciones;
        setCheckBoxes(new ArrayList<>());
        if (convertView == null) {
            LayoutInflater inflador = getActivity().getLayoutInflater();
            layoutHabitaciones = inflador.inflate(getIdLayoutHabitaciones(), null);

            referenciasLayoutHabitaciones = new ReferenciasLayoutHabitaciones();
            referenciasLayoutHabitaciones.setNiv_imagen_habitacion(layoutHabitaciones.findViewById(R.id.niv_imagen_habitacion));
            referenciasLayoutHabitaciones.setTv_numero_habitacion(layoutHabitaciones.findViewById(R.id.tv_numero_habitacion));
            referenciasLayoutHabitaciones.setTv_precio_habitacion(layoutHabitaciones.findViewById(R.id.tv_precio_habitacion));
            referenciasLayoutHabitaciones.setTextViewArea(layoutHabitaciones.findViewById(R.id.tv_area_habitacion));
            referenciasLayoutHabitaciones.setTv_numero_huespedes(layoutHabitaciones.findViewById(R.id.tv_numero_huespedes));
            referenciasLayoutHabitaciones.setChk_reservar_habitacion(layoutHabitaciones.findViewById(R.id.chk_reservar_habitacion));

            getCheckBoxes().add(referenciasLayoutHabitaciones.getChk_reservar_habitacion());

            referenciasLayoutHabitaciones.setTv_precio_habitacion(layoutHabitaciones.findViewById(R.id.tv_precio_habitacion));
            layoutHabitaciones.setTag(referenciasLayoutHabitaciones);

        } else {
            layoutHabitaciones = convertView;
            referenciasLayoutHabitaciones = (ReferenciasLayoutHabitaciones) layoutHabitaciones.getTag();
            //referenciasLayoutHabitaciones.tarea.cancel(true);
        }

        Habitacion habitacion = getHabitaciones().get(posicion);
        String imageUrl = habitacion.getImagen();
        if (imageUrl != null && (imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))) {
            referenciasLayoutHabitaciones.getNiv_imagen_habitacion()
                    .setImageUrl(imageUrl, getPeticionesRed().getImageLoader());
        } else {
            referenciasLayoutHabitaciones.getNiv_imagen_habitacion().setImageUrl(
                    "", getPeticionesRed().getImageLoader());
        }
        referenciasLayoutHabitaciones.getTv_numero_habitacion().setText(
                "Número: " + habitacion.getNumero());
        referenciasLayoutHabitaciones.getTv_precio_habitacion().setText("Precio: " + habitacion.getPrecio().toBigInteger().toString()+" €");
        referenciasLayoutHabitaciones.getTextViewArea().setText(
                "Área: " + habitacion.getArea() + " m" + Utilidades.superindice("2"));
        referenciasLayoutHabitaciones.getTv_numero_huespedes().setText(
                "Número de huéspedes: " + habitacion.getNum_huespedes());
        referenciasLayoutHabitaciones.getChk_reservar_habitacion().setOnCheckedChangeListener(null);
        referenciasLayoutHabitaciones.getChk_reservar_habitacion().setChecked(
                getSelectionStates()[posicion]
        );
        referenciasLayoutHabitaciones.getChk_reservar_habitacion().setVisibility(
                isOcultarCheckboxes() ? View.GONE : View.VISIBLE
        );
        referenciasLayoutHabitaciones.getChk_reservar_habitacion().setOnCheckedChangeListener(
                (buttonView, isChecked) -> getSelectionStates()[posicion] = isChecked
        );

        /*referenciasLayoutHabitaciones.tarea = new AsignaImagenDeURL(referenciasLayoutHabitaciones.imageViewImagenAsociada, activity);
        String descripcion = habitacion.getDescripcion();
        referenciasLayoutHabitaciones.tv_descripcion_noticia.setText(descripcion);
        referenciasLayoutHabitaciones.tarea.execute(habitacion.getUrlImagen());*/

        /*referenciasLayoutHabitaciones.tv_numero_habitacion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = habitacion.getEnlace();
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                activity.startActivity(i);
            }
        });*/

        return layoutHabitaciones;

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

    public void setHabitaciones(List<Habitacion> habitaciones) {
        this.habitaciones = habitaciones;
    }

    public void setCheckBoxes(List<CheckBox> checkBoxes) {
        this.checkBoxes = checkBoxes;
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

    public void setSelectionStates(boolean[] selectionStates) {
        this.selectionStates = selectionStates;
    }

    static class ReferenciasLayoutHabitaciones {
        private NetworkImageView niv_imagen_habitacion;
        private TextView tv_numero_habitacion;
        private TextView tv_numero_huespedes;
        private TextView tv_precio_habitacion;
        private TextView textViewArea;

        public CheckBox getChk_reservar_habitacion() {
            return chk_reservar_habitacion;
        }

        private CheckBox chk_reservar_habitacion;

        public NetworkImageView getNiv_imagen_habitacion() {
            return niv_imagen_habitacion;
        }

        public void setNiv_imagen_habitacion(NetworkImageView niv_imagen_habitacion) {
            this.niv_imagen_habitacion = niv_imagen_habitacion;
        }

        public TextView getTv_numero_habitacion() {
            return tv_numero_habitacion;
        }

        public void setTv_numero_habitacion(TextView tv_numero_habitacion) {
            this.tv_numero_habitacion = tv_numero_habitacion;
        }

        public TextView getTv_numero_huespedes() {
            return tv_numero_huespedes;
        }

        public void setTv_numero_huespedes(TextView tv_numero_huespedes) {
            this.tv_numero_huespedes = tv_numero_huespedes;
        }

        public TextView getTv_precio_habitacion() {
            return tv_precio_habitacion;
        }

        public void setTv_precio_habitacion(TextView tv_precio_habitacion) {
            this.tv_precio_habitacion = tv_precio_habitacion;
        }

        public TextView getTextViewArea() {
            return textViewArea;
        }

        public void setTextViewArea(TextView textViewArea) {
            this.textViewArea = textViewArea;
        }

        public void setChk_reservar_habitacion(CheckBox chk_reservar_habitacion) {
            this.chk_reservar_habitacion = chk_reservar_habitacion;
        }
    }
}
