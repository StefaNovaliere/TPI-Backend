# TPI Backend — Mercado Bursátil (Microservicios)

Backend que permite a los usuarios operar en el mercado de acciones: consultar
cotizaciones, ingresar dinero, colocar órdenes de compra y venta, y que el sistema
**empareje** esas órdenes ejecutando la transacción de forma atómica.

Desarrollado con **Spring Boot 3**, **microservicios**, **Docker Compose**,
**PostgreSQL** y **Keycloak (OAuth2)** como IDP.

> 📘 **Para entender y defender el sistema en detalle (coloquio), leer
> [`DOCUMENTACION.md`](DOCUMENTACION.md)**: explica cada decisión de diseño, por
> qué cada microservicio, el motor de emparejamiento paso a paso, las
> transacciones, la seguridad y un glosario con preguntas frecuentes.

---

## 1. Arquitectura

Único punto de entrada (**API Gateway**) y 4 microservicios de negocio. Cada
microservicio tiene su propio puerto, su contenedor y su propia base de datos.

| Microservicio   | Puerto | Responsabilidad                                                        | Base de datos |
|-----------------|--------|------------------------------------------------------------------------|---------------|
| `tpi-gateway`   | 8080   | Único punto de entrada. Enruta a los microservicios (Spring Cloud Gateway) | —          |
| `tpi-users`     | 8081   | Usuarios, cuentas (saldo ARS), portfolios y **liquidación atómica**     | `tpi_users`   |
| `tpi-market`    | 8082   | **Cotizaciones** desde una API externa + conversión de moneda. Público  | —             |
| `tpi-orders`    | 8083   | Órdenes de compra/venta + **motor de emparejamiento** + transacciones   | `tpi_orders`  |
| `tpi-history`   | 8084   | **Historial** de operaciones (por usuario y global para ADMIN)          | `tpi_history` |

Infraestructura:

| Componente   | Puerto host | Descripción                                            |
|--------------|-------------|--------------------------------------------------------|
| `postgres`   | 5432        | Una instancia, una base de datos por microservicio     |
| `keycloak`   | 8090        | Identity Provider (OAuth2). Realm `tpi` autoimportado  |

```
                         ┌─────────────┐
        Cliente ───────► │ API Gateway │ :8080  (único punto de entrada)
        (Postman)        └──────┬──────┘
                                │
        ┌───────────────┬───────┼───────────────┬───────────────┐
        ▼               ▼       ▼               ▼               
   tpi-users       tpi-market  tpi-orders ───► tpi-users (settlement)
   :8081           :8082       :8083      ───► tpi-history (operaciones)
        │               │           │               │
        ▼               ▼ (API ext) ▼               ▼
   tpi_users        Stooq      tpi_orders       tpi_history
```

> **¿Por qué se consolidaron compra y venta en un solo microservicio (`tpi-orders`)?**
> El motor de emparejamiento necesita ver simultáneamente las órdenes de compra y
> de venta y ejecutar la operación en una transacción. Tenerlas en el mismo servicio
> evita una transacción distribuida innecesaria y mantiene el emparejamiento simple
> y atómico. La consigna delega explícitamente en el grupo la cantidad de microservicios.

---

## 2. Cómo levantar el proyecto

Requisitos: **Docker** y **Docker Compose**.

```bash
docker compose up --build
```

Esto construye y levanta Postgres, Keycloak (importando el realm) y los 5 servicios.
La primera vez tarda unos minutos (compila cada microservicio con Maven dentro de
su imagen). Cuando todo está arriba:

- API Gateway: <http://localhost:8080>
- Keycloak (admin/admin): <http://localhost:8090>

Para bajar todo y borrar los datos:

```bash
docker compose down -v
```

---

## 3. Seguridad (OAuth2 + Keycloak)

- Cada microservicio es un **Resource Server** que valida el JWT contra el JWKS
  (clave pública) de Keycloak.
- El realm `tpi` se importa automáticamente con estos usuarios de prueba:

| Usuario | Contraseña | Rol         |
|---------|------------|-------------|
| `admin` | `admin`    | ADMIN, USER |
| `ana`   | `ana`      | USER        |
| `bruno` | `bruno`    | USER        |

