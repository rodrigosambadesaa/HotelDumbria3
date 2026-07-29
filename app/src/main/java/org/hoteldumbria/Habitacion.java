package org.hoteldumbria;

import java.io.Serializable;
import java.math.BigDecimal;

public class Habitacion implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numero;
    private BigDecimal precio;
    private int area; // metros cuadrados
    private int num_huespedes;
    private String imagen;
    private static final String RSS_MUY_INTERESANTE="Muy interesante"; 				// Formato fecha item RSS estandard

    public static String getRssMuyInteresante() {
        return RSS_MUY_INTERESANTE;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    public String getImagen() {
        return imagen;
    }

    public Habitacion() {
        super();
    }

    public Habitacion(int numero, BigDecimal precio, int area, int numHuespedes) {
        super();
        this.setNumero(numero);
        this.setPrecio(precio);
        this.setArea(area);
        this.setNum_huespedes(numHuespedes);
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public int getArea() {
        return area;
    }

    public void setArea(int area) {
        this.area = area;
    }

    public int getNum_huespedes() {
        return num_huespedes;
    }

    public void setNum_huespedes(int num_huespedes) {
        this.num_huespedes = num_huespedes;
    }
}
