# Arquitectura

```text
Android / iOS / Admin web
           |
        REST API
     Fastify + JWT
       /        \
PostgreSQL     SMTP outbox -> proveedor SMTP
```

`persons` es la identidad personal y `users` contiene únicamente credenciales y
estado de autenticación. Las reservas referencian persona y habitación. La
integridad referencial parte de `persons`, por lo que el borrado transaccional
no puede dejar una fila personal huérfana.

El correo de alta se escribe en `email_outbox` dentro de la misma transacción
que crea la cuenta. Un worker lo envía con reintentos; la confirmación consume
un token aleatorio almacenado solo como hash.

Los clientes comprueban primero el estado de red específico de la versión y
luego conectividad real mediante una secuencia de proveedores. La secuencia se
detiene en el primer servidor alcanzable.
