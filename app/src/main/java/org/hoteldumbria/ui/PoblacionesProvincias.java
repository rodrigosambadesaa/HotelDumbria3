package org.hoteldumbria.ui;

import java.io.Serializable;

public class PoblacionesProvincias implements Serializable {

    private static final long serialVersionUID = 1L;

    private int poblacionid;
    private String poblacion;
    private int provinciaid;
    private String provincia;

    public int getPoblacionid() {
        return poblacionid;
    }

    public String getPoblacion() {
        return poblacion;
    }

    public int getProvinciaid() {
        return provinciaid;
    }

    public String getProvincia() {
        return provincia;
    }

    public PoblacionesProvincias(int poblacionid, String poblacion, int provinciaid, String provincia) {
        super();
        this.setPoblacionid(poblacionid);
        this.setPoblacion(poblacion);
        this.setProvinciaid(provinciaid);
        this.setProvincia(provincia);
    }

    public void setPoblacionid(int poblacionid) {
        this.poblacionid = poblacionid;
    }

    public void setPoblacion(String poblacion) {
        this.poblacion = poblacion;
    }

    public void setProvinciaid(int provinciaid) {
        this.provinciaid = provinciaid;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }
}
