package org.hoteldumbria;

import org.json.JSONObject;

public final class ApiMapper {
    private ApiMapper() {}

    public static Usuario userFromProfile(JSONObject profile) {
        Usuario user = new Usuario("", WebService.getAvatarUrl());
        user.setNif(profile.optString("nationalId", ""));
        user.setEmail(profile.optString("email", ""));
        user.setNombre(profile.optString("firstName", ""));
        user.setApellido_1(profile.optString("lastName1", ""));
        user.setApellido_2(profile.optString("lastName2", ""));
        user.setSexo(profile.optString("gender", ""));
        user.setCalle(profile.optString("street", ""));
        user.setNumero(parseInteger(profile.optString("streetNumber", "0")));
        user.setPiso(0);
        user.setPuerta("");
        user.setEscalera("");
        user.setBloque(0);
        user.setCodigo_postal(profile.optString("postalCode", ""));
        user.setTipo_persona("usuario");
        user.setLatitude(0d);
        user.setLongitude(0d);
        return user;
    }

    private static int parseInteger(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

}
