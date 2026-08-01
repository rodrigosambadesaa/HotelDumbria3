package org.hoteldumbria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.hoteldumbria.ui.PoblacionesProvincias;
import org.junit.Test;

import java.lang.reflect.Field;

public class ModelPrimitiveTypesTest {

    @Test
    public void numericModelFieldsUsePrimitiveTypes() throws Exception {
        assertPrimitiveField(Persona.class, "numero", int.class);
        assertPrimitiveField(Persona.class, "piso", int.class);
        assertPrimitiveField(Persona.class, "bloque", int.class);
        assertPrimitiveField(Persona.class, "latitude", double.class);
        assertPrimitiveField(Persona.class, "longitude", double.class);

        assertEquals(String.class,
                PoblacionesProvincias.class.getDeclaredField("poblacionid").getType());
        assertPrimitiveField(PoblacionesProvincias.class, "provinciaid", int.class);

        assertPrimitiveField(Habitacion.class, "numero", int.class);
        assertPrimitiveField(Habitacion.class, "area", int.class);
        assertPrimitiveField(Habitacion.class, "num_huespedes", int.class);
        assertPrimitiveField(Reserva.class, "numero", int.class);
        assertPrimitiveField(ReservaListado.class, "numero", int.class);
    }

    @Test
    public void postalCodeIsTextAndPreservesLeadingZeroes() throws Exception {
        Field postalCode = Persona.class.getDeclaredField("codigo_postal");
        assertEquals(String.class, postalCode.getType());

        Persona person = new Persona(
                "00000000T",
                "guest@example.com",
                "Guest",
                "One",
                "Two",
                "other",
                "Main Street",
                1,
                0,
                "",
                "",
                0,
                "01234",
                "usuario",
                0d,
                0d
        );

        assertEquals("01234", person.getCodigo_postal());
    }

    private static void assertPrimitiveField(
            Class<?> owner,
            String fieldName,
            Class<?> expectedType
    ) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        assertTrue(owner.getSimpleName() + "." + fieldName + " must be primitive",
                field.getType().isPrimitive());
        assertEquals(expectedType, field.getType());
    }
}
