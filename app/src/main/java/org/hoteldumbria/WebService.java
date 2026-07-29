package org.hoteldumbria;

/** Catálogo de endpoints configurable durante el build. */
public final class WebService {
    private static final String BASE = BuildConfig.API_BASE_URL.replaceAll("/+$", "");

    private WebService() {}

    public static String getBaseUrl() { return BASE; }
    public static String getLoginUrl() { return BASE + "/auth/login"; }
    public static String getRefreshUrl() { return BASE + "/auth/refresh"; }
    public static String getLogoutUrl() { return BASE + "/auth/logout"; }
    public static String getRegistrationUrl() { return BASE + "/auth/register"; }
    public static String getMeUrl() { return BASE + "/users/me"; }
    public static String getAvatarUrl() { return BASE + "/users/me/avatar"; }
    public static String getAvailabilityUrl() { return BASE + "/rooms/availability"; }
    public static String getReservationsUrl() { return BASE + "/reservations"; }
    public static String getMyReservationsUrl() { return BASE + "/reservations/me"; }

    public static String getURL_Habitaciones() { return getAvailabilityUrl(); }
    public static String getURL_Reserva_Habitaciones() { return getReservationsUrl(); }
    public static String getURL_Usuarios() { return getMeUrl(); }
    public static String getURL_listado_reservas_habitaciones() { return getMyReservationsUrl(); }
    public static String getURL_borrar_perfil() { return getMeUrl(); }
    public static String getURL_Upload() { return getAvatarUrl(); }
    public static String getURL_imagenes_habitaciones() { return BASE + "/rooms/"; }
    public static String getURL_fotos_perfil() { return getAvatarUrl(); }
    public static String getURL_poblaciones_provincias() { return BASE + "/locations/postal-code"; }
    public static String getParametroUsuarioParaUploadFichero() { return "unused"; }
    public static void setURL_Upload(String ignored) {}

    public static final class JSON {
        public static final String STATUS = "status";
        public static final String ERROR = "error";
        public static final String MESSAGE = "message";
        public static final String CODE = "code";
        public static final String FAIL = "fail";
        public static final String SUCCESS = "success";
        public static final String DATA = "data";
        private JSON() {}
    }
}
