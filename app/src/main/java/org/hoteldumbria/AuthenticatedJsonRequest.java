package org.hoteldumbria;

import android.content.Context;

import com.android.volley.VolleyError;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AuthenticatedJsonRequest extends JsonObjectRequest {
    private final Context context;
    private final int originalMethod;
    private final String originalUrl;
    private final JSONObject originalBody;
    private final Response.Listener<JSONObject> responseListener;
    private final Response.ErrorListener errorListener;
    private final boolean mayRefresh;

    public AuthenticatedJsonRequest(Context context, int method, String url, JSONObject body,
                                    Response.Listener<JSONObject> listener,
                                    Response.ErrorListener errorListener) {
        this(context, method, url, body, listener, errorListener, true);
    }

    public AuthenticatedJsonRequest(int method, String url, JSONObject body,
                                    Response.Listener<JSONObject> listener,
                                    Response.ErrorListener errorListener) {
        this(null, method, url, body, listener, errorListener, false);
    }

    private AuthenticatedJsonRequest(Context context, int method, String url, JSONObject body,
                                     Response.Listener<JSONObject> listener,
                                     Response.ErrorListener errorListener,
                                     boolean mayRefresh) {
        super(method, url, body, listener, errorListener);
        this.context = context == null ? null : context.getApplicationContext();
        this.originalMethod = method;
        this.originalUrl = url;
        this.originalBody = body;
        this.responseListener = listener;
        this.errorListener = errorListener;
        this.mayRefresh = mayRefresh;
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

    @Override
    public void deliverError(VolleyError error) {
        if (!mayRefresh || context == null || error.networkResponse == null
                || error.networkResponse.statusCode != 401) {
            errorListener.onErrorResponse(error);
            return;
        }
        SessionManager.refreshAccessToken(context, refreshed -> {
            if (!refreshed) {
                errorListener.onErrorResponse(error);
                return;
            }
            AuthenticatedJsonRequest retry = new AuthenticatedJsonRequest(
                    context,
                    originalMethod,
                    originalUrl,
                    originalBody,
                    responseListener,
                    errorListener,
                    false
            );
            retry.setTag(getTag());
            PeticionesRed.getInstancia(context).anhadirPeticionACola(retry);
        });
    }
}
