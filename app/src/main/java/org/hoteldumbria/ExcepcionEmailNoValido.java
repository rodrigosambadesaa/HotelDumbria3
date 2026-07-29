package org.hoteldumbria;

import android.os.Build;

import androidx.annotation.RequiresApi;

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

    @RequiresApi(api = Build.VERSION_CODES.N)
    public ExcepcionEmailNoValido(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }
}
