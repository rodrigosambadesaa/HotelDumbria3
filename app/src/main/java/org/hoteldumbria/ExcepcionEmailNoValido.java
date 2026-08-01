package org.hoteldumbria;

class ExcepcionEmailNoValido extends Exception {

    private static final long serialVersionUID = 7750244265901838870L;

    public ExcepcionEmailNoValido() {
        super();
    }

    public ExcepcionEmailNoValido(String message) {
        super(message);
    }

    public ExcepcionEmailNoValido(String message, Throwable cause) {
        super(message, cause);
    }

    public ExcepcionEmailNoValido(Throwable cause) {
        super(cause);
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }
}
