package org.hoteldumbria;

import java.io.Serializable;
import java.util.Calendar;

public class Reserva implements Serializable {

    private static final long serialVersionUID = 1L;

    private int numero;
    private String email_usuario;
    private Calendar fecha_entrada;
    private Calendar fecha_salida;


    public Reserva() {
        super();
    }

    public Reserva(int numero, String email_usuario, Calendar fecha_entrada, Calendar fecha_salida) {
        super();
        setNumero(numero);
        setEmail_usuario(email_usuario);
        setFecha_entrada(fecha_entrada);
        setFecha_salida(fecha_salida);
    }


    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getEmail_usuario() {
        return email_usuario;
    }

    public void setEmail_usuario(String email_usuario) {
        this.email_usuario = email_usuario;
    }

    public Calendar getFecha_entrada() {
       return fecha_entrada;
    }

    public void setFecha_entrada(Calendar fecha_entrada) {
        this.fecha_entrada = fecha_entrada;
    }

    public Calendar getFecha_salida() {
        return fecha_salida;
    }

    public void setFecha_salida(Calendar fecha_salida) {
        this.fecha_salida = fecha_salida;
    }
}
