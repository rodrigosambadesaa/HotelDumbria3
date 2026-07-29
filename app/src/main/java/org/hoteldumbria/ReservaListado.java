package org.hoteldumbria;

import java.io.Serializable;
import java.util.Date;

public class ReservaListado implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numero;
    private String nif;
    private Date fecha_entrada;
    private Date fecha_salida;

    public ReservaListado() {
        super();
    }

    public ReservaListado(int numero, String nif, Date fecha_entrada, Date fecha_salida) {
        super();
        this.setNumero(numero);
        this.setNif(nif);
        this.setFecha_entrada(fecha_entrada);
        this.setFecha_salida(fecha_salida);
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public Date getFecha_entrada() {
        return fecha_entrada;
    }

    public void setFecha_entrada(Date fecha_entrada) {
        this.fecha_entrada = fecha_entrada;
    }

    public Date getFecha_salida() {
        return fecha_salida;
    }

    public void setFecha_salida(Date fecha_salida) {
        this.fecha_salida = fecha_salida;
    }
}