- La consulta de **cotizaciones es pública** (no requiere token).
- El historial **global de transacciones** requiere rol **ADMIN**.

### Obtener un token (password grant)

```bash
curl -X POST http://localhost:8090/realms/tpi/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=tpi-client" \
  -d "username=ana" \
  -d "password=ana"
```

Luego se usa el `access_token` como header: `Authorization: Bearer <token>`.

---

## 4. Endpoints (todos vía el Gateway, http://localhost:8080)

| Método | Ruta                                   | Auth      | Descripción                                  |
|--------|----------------------------------------|-----------|----------------------------------------------|
| GET    | `/api/v1/quotes/{simbolo}`             | Público   | Cotización de una acción (RF 1)              |
| POST   | `/api/v1/users/{id}/deposit`           | USER      | Ingresar dinero (ARS)                        |
| GET    | `/api/v1/users/{id}/portfolio`         | USER      | Portfolio y saldo (RF 2)                     |
| GET    | `/api/v1/users/me`                     | USER      | Datos del usuario autenticado                |
| POST   | `/api/v1/orders/sell`                  | USER      | Registrar orden de venta (RF 4)              |
| POST   | `/api/v1/orders/buy`                   | USER      | Registrar + resolver orden de compra (RF 3)  |
| GET    | `/api/v1/orders/buy/user/{id}`         | USER      | Órdenes de compra de un usuario              |
| GET    | `/api/v1/orders/sell/user/{id}`        | USER      | Órdenes de venta de un usuario               |
| GET    | `/api/v1/history/users/{id}`           | USER      | Historial completo del usuario (RF 5)        |
| GET    | `/api/v1/history/transactions`         | **ADMIN** | Historial global de transacciones (RF 6)     |

---

## 5. Flujo de prueba sugerido (Postman)

Importar `postman/TPI-Backend.postman_collection.json` y ejecutar en orden:

1. **Auth - Login (ana)** → guarda el token automáticamente.
2. **Cotizaciones - Consultar NVDA** → endpoint público.
3. **Usuarios - Ingresar dinero (ana)** → suma saldo en ARS.
4. **Usuarios - Portfolio (ana)** → muestra saldo y tenencias.
5. **Ordenes - Crear orden de COMPRA (ana compra NVDA)** → dispara el
   **motor de emparejamiento** contra las órdenes de venta sembradas de Bruno.
   La respuesta indica si se aceptó/rechazó, cuánto se compró y a qué precio.
6. **Historial - De un usuario (ana)** → muestra sus operaciones.
7. **Auth - Login (admin)** y **Historial - Todas las transacciones** → sólo ADMIN.

Datos sembrados: Bruno (id 3) posee NVDA/AAPL y tiene órdenes de venta abiertas;
Ana (id 2) tiene saldo para comprar. Así el emparejamiento funciona desde el arranque.

---

## 6. Cómo se cumplen las consignas

- **Microservicios + único punto de entrada**: 5 servicios, gateway en 8080.
- **Docker Compose / un contenedor por microservicio**: `docker-compose.yml`.
- **DTOs (servicio ↔ controller) y Entities (servicio ↔ repository)**: ver paquetes
  `dtos/` y `models/` en cada servicio.
- **JPA con Hibernate + PostgreSQL**: `spring-boot-starter-data-jpa`.
- **Consumir API externa**: `tpi-market` consulta Stooq (con fallback mock).
- **Conversión de monedas**: `tpi-market` convierte USD→ARS (tipo de cambio paramétrico).
- **Motor de emparejamiento**: `OrderService.procesarCompra` (respeta cantidades y
  límites de precio, soporta fills parciales — RF/Especificaciones del motor).
- **Transacciones al emparejar**: la liquidación (dinero + acciones) se ejecuta
  atómicamente en `UserService.liquidar` con `@Transactional`.
- **OAuth2 con Keycloak + usuario ADMIN**: realm `tpi`, resource servers, rol ADMIN.
- **Historial por usuario y global**: `tpi-history`.

### Apartado extra (15%)
- **Logging** estructurado en los servicios.
- Diseño tolerante a fallos en la API externa (fallback de cotizaciones).
- El registro de historial es *best-effort* (no bloquea la operación si falla).
