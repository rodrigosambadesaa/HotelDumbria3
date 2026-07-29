package org.hoteldumbria;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.hoteldumbria.ui.PoblacionesProvincias;

import java.util.List;

public class ArrayAdapterPoblacionesProvincias extends ArrayAdapter<PoblacionesProvincias> {
    private Activity activity;
    private int idLayoutPoblacionesProvincias;
    private List<PoblacionesProvincias> poblacionesProvincias;
    private PeticionesRed peticionesRed;

    public void setOcultarCheckboxes(boolean ocultarCheckboxes) {
        this.ocultarCheckboxes = ocultarCheckboxes;
    }

    private boolean ocultarCheckboxes;

    public ArrayAdapterPoblacionesProvincias(Context contexto, int indice, List < PoblacionesProvincias > listaNoticias, PeticionesRed peticionesRed) {
        super(contexto, indice, listaNoticias);

        setActivity((Activity) contexto);
        setIdLayoutPoblacionesProvincias(indice);
        setPoblacionesProvincias(listaNoticias);
        this.setPeticionesRed(peticionesRed);
    }

    public List<PoblacionesProvincias> getPoblacionesProvincias() {
        return poblacionesProvincias;
    }

    @Override
    public View getView(final int posicion, View convertView, ViewGroup padre) {
        //Añadidas las optimizaciones de memoria y CPU

        View layoutPoblacionesProvincias;
        ReferenciasLayoutPoblacionesProvincias referenciasLayoutPoblacionesProvincias;
        //checkBoxes = new ArrayList<>();
        if (convertView == null) {
            LayoutInflater inflador = getActivity().getLayoutInflater();
            layoutPoblacionesProvincias = inflador.inflate(getIdLayoutPoblacionesProvincias(), null);

            referenciasLayoutPoblacionesProvincias = new ReferenciasLayoutPoblacionesProvincias();
            referenciasLayoutPoblacionesProvincias.setTv_id_provincia(layoutPoblacionesProvincias.findViewById(R.id.tv_provincia_id));
            referenciasLayoutPoblacionesProvincias.setTv_nombre_provincia(layoutPoblacionesProvincias.findViewById(R.id.tv_nombre_provincia));
            referenciasLayoutPoblacionesProvincias.setTv_poblacion_id(layoutPoblacionesProvincias.findViewById(R.id.tv_poblacionid));
            referenciasLayoutPoblacionesProvincias.setTv_nombre_poblacion(layoutPoblacionesProvincias.findViewById(R.id.tv_nombre_poblacion));
            layoutPoblacionesProvincias.setTag(referenciasLayoutPoblacionesProvincias);

        } else {
            layoutPoblacionesProvincias = convertView;
            referenciasLayoutPoblacionesProvincias = (ReferenciasLayoutPoblacionesProvincias) layoutPoblacionesProvincias.getTag();
            //referenciasLayoutPoblacionesProvincias.tarea.cancel(true);
        }

        PoblacionesProvincias poblacionProvincia = getPoblacionesProvincias().get(posicion);
        referenciasLayoutPoblacionesProvincias.getTv_nombre_poblacion().setText(poblacionProvincia.getPoblacion());
        referenciasLayoutPoblacionesProvincias.getTv_poblacion_id().setText(String.valueOf(poblacionProvincia.getPoblacionid()));
        referenciasLayoutPoblacionesProvincias.getTv_nombre_provincia().setText(poblacionProvincia.getProvincia());
        referenciasLayoutPoblacionesProvincias.getTv_id_provincia().setText(String.valueOf(poblacionProvincia.getProvinciaid()));

        /*referenciasLayoutPoblacionesProvincias.tarea = new AsignaImagenDeURL(referenciasLayoutPoblacionesProvincias.imageViewImagenAsociada, activity);
        String descripcion = poblacionProvincia.getDescripcion();
        referenciasLayoutPoblacionesProvincias.tv_descripcion_noticia.setText(descripcion);
        referenciasLayoutPoblacionesProvincias.tarea.execute(poblacionProvincia.getUrlImagen());*/

        /*referenciasLayoutPoblacionesProvincias.tv_numero_habitacion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = poblacionProvincia.getEnlace();
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                activity.startActivity(i);
            }
        });*/

        return layoutPoblacionesProvincias;

    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public int getIdLayoutPoblacionesProvincias() {
        return idLayoutPoblacionesProvincias;
    }

    public void setIdLayoutPoblacionesProvincias(int idLayoutPoblacionesProvincias) {
        this.idLayoutPoblacionesProvincias = idLayoutPoblacionesProvincias;
    }

    public void setPoblacionesProvincias(List<PoblacionesProvincias> poblacionesProvincias) {
        this.poblacionesProvincias = poblacionesProvincias;
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

    static class ReferenciasLayoutPoblacionesProvincias {
        private TextView tv_poblacion_id;
        private TextView tv_nombre_poblacion;
        private TextView tv_id_provincia;
        private TextView tv_nombre_provincia;

        public TextView getTv_poblacion_id() {
            return tv_poblacion_id;
        }

        public void setTv_poblacion_id(TextView tv_poblacion_id) {
            this.tv_poblacion_id = tv_poblacion_id;
        }

        public TextView getTv_nombre_poblacion() {
            return tv_nombre_poblacion;
        }

        public void setTv_nombre_poblacion(TextView tv_nombre_poblacion) {
            this.tv_nombre_poblacion = tv_nombre_poblacion;
        }

        public TextView getTv_id_provincia() {
            return tv_id_provincia;
        }

        public void setTv_id_provincia(TextView tv_id_provincia) {
            this.tv_id_provincia = tv_id_provincia;
        }

        public TextView getTv_nombre_provincia() {
            return tv_nombre_provincia;
        }

        public void setTv_nombre_provincia(TextView tv_nombre_provincia) {
            this.tv_nombre_provincia = tv_nombre_provincia;
        }
    }
}
