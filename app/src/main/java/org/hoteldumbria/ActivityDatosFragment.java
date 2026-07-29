package org.hoteldumbria;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;

public class ActivityDatosFragment extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actvivity_datos2);

        Fragment fragment_activity_datos = new ActivityDatos();
        getSupportFragmentManager().beginTransaction().add(R.id.contenedor_fragment_activity_datos,fragment_activity_datos).commit();
    }
}
