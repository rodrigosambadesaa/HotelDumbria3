package org.hoteldumbria;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class ActivityContacto extends Fragment {

    private Button btn_llamar;
    private Button btn_enviar_email;
    private static final String destinatario = "contacto@hotel.local";

    public static String getDestinatario() {
        return destinatario;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.activity_contacto, container, false);

        setBtn_llamar(layout.findViewById(R.id.btn_llamar));
        setBtn_enviar_email(layout.findViewById(R.id.btn_enviar_email));


        getBtn_llamar().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = Uri.parse("tel:+34981764806"); // Teléfono del Hotel Dumbría
                Intent it = new Intent(Intent.ACTION_DIAL, uri);
                startActivity(it);
            }
        });

        getBtn_enviar_email().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String asunto="Contacto - Hotel Dumbría";
                String mensaje="Estimado Equipo de Contacto del Hotel Dumbría:\n\nLes escribo para comunicarles lo siguiente:\n\n\n\nReciban un cordial saludo:\n\n"+((MainActivity)getActivity()).getUsuarioRecibido().getNombre();

                Intent email = new Intent(Intent.ACTION_SEND);
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{getDestinatario()});
                email.putExtra(Intent.EXTRA_SUBJECT, asunto);
                email.putExtra(Intent.EXTRA_TEXT, mensaje);

                //need this to prompts email client only
                email.setType("message/rfc822");

                startActivity(Intent.createChooser(email, "Choose an Email client :"));
            }
        });

        return  layout;


    }

    public Button getBtn_llamar() {
        return btn_llamar;
    }

    public void setBtn_llamar(Button btn_llamar) {
        this.btn_llamar = btn_llamar;
    }

    public Button getBtn_enviar_email() {
        return btn_enviar_email;
    }

    public void setBtn_enviar_email(Button btn_enviar_email) {
        this.btn_enviar_email = btn_enviar_email;
    }
}

