# Documentación de Arquitectura y Diseño — TPI Backend

> Documento de defensa para el coloquio. Explica **qué** hace cada parte del
> sistema, **cómo** lo hace y, sobre todo, **por qué** se tomó cada decisión.
> Está pensado para que cualquier integrante del grupo pueda defender cualquier
> parte del sistema.

---

## Índice

1. [Visión general del sistema](#1-visión-general-del-sistema)
2. [Por qué microservicios y por qué *estos*](#2-por-qué-microservicios-y-por-qué-estos)
3. [El API Gateway: el único punto de entrada](#3-el-api-gateway-el-único-punto-de-entrada)
4. [Microservicio de Usuarios (`tpi-users`)](#4-microservicio-de-usuarios-tpi-users)
5. [Microservicio de Cotizaciones (`tpi-market`)](#5-microservicio-de-cotizaciones-tpi-market)
6. [Microservicio de Órdenes (`tpi-orders`) y el motor de emparejamiento](#6-microservicio-de-órdenes-tpi-orders-y-el-motor-de-emparejamiento)
7. [Microservicio de Historial (`tpi-history`)](#7-microservicio-de-historial-tpi-history)
8. [Seguridad: OAuth2 + Keycloak](#8-seguridad-oauth2--keycloak)
9. [Comunicación entre microservicios](#9-comunicación-entre-microservicios)
10. [Transacciones y consistencia](#10-transacciones-y-consistencia)
11. [Base de datos](#11-base-de-datos)
12. [DTOs vs Entities](#12-dtos-vs-entities)
13. [Docker y despliegue](#13-docker-y-despliegue)
14. [Glosario para el coloquio](#14-glosario-para-el-coloquio)
15. [Preguntas difíciles y cómo responderlas](#15-preguntas-difíciles-y-cómo-responderlas)

---

## 1. Visión general del sistema

El sistema es un backend que simula un **mercado de acciones bursátiles**. Los
usuarios pueden:

- Consultar la **cotización** de una acción (ej. NVDA, AAPL).
- **Ingresar dinero** a su cuenta (siempre en Pesos Argentinos, ARS).
- Consultar su **portfolio** (acciones que poseen) y su **saldo**.
- Colocar **órdenes de compra** y **órdenes de venta**.
- El sistema **empareja** automáticamente las órdenes (motor de matching) y, si
  hay coincidencia, ejecuta la operación: mueve el dinero y las acciones.
- Consultar su **historial** de operaciones; el ADMIN puede ver **todas** las
  transacciones del sistema.

Está construido con una **arquitectura de microservicios**: en vez de una sola
aplicación monolítica, el sistema se divide en varios servicios pequeños e
independientes, cada uno con una responsabilidad clara, su propio contenedor
Docker, su propio puerto y (los que persisten datos) su propia base de datos.

```
                          ┌──────────────────┐
   Cliente  ───────────►  │   API Gateway    │  :8080   ← ÚNICO punto de entrada
   (Postman/navegador)    └────────┬─────────┘
                                   │ enruta según la URL
        ┌──────────────┬──────────┼───────────────┬──────────────┐
        ▼              ▼          ▼               ▼              
   ┌─────────┐   ┌──────────┐  ┌──────────┐  ┌───────────┐
   │tpi-users│   │tpi-market│  │tpi-orders│  │tpi-history│
   │  :8081  │   │  :8082   │  │  :8083   │  │  :8084    │
   └────┬────┘   └────┬─────┘  └────┬─────┘  └─────┬─────┘
        │             │             │              │
        ▼             ▼             │              ▼
   [tpi_users]   API externa        │         [tpi_history]
                  (Stooq)           │
                                    ├──► llama a tpi-users (liquidación)
                                    └──► llama a tpi-history (registro)
                                    │
                                    ▼
                               [tpi_orders]

   Seguridad transversal: Keycloak (:8090) emite los tokens OAuth2 (JWT)
   que cada microservicio valida.
```

---

## 2. Por qué microservicios y por qué *estos*

### Por qué microservicios (lo pide la consigna, pero hay razones reales)

La consigna exige microservicios, pero conviene entender las ventajas para
defenderlas:

- **Separación de responsabilidades**: cada servicio hace una sola cosa y la hace
  bien. El de usuarios no sabe nada de cómo se emparejan órdenes, y viceversa.
- **Despliegue y escalado independiente**: si el endpoint de cotizaciones recibe
  mucho tráfico (es público), se puede escalar *solo* ese servicio sin tocar el
  resto.
- **Aislamiento de fallos**: si el servicio de historial se cae, las compras
  siguen funcionando (el registro de historial es "best-effort", ver §10).
- **Equipos independientes**: cada servicio puede evolucionar por separado.

### Cómo decidimos cuántos y cuáles (la consigna nos deja elegir)

La consigna dice textualmente: *"Es su tarea decidir cuántos y cuáles serán los
microservicios"*. Partimos de las **entidades y capacidades del dominio** y
agrupamos por responsabilidad de negocio:

| Capacidad del dominio (de la consigna)              | Microservicio  |
|-----------------------------------------------------|----------------|
| Punto de entrada único + ruteo                      | `tpi-gateway`  |
| Usuarios, cuentas, saldo, portfolios                | `tpi-users`    |
| Cotizaciones (API externa) + conversión de moneda   | `tpi-market`   |
| Órdenes de compra/venta + emparejamiento            | `tpi-orders`   |
| Historial de operaciones                            | `tpi-history`  |

**Criterio de división**: cada microservicio agrupa entidades que **cambian
juntas** y pertenecen al mismo *contexto* de negocio (esto se llama *bounded
context* en Domain-Driven Design). Por eso cuentas y portfolios están juntos en
`tpi-users` (ambos son "lo que tiene el usuario"), y las órdenes y los trades
están juntos en `tpi-orders`.

### ⭐ La decisión más importante: ¿por qué compra y venta NO son servicios separados?

El scaffolding inicial tenía carpetas `tpi-orders-buy` y `tpi-orders-sell`
separadas. **Las unificamos en un solo `tpi-orders`.** Esta es probablemente la
pregunta que más van a hacer en el coloquio. La razón:

> El **motor de emparejamiento** necesita, en un mismo instante, leer las órdenes
> de compra Y las de venta para cruzarlas, y ejecutar el resultado de forma
> **atómica** (o se hace todo, o no se hace nada).

Si compra y venta vivieran en servicios separados, cada vez que llega una compra
habría que:
1. Pedirle al servicio de ventas la lista de ventas compatibles (llamada de red).
2. Reservar/bloquear esas ventas en el otro servicio (llamada de red).
3. Confirmar o revertir según el resultado (más llamadas de red).

Eso es una **transacción distribuida** (patrón *saga* con compensaciones), que es
**complejo, propenso a errores y lento**, y excede lo razonable para este TPI.
Al tener compra, venta y trades en la **misma base de datos** dentro de
`tpi-orders`, el emparejamiento se resuelve con **una transacción local de base
de datos** (`@Transactional`), que es simple y correcta.

**Conclusión defendible**: *"Dividimos por contexto de negocio, no por tipo de
entidad. Compra y venta son dos caras de la misma capacidad —el trading— y el
emparejamiento las acopla fuertemente, así que separarlas habría introducido una
transacción distribuida innecesaria. Las mantuvimos juntas para que el matching
sea atómico y simple."*

---

## 3. El API Gateway: el único punto de entrada

**Archivo clave**: `tpi-gateway/src/main/resources/application.yml`

La consigna exige *"un único punto de entrada al backend"*. Ese rol lo cumple el
**API Gateway**, implementado con **Spring Cloud Gateway**.

### Qué hace

El cliente (Postman, navegador) **nunca** habla directamente con los
microservicios internos. Habla solo con el gateway en el puerto **8080**, y el
gateway **enruta** la petición al microservicio correcto según el path de la URL:

```yaml
routes:
  - id: users
    uri: http://tpi-users:8081
    predicates:
      - Path=/api/v1/users/**     # todo lo que empiece así va a tpi-users
  - id: market
    uri: http://tpi-market:8082
    predicates:
      - Path=/api/v1/quotes/**
  - id: orders
    uri: http://tpi-orders:8083
    predicates:
      - Path=/api/v1/orders/**
  - id: history
    uri: http://tpi-history:8084
    predicates:
      - Path=/api/v1/history/**
```

### Por qué un gateway (ventajas para defender)

- **Un solo punto de acceso**: el cliente no necesita conocer los puertos ni las
  direcciones internas de cada microservicio. Solo conoce `localhost:8080`.
- **Desacople**: si mañana movemos `tpi-users` a otro host o puerto, solo cambia
  la config del gateway; el cliente no se entera.
- **Punto central** para aplicar (a futuro) políticas transversales: rate
  limiting, CORS, logging centralizado, etc.

### Por qué Spring Cloud Gateway y no otra cosa

Es la solución estándar del ecosistema Spring, **reactiva** (no bloqueante, basada
en Netty, soporta mucha concurrencia con pocos recursos) e integra naturalmente
con el resto de Spring. Reenvía automáticamente los headers HTTP —incluido
`Authorization: Bearer <token>`— hacia los microservicios.

### Detalle importante: ¿el gateway valida el token?

**No.** El gateway solo enruta. La **validación del JWT la hace cada
microservicio** (cada uno es un *Resource Server*). Esto es una decisión
consciente: mantiene el gateway simple y hace que cada servicio sea seguro **por
sí mismo** (defensa en profundidad: aunque alguien saltee el gateway y llegue
directo a un servicio, ese servicio igual exige token válido).

---

## 4. Microservicio de Usuarios (`tpi-users`)

**Puerto**: 8081 · **Base de datos**: `tpi_users`

### Responsabilidad

Es el "dueño" de todo lo relacionado con el usuario: sus datos, su **cuenta**
(saldo en ARS) y su **portfolio** (acciones que posee). También ejecuta la
**liquidación** de una operación (el movimiento atómico de dinero y acciones).

### Entidades (modelos JPA)

- **`Usuario`** (`models/Usuario.java`): id, username, email, `keycloakId`.
  - El `keycloakId` vincula al usuario de la base de datos con el usuario de
    Keycloak (el sistema de login). Es lo que conecta "quién se logueó" con "qué
    registro de la base es".
- **`Cuenta`** (`models/Cuenta.java`): id, `saldoArs`, `usuarioId`.
  - **Decisión**: el saldo es un `BigDecimal`, **nunca** un `double`. Con dinero
    jamás se usa `double`/`float` porque tienen errores de redondeo binario
    (0.1 + 0.2 ≠ 0.3). `BigDecimal` es exacto. *Esta es una pregunta típica de
    coloquio.*
  - Relación 1:1 con el usuario (`unique = true` en `usuario_id`).
- **`Portafolio`** (`models/Portafolio.java`): id, simbolo, cantidad, `usuarioId`.
  - Cada fila es una **tenencia**: "el usuario X tiene N acciones de SÍMBOLO".
  - **Decisión**: hay una restricción de unicidad sobre `(usuario_id, simbolo)`
    para que no existan dos filas del mismo símbolo para el mismo usuario; las
    cantidades se suman en la misma fila.

> **Decisión de diseño sobre las relaciones**: en lugar de usar relaciones JPA
> pesadas (`@OneToOne`, `@ManyToOne` con objetos `Usuario` embebidos), guardamos
> directamente `usuarioId` (un `Long`). Esto es deliberado: en microservicios se
> prefiere **referenciar por id**, no por objeto, porque los datos del usuario
> "viven" conceptualmente en su propio agregado. Hace las entidades más livianas
> y evita cargas en cascada inesperadas.

### Endpoints

| Método | Ruta                             | Quién | Qué hace |
|--------|----------------------------------|-------|----------|
| POST   | `/api/v1/users/{id}/deposit`     | USER  | Suma dinero (ARS) al saldo |
| GET    | `/api/v1/users/{id}/portfolio`   | USER  | Devuelve saldo + tenencias |
| GET    | `/api/v1/users/me`               | USER  | Resuelve el usuario desde el token |
| POST   | `/api/v1/users/settlement`       | interno | Liquida una operación (atómico) |

### El método estrella: `liquidar()` (`services/UserService.java`)

Cuando el motor de órdenes empareja una compra con una venta, llama a este
endpoint. El método hace **todo el movimiento de la operación en una sola
transacción** (`@Transactional`):

1. Verifica que el **comprador tenga saldo** suficiente → si no, lanza error
   (HTTP 409) y **nada** se modifica.
2. Verifica que el **vendedor tenga las acciones** → si no, error 409.
3. **Debita** el dinero del comprador y lo **acredita** al vendedor.
4. **Resta** las acciones del portfolio del vendedor.
5. **Suma** las acciones al portfolio del comprador (creando la tenencia si no
   existía).

**Por qué está acá y no en el servicio de órdenes**: el dueño de los datos de
dinero y acciones es `tpi-users`. Solo él puede modificarlos. Concentrar las 5
operaciones en **un único método transaccional de un único servicio** garantiza
**atomicidad**: es imposible que se descuente el dinero pero no se muevan las
acciones (o al revés). Si cualquier paso falla, Hibernate hace **rollback** de
todo. *Esto responde directamente a la Consideración 7 de la consigna
("implementar transacciones cuando coincidan una orden de compra con una de
venta").*

---

## 5. Microservicio de Cotizaciones (`tpi-market`)

**Puerto**: 8082 · **Base de datos**: ninguna (no persiste nada)

### Responsabilidad

Devolver la **cotización** de una acción consultando una **API externa**, y
**convertir** el precio a Pesos Argentinos.

### Por qué no tiene base de datos

Las cotizaciones se piden en tiempo real a una API externa; no tiene sentido
guardarlas (cambiarían constantemente y quedarían desactualizadas). Es un
servicio **stateless** (sin estado). Esto es una decisión válida y defendible:
**no todos los microservicios necesitan base de datos**.

### Consumo de la API externa (`services/MarketService.java`)

Usamos **Stooq** (`https://stooq.com/q/l/...`), que devuelve la cotización en CSV
y **no requiere API key**. La consigna pide explícitamente *"consumir datos desde
otras APIs"*.

```
Stooq devuelve: Symbol,Date,Time,Open,High,Low,Close,Volume
Tomamos la columna "Close" como precio actual (en USD).
```

**Decisión clave — tolerancia a fallos**: si la API externa no responde (timeout,
sin internet, símbolo desconocido), el servicio **no se rompe**: usa una tabla de
**precios de respaldo (mock)** en memoria. Así la demostración del coloquio
**siempre funciona**, aunque la red del aula falle. El campo `fuente` del
resultado indica si el precio vino de la `"API externa (Stooq)"` o del
`"mock de respaldo"`. Esto es parte del 15% extra (diseño resiliente).

### Conversión de monedas (Consigna)

La consigna avisa: *"el saldo está en ARS, algunas acciones cotizan en otra
moneda, se debe convertir"*. Lo resolvemos con una **tabla paramétrica**: un tipo
de cambio USD→ARS configurable (`market.usd-ars-rate` en `application.yml`).

```
precioArs = precioUsd × tipoDeCambio
```

La respuesta incluye **ambos** valores (`precio` en USD y `precioArs` en ARS) y la
moneda original, para que quede transparente la conversión. La consigna permite
explícitamente usar "una tabla paramétrica" para esto.

### Seguridad: este endpoint es PÚBLICO

El Requerimiento de Seguridad 3 dice que *"la consulta de cotizaciones es de
acceso público"*. Por eso, en `config/SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/quotes/**").permitAll()
```

No requiere token. El resto de los endpoints del servicio (si los hubiera)
seguirían requiriendo autenticación.

---

## 6. Microservicio de Órdenes (`tpi-orders`) y el motor de emparejamiento

**Puerto**: 8083 · **Base de datos**: `tpi_orders`

Es el **corazón** del sistema. Aquí vive la lógica más compleja y la que más se va
a preguntar en el coloquio.

### Entidades

- **`OrdenCompra`**: quién compra, qué símbolo, cuántas acciones, `precioMaxArs`
  (el máximo que está dispuesto a pagar por acción), `estado`, cuánto se compró.
- **`OrdenVenta`**: quién vende, qué símbolo, cantidad, `cantidadRestante` (lo que
  queda por vender, se va decrementando), `precioMinArs` (el mínimo que acepta),
  `estado`.
- **`Trade`**: el registro de una operación concreta ejecutada (comprador,
  vendedor, símbolo, cantidad, precio unitario, total, fecha). Es la "prueba" de
  que la compraventa ocurrió.
- **`EstadoOrden`** (enum): `ABIERTA`, `PARCIAL`, `COMPLETADA`, `RECHAZADA`.

**Decisión — `cantidadRestante` en la venta**: la consigna (Especificación 3) dice
que si entra una compra por 40 y hay una venta por 50, se ejecutan 40 y la venta
debe quedar reflejando que aún puede vender 10. Por eso la orden de venta lleva
`cantidadRestante` separado de `cantidad` (la original). Lo mismo permite los
**fills parciales**.

### El motor de emparejamiento: `procesarCompra()` paso a paso

Está en `services/OrderService.java`. Cuando llega una orden de compra:

```
1. Se guarda la orden de compra (estado provisional).

2. Se buscan las órdenes de venta COMPATIBLES:
   - mismo símbolo
   - estado ABIERTA (todavía con acciones)
   - precioMinArs <= precioMaxArs del comprador   ← la regla de oro del matching
   - ordenadas por precio ascendente (la más barata primero, mejor para el
     comprador) y, a igual precio, por antigüedad (la más vieja primero).
     Esto se llama prioridad PRECIO-TIEMPO, el criterio real de las bolsas.

3. Se recorren esas ventas, comprando de a una hasta satisfacer la cantidad
   pedida o quedarse sin ventas compatibles. Por cada venta:
   - cantidadFill = mínimo(lo que falta comprar, lo que le queda a esa venta)
   - precioEjecución = precioMinArs de la venta (precio de la orden "en el libro")
   - totalFill = precioEjecución × cantidadFill
   - Se llama a tpi-users /settlement para LIQUIDAR ese tramo (mover plata y
     acciones de forma atómica).
       · Si la liquidación falla (ej. el comprador se quedó sin saldo), se
         DETIENE el emparejamiento (break) y se conserva lo ya ejecutado.
   - Si tuvo éxito: se actualiza la venta (cantidadRestante, estado), se crea el
     Trade, y se registra la operación en el historial.

4. Según cuánto se compró, la orden de compra queda:
   - RECHAZADA  → no se compró nada (no había contraparte compatible)
   - COMPLETADA → se compró todo lo pedido
   - PARCIAL    → se compró una parte
```

### Decisiones de diseño del motor (defensa)

- **Por qué el precio de ejecución es el de la VENTA y no el de la compra**: el
  comprador puso un **máximo** que acepta pagar; el vendedor puso un **mínimo**.
  Si el vendedor acepta $145.000 y el comprador acepta hasta $150.000, la
  operación se cierra al precio del que ya estaba en el mercado (la venta):
  $145.000. El comprador se beneficia (paga menos que su máximo). Es el
  comportamiento estándar de un *order book*.
- **Por qué resultado inmediato**: la consigna (Especificación 1) dice que la
  orden de compra *"se acepta o rechaza inmediatamente"*. Por eso `procesarCompra`
  responde en la misma llamada HTTP con el resultado completo (no hay
  procesamiento en background).
- **No se puede comprar lo que no está a la venta** (Especificación 2): si no hay
  ventas compatibles, la lista queda vacía y la orden se marca `RECHAZADA`. Se
  respetan cantidades y límites de precio por la condición
  `precioMinArs <= precioMaxArs`.
- **No auto-emparejamiento**: se saltea cualquier venta del **mismo usuario** que
  compra (no tendría sentido comprarse a uno mismo).
- **`@Transactional` en `procesarCompra`**: garantiza que todos los cambios en la
  base de `tpi-orders` (órdenes y trades) se confirmen juntos.

### Las órdenes de venta de ejemplo (`data.sql`)

La consigna recomienda *"generen portfolios que ya tengan acciones para poder
realizar compras y ventas"*. Por eso sembramos:
- En `tpi-users`: Bruno (id 3) ya posee NVDA y AAPL.
- En `tpi-orders`: Bruno ya tiene **órdenes de venta abiertas** de esas acciones.

Así, apenas arranca el sistema, Ana (id 2) puede comprarle a Bruno y el
emparejamiento funciona en la demo sin pasos previos.

---

## 7. Microservicio de Historial (`tpi-history`)

**Puerto**: 8084 · **Base de datos**: `tpi_history`

### Responsabilidad

Llevar el registro de **todas las operaciones** que ocurren, para poder consultar:
- El **historial de un usuario** (Requerimiento Funcional 5).
- El **historial global de transacciones**, solo para el ADMIN (RF 6).

### Cómo se llena

`tpi-orders` le **envía** a `tpi-history` un registro por cada cosa que pasa:
- `ORDEN_COMPRA` cuando se procesa una compra (con su estado final).
- `ORDEN_VENTA` cuando se crea una venta.
- `TRADE` por cada operación efectivamente ejecutada (con comprador y vendedor).

### Entidad `Operacion`

Guarda: tipo, `usuarioId`, `contraparteId` (la otra parte en un trade), símbolo,
cantidad, precio, total, estado, fecha.

**Decisión clave — el campo `contraparteId`**: cuando Ana le compra a Bruno, se
guarda **un** registro `TRADE` con `usuarioId=Ana` y `contraparteId=Bruno`. Para
mostrar el historial de un usuario, buscamos las operaciones donde él aparece como
**titular O como contraparte**:

```java
findByUsuarioIdOrContraparteIdOrderByFechaDesc(id, id)
```

Así, ese mismo trade aparece tanto en el historial de Ana (como compradora) como
en el de Bruno (como vendedor), **sin duplicar registros**. Elegante y eficiente.

### Seguridad del historial global

El RF 6 y el Requerimiento de Seguridad 2 piden que **solo el ADMIN** vea todas
las transacciones. En `config/SecurityConfig.java`:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/history/transactions").hasRole("ADMIN")
```

Un usuario normal que intente entrar recibe **403 Forbidden**.

---

## 8. Seguridad: OAuth2 + Keycloak

La consigna exige autenticación y autorización con **OAuth2** usando **Keycloak**
como **IDP** (Identity Provider).

### Conceptos (para el coloquio)

- **OAuth2**: un estándar de autorización. La idea central: el usuario se
  autentica **una vez** contra un servidor de identidad (Keycloak) y recibe un
  **token** (un JWT). Después presenta ese token en cada petición; los servicios
  **no manejan contraseñas**, solo validan el token.
- **Keycloak (IDP)**: el "portero" del sistema. Guarda los usuarios, sus
  contraseñas y sus **roles**, y emite los tokens. Lo levantamos como un
  contenedor más.
- **JWT (JSON Web Token)**: un token firmado digitalmente que contiene los datos
  del usuario (username, roles, expiración). Al estar **firmado** por Keycloak,
  cualquier servicio puede verificar que es auténtico sin preguntarle a Keycloak.
- **Realm**: un "reino" o espacio aislado dentro de Keycloak con sus propios
  usuarios, roles y clientes. El nuestro se llama `tpi`.

### Cómo lo configuramos

El realm se **importa automáticamente** al arrancar Keycloak, desde
`infra/keycloak/tpi-realm.json`. Define:
- **Roles**: `USER` y `ADMIN`.
- **Usuarios de prueba**: `admin/admin` (ADMIN+USER), `ana/ana` (USER),
  `bruno/bruno` (USER).
- **Cliente** `tpi-client`: público, con *Direct Access Grants* habilitado (para
  poder pedir tokens con usuario+contraseña desde Postman).

### Cómo valida cada microservicio (Resource Server)

Cada microservicio incluye `spring-boot-starter-oauth2-resource-server` y se
configura como **Resource Server**. La validación es así:

1. El cliente manda `Authorization: Bearer <jwt>`.
2. El servicio **valida la firma** del JWT contra la **clave pública** de Keycloak
   (la obtiene del *JWKS endpoint*: `.../protocol/openid-connect/certs`).
3. Si la firma es válida y no expiró, el usuario está **autenticado**.

**Decisión técnica importante (y pregunta probable)**: configuramos
`jwk-set-uri` (la URL del JWKS) en lugar de `issuer-uri`. ¿Por qué? Porque
Keycloak está expuesto en el host por el puerto **8090** pero internamente los
servicios lo ven como `keycloak:8080`. Si usáramos `issuer-uri`, Spring validaría
que el campo *issuer* del token coincida exactamente con la URL configurada, y
como el token se pide desde `localhost:8090` pero los servicios usan otra URL
interna, esa validación fallaría (es el clásico problema de "issuer mismatch" de
Keycloak en Docker). Validando **solo la firma** vía `jwk-set-uri`, el token
funciona sin importar desde qué host se haya pedido. La firma es la misma porque
la clave privada de Keycloak es una sola.

### De roles de Keycloak a permisos de Spring (`KeycloakRoleConverter`)

Keycloak mete los roles en el token dentro del claim `realm_access.roles`. Spring
Security, por defecto, no sabe leerlos de ahí. Por eso cada servicio tiene un
**`KeycloakRoleConverter`** que toma esos roles y los convierte al formato que
Spring entiende, agregándoles el prefijo `ROLE_`:

```
realm_access.roles = ["ADMIN", "USER"]   →   ROLE_ADMIN, ROLE_USER
```

Así `hasRole("ADMIN")` en la config de seguridad funciona correctamente.

### Por qué cada servicio valida el token (y no solo el gateway)

**Defensa en profundidad**. Si la validación estuviera solo en el gateway, un
atacante que lograra alcanzar un microservicio directamente (saltándose el
gateway) tendría acceso libre. Al validar en cada servicio, **cada uno es seguro
de forma independiente**.

---

## 9. Comunicación entre microservicios

### Cliente → Backend: REST/HTTP a través del gateway

El cliente habla **solo** con el gateway (`:8080`) vía HTTP/REST con JSON.

### Microservicio → Microservicio: REST con `RestClient`

`tpi-orders` necesita hablar con `tpi-users` (para liquidar) y con `tpi-history`
(para registrar). Lo hace con **`RestClient`** (el cliente HTTP moderno de Spring
6, sucesor de `RestTemplate`).

**Decisión — por qué REST síncrono y no mensajería (Kafka/RabbitMQ)**: el
emparejamiento debe dar **respuesta inmediata** (la consigna lo exige). Una compra
necesita saber *ahora* si se liquidó o no, para responderle al usuario. Eso es un
flujo **request/response síncrono**, que encaja con REST. Mensajería asíncrona
sería apropiada si el procesamiento fuera en background, pero la consigna pide lo
contrario.

**Decisión — direcciones por configuración**: las URLs de los otros servicios no
están "hardcodeadas"; vienen de variables (`services.users-uri`, etc.) que en
Docker apuntan a los nombres de los contenedores (`http://tpi-users:8081`). Docker
Compose provee **DNS interno**: el nombre del servicio resuelve a su IP. Esto
desacopla y permite cambiar destinos sin tocar el código.

### Propagación del token (`AuthForwarding`)

Cuando `tpi-orders` llama a `tpi-users/settlement`, ese endpoint **también exige
token**. Por eso `tpi-orders` **reenvía** el mismo token que recibió del usuario:
la clase `AuthForwarding` lee el header `Authorization` de la petición entrante y
lo adjunta a la llamada saliente. Así la identidad del usuario "viaja" a través de
la cadena de servicios.

---

## 10. Transacciones y consistencia

Este es el tema más delicado de los microservicios y conviene tenerlo claro.

### Transacción local (lo que SÍ es 100% atómico)

Dentro de **un** microservicio, `@Transactional` da atomicidad real de base de
datos:
- En `tpi-users.liquidar()`: debitar comprador + acreditar vendedor + mover
  acciones es **todo o nada**. Si falla cualquier paso, rollback completo. Esta es
  la transacción que pide la Consideración 7, y es **sólida**.
- En `tpi-orders.procesarCompra()`: todos los cambios de órdenes y trades se
  confirman juntos.

### Consistencia entre servicios (lo que hay que saber explicar)

El emparejamiento toca **dos** bases de datos (la de órdenes y la de usuarios) a
través de una llamada REST. Eso **no** es una transacción distribuida ACID. Lo
manejamos así:

1. Por cada tramo de la compra, **primero** se llama a la liquidación en
   `tpi-users` (que es atómica de su lado).
2. **Solo si la liquidación tuvo éxito**, `tpi-orders` registra el trade y
   actualiza la orden de venta.
3. Si la liquidación falla, se **detiene** el emparejamiento y se conserva lo ya
   ejecutado (que ya está consistente, porque cada tramo se confirmó de a uno).

**Cómo defenderlo**: *"La transacción crítica —el movimiento de dinero y
acciones— es atómica porque vive entera en un solo servicio. Entre servicios
usamos un orden de operaciones cuidadoso: liquidamos primero y registramos
después, de modo que nunca registramos un trade que no haya movido el dinero. Para
un sistema productivo de mayor escala, el siguiente paso sería un patrón **Saga**
con eventos y compensaciones, pero para el alcance de este TPI esta estrategia es
correcta y mucho más simple."*

### Registro de historial "best-effort"

La llamada a `tpi-history` está envuelta en un `try/catch` que solo loguea si
falla. **Decisión**: el historial es importante pero **no crítico**: si el
servicio de historial está caído, **no** queremos cancelar una compra que ya
movió dinero real. Priorizamos la operación de negocio sobre el registro
secundario. Esto también demuestra **aislamiento de fallos** entre servicios
(parte del 15% extra).

---

## 11. Base de datos

### Una instancia de PostgreSQL, una base de datos por microservicio

- Levantamos **un contenedor** de PostgreSQL.
- Dentro, un script (`infra/postgres/init.sql`) crea **tres bases separadas**:
  `tpi_users`, `tpi_orders`, `tpi_history`.
- Cada microservicio se conecta **solo a la suya**.

**Por qué una base por servicio (principio de microservicios)**: cada servicio es
**dueño exclusivo de sus datos**. Nadie más accede a su base directamente; si
necesitás un dato de otro servicio, se lo pedís por su API. Esto evita acoplar los
servicios a través de la base de datos (que sería una "puerta trasera" que rompe
el aislamiento).

**Por qué una sola instancia física y no tres contenedores Postgres**: la consigna
permite *"una base de datos por microservicio o una para todo"*. Optamos por un
punto intermedio pragmático: **aislamiento lógico** (bases separadas, cada servicio
ve solo la suya) con **menor consumo de recursos** (un solo contenedor de Postgres
en vez de tres). Lo mejor de ambos mundos para un TPI.

### Por qué PostgreSQL y no MySQL

Ambas servirían. Elegimos PostgreSQL porque es muy robusta, estándar en la
industria, con excelente soporte de tipos (incluido `NUMERIC`/`BigDecimal` exacto
para dinero) e integración impecable con Spring Data JPA. La diferencia para este
proyecto es menor, pero PostgreSQL es la opción más "profesional".

### JPA / Hibernate y `ddl-auto: update`

Usamos **Spring Data JPA** con **Hibernate** como implementación. Hibernate es el
**ORM** (Object-Relational Mapping): traduce automáticamente entre objetos Java
(las entidades `@Entity`) y filas de tablas SQL, así no escribimos SQL a mano para
el CRUD.

`spring.jpa.hibernate.ddl-auto=update` hace que Hibernate **cree/actualice las
tablas** automáticamente a partir de las entidades al arrancar. Para un TPI es
cómodo (no hay que crear tablas a mano). *Aclaración para el coloquio*: en
producción se prefiere `validate` + migraciones controladas (Flyway/Liquibase),
pero para este alcance `update` es lo apropiado.

### Datos de prueba (`data.sql`)

Cada servicio que persiste tiene un `data.sql` con datos iniciales (usuarios,
saldos, portfolios, órdenes de venta). Es **idempotente** (`ON CONFLICT DO
NOTHING`) para soportar reinicios sin duplicar, y reajusta las secuencias de IDs
con `setval` para que los IDs autogenerados no choquen con los sembrados.

---

## 12. DTOs vs Entities

La consigna lo pide explícitamente (Consideraciones 2 y 3). Es una distinción
**clave** y muy preguntada.

- **Entity** (`models/`): representa una **tabla** de la base de datos. Lleva
  anotaciones JPA (`@Entity`, `@Id`, `@Column`). Se usa **solo** en la
  comunicación entre el **service y el repository** (la capa de persistencia).
- **DTO** (Data Transfer Object, `dtos/`): un objeto "de viaje", sin lógica ni
  anotaciones de persistencia. Se usa en la comunicación entre el **controller y
  el cliente** (entra como `@RequestBody`, sale como respuesta JSON).

```
Cliente  ⇄  [DTO]  ⇄  Controller  ⇄  Service  ⇄  [Entity]  ⇄  Repository  ⇄  BD
```

**Por qué separarlos (defensa)**:
- **No exponer la estructura interna de la base** al exterior. El cliente no
  necesita (ni debe) ver cómo está modelada la tabla.
- **Seguridad**: evita exponer campos sensibles o internos (ej. el `keycloakId`)
  sin querer.
- **Flexibilidad**: el DTO puede combinar datos de varias entidades (ej.
  `PortfolioDTO` junta datos de `Usuario`, `Cuenta` y `Portafolio` en una sola
  respuesta cómoda) o tener una forma distinta a la tabla.
- **Desacople**: podés cambiar la base de datos sin romper el contrato de la API.

Ejemplos en el código: `PortfolioDTO`, `DepositRequest`, `NuevaOrdenCompraDTO`,
`ResultadoCompraDTO`. Muchos están hechos como `record` de Java (inmutables y
concisos, ideales para DTOs).

---

## 13. Docker y despliegue

### Un contenedor por microservicio (Consideración 6)

Cada microservicio tiene su propio **`Dockerfile`** y se levanta como un
contenedor independiente. Todo se orquesta con **`docker-compose.yml`**, que
define los 7 contenedores: Postgres, Keycloak y los 5 servicios.

### Dockerfile multi-stage (decisión de diseño)

Cada `Dockerfile` tiene **dos etapas**:

```dockerfile
# Etapa 1 (build): imagen con Maven+JDK, compila el código y genera el .jar
FROM maven:3.9-eclipse-temurin-17 AS build
...
RUN mvn clean package -DskipTests

# Etapa 2 (run): imagen liviana solo con Java, copia el .jar y lo ejecuta
FROM eclipse-temurin:17-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Por qué multi-stage**: la imagen final **no incluye Maven ni el código fuente**,
solo el `.jar` y un JRE. Resulta mucho **más liviana y segura**. No necesitás
tener Java ni Maven instalados en tu PC: Docker compila todo adentro.

### Orden de arranque (`depends_on` + `healthcheck`)

Los servicios dependen de Postgres y Keycloak. Usamos `healthcheck` en Postgres
(`pg_isready`) y `depends_on` con `condition: service_healthy` para que los
microservicios **esperen** a que la base esté lista antes de arrancar. Evita
errores de conexión en el arranque.

### Puertos

| Contenedor   | Puerto interno | Puerto en tu PC |
|--------------|----------------|-----------------|
| tpi-gateway  | 8080           | **8080**        |
| tpi-users    | 8081           | (no expuesto)   |
| tpi-market   | 8082           | (no expuesto)   |
| tpi-orders   | 8083           | (no expuesto)   |
| tpi-history  | 8084           | (no expuesto)   |
| keycloak     | 8080           | **8090**        |
| postgres     | 5432           | 5432            |

**Decisión**: los microservicios internos **no exponen** sus puertos al host. Solo
se llega a ellos **a través del gateway**. Esto refuerza el "único punto de
entrada". Keycloak se expone en 8090 (no 8080) para no chocar con el gateway.

---

## 14. Glosario para el coloquio

Términos que aparecen en sus apuntes ("Dudas") y que pueden preguntar:

- **Spring Framework vs Spring Boot**: Spring es un framework grande y modular para
  Java; configurarlo "a mano" es tedioso. **Spring Boot** es una capa encima que
  hace **autoconfiguración** y trae un **servidor embebido**, para arrancar una
  app con configuración mínima ("convención sobre configuración"). En resumen:
  Spring Boot = Spring + autoconfiguración + servidor embebido + *starters*.
- **Inyección de dependencias (DI)**: en vez de que una clase **cree** sus
  dependencias (`new UserService()`), Spring las **provee** automáticamente (las
  "inyecta", normalmente por constructor). Beneficios: bajo acoplamiento, fácil de
  testear, código más limpio. Spring gestiona estos objetos como **beans** en su
  *contenedor*.
- **Tomcat embebido**: el servidor web que atiende las peticiones HTTP. "Embebido"
  significa que viene **dentro** del `.jar` de la aplicación; no hay que instalar
  un servidor aparte. Por eso la app se ejecuta con `java -jar`.
- **JDBC**: la API de bajo nivel de Java para hablar con bases de datos SQL. JPA/
  Hibernate la usan **por debajo**; nosotros trabajamos en el nivel alto (JPA) y
  Hibernate traduce a JDBC.
- **JPA vs Hibernate**: **JPA** es la *especificación* (el estándar, las
  interfaces). **Hibernate** es la *implementación* concreta que usamos. JPA es el
  "qué", Hibernate el "cómo".
- **ORM (Object-Relational Mapping)**: técnica que mapea objetos (clases Java) a
  tablas relacionales. Hibernate es un ORM.
- **Swagger / OpenAPI**: **OpenAPI** es un estándar para describir una API REST.
  **Swagger** es el conjunto de herramientas (incluida una UI web) para
  documentarla y probarla. *No lo implementamos en esta versión*, pero podríamos
  agregarlo con `springdoc-openapi` (es candidato al 15% extra).
- **API Gateway**: el punto de entrada único que enruta a los microservicios.
- **IDP (Identity Provider)**: el servicio que gestiona identidades y autenticación
  (Keycloak).
- **JWT**: token firmado con los datos del usuario; permite autenticación sin
  estado (stateless).
- **Resource Server**: una app que protege recursos y valida tokens (cada
  microservicio nuestro lo es).
- **DTO / Entity**: ver §12.
- **Stateless**: sin estado de sesión guardado en el servidor; cada petición trae
  su token. Por eso configuramos `SessionCreationPolicy.STATELESS`.

---

## 15. Preguntas difíciles y cómo responderlas

**P: ¿Por qué no usaron una base de datos compartida, que era más simple?**
R: Romper el aislamiento de datos es el antipatrón más común en microservicios. Si
dos servicios comparten tablas, quedan acoplados: un cambio de esquema rompe a
ambos y se pierde la independencia. Cada servicio dueño de sus datos es la regla de
oro. Usamos una sola instancia física por economía de recursos, pero con bases
lógicamente separadas.

**P: ¿Qué pasa si dos compras quieren la misma venta al mismo tiempo (concurrencia)?**
R: Hoy el emparejamiento corre dentro de una transacción y procesa las ventas de a
una. Para alta concurrencia real, el siguiente paso sería bloqueo optimista
(`@Version` en la orden de venta) o pesimista (`SELECT ... FOR UPDATE`) para evitar
que dos compras vendan las mismas acciones. Lo tenemos identificado como mejora;
para el alcance del TPI, el modelo transaccional actual es correcto.

**P: Si se cae `tpi-users` justo después de liquidar pero antes de que orders
registre el trade, ¿qué pasa?**
R: La liquidación ya está confirmada (el dinero se movió, que es lo importante). El
trade no quedaría registrado en orders. Es una ventana muy pequeña. La solución
productiva sería un patrón Saga con eventos idempotentes o un *outbox*. Para este
alcance, asumimos esa ventana como aceptable y la documentamos.

**P: ¿Por qué el precio de las órdenes está en ARS y no en la moneda de la acción?**
R: Para simplificar el matching, las órdenes se expresan en ARS (el usuario piensa
en pesos, que es su saldo). La capacidad de **conversión de moneda** está
demostrada en `tpi-market`, que devuelve el precio en USD y en ARS. Si quisiéramos
órdenes en moneda extranjera, `tpi-orders` llamaría a `tpi-market` para convertir
antes de comparar; la arquitectura ya lo soporta.

**P: ¿Por qué REST y no gRPC o mensajería entre servicios?**
R: La operación clave (compra) requiere respuesta inmediata → request/response
síncrono → REST encaja perfecto y es lo que vimos en la materia. Mensajería sería
para flujos asíncronos/desacoplados, que no es el caso acá.

**P: ¿El gateway no es un punto único de falla?**
R: Sí, conceptualmente. En producción se corren **varias instancias** del gateway
detrás de un balanceador de carga. Para el TPI, una instancia es suficiente y es
el precio razonable a pagar por tener un único punto de entrada bien definido.

**P: ¿Cómo escala este sistema?**
R: Cada microservicio escala **independientemente** (más instancias del que reciba
más carga; ej. cotizaciones, que es público). Al ser los servicios stateless
(sin sesión), se pueden replicar sin problema detrás del gateway.

**P: ¿Dónde está exactamente la "transacción" que pide la consigna?**
R: En `UserService.liquidar()`, anotado con `@Transactional`. Ahí se debita al
comprador, se acredita al vendedor y se mueven las acciones, todo atómico. Si algo
falla, rollback total. Es el cumplimiento directo de la Consideración 7.
