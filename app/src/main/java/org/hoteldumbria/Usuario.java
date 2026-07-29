package org.hoteldumbria;

import java.io.Serializable;

public class Usuario extends  Persona implements Serializable {

    private static final long serialVersionUID = -3930114772374287385L;
    private String imagen;


    public Usuario(String clave, String imagen) {
        this.setImagen(imagen);
    }

    public Usuario(String nif, String email, String nombre, String apellido_1,
                   String apellido_2, String sexo, String calle, int numero,
                   int piso, String puerta, String escalera, int bloque,
                   String codigo_postal, String tipo_persona, double latitude,
                   double longitude, String clave, String imagen) {
        super(nif, email, nombre, apellido_1, apellido_2, sexo, calle, numero, piso,
                puerta,
                escalera, bloque, codigo_postal, tipo_persona, latitude, longitude);
        this.imagen = imagen;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public String getImagen() {
        if(imagen==null){
            return "imagen.png";
        }
        else{
            return imagen;
        }
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    @Override
    public String getTipo_persona() {
        return super.getTipo_persona();
    }


}
