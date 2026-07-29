package org.hoteldumbria;

import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AuthenticatedJsonRequest extends JsonObjectRequest {
    public AuthenticatedJsonRequest(int method, String url, JSONObject body,
                                    Response.Listener<JSONObject> listener,
                                    Response.ErrorListener errorListener) {
        super(method, url, body, listener, errorListener);
    }

    @Override
    public Map<String, String> getHeaders() {
        String token = SessionManager.getAccessToken();
        if (token == null || token.isEmpty()) return Collections.emptyMap();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", "application/json");
        return headers;
    }
}
