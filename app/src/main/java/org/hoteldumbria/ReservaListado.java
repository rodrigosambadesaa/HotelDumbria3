package org.hoteldumbria;

import java.io.Serializable;
import java.util.Date;

public class ReservaListado implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numero;
    private String id;
    private String status;
    private String roomName;
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

    public ReservaListado(String id, int numero, String roomName, String status,
                          Date fechaEntrada, Date fechaSalida) {
        this(numero, "", fechaEntrada, fechaSalida);
        this.id = id;
        this.roomName = roomName;
        this.status = status;
    }

    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getRoomName() { return roomName; }
    public void setStatus(String status) { this.status = status; }

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
