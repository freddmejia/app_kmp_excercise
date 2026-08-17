# FEATURE — Gestión de Actividades (CRUD reactivo)

> Entidad de primera clase, editable desde la app. Está aguas arriba de **Registrar entrenamiento** y **Metas**: ambas consumen la lista de actividades. Nada de lo que aquí se defina calcula métricas por sí mismo, pero **condiciona qué métricas son posibles** aguas abajo a través del `measurement_type`.

---

## 1. Propósito y frontera

La pantalla gestiona el catálogo de actividades del usuario: crear, listar, editar, archivar. No registra entrenamientos ni evalúa metas. Su único producto es una **actividad bien tipada** que las otras dos pantallas consumen.

Regla de frontera: una actividad solo declara *identidad* (nombre, icono) y *cómo se mide* (`measurement_type`). No guarda datos de sesión ni de progreso.

---

## 2. Modelo de datos persistido

Tabla `activity`.

| Campo | Tipo | Oblig. | Editable | Descripción |
|---|---|---|---|---|
| `id` | INTEGER PK | auto | no | Identificador |
| `name` | TEXT | sí | sí | Nombre libre ("Músculo", "Running", "Natación") |
| `measurement_type` | TEXT (enum) | sí | condicional* | `STRENGTH` / `DISTANCE_TIME` / `SIMPLE` |
| `icon` | TEXT | no | sí | Clave de icono/emoji para lectura rápida |
| `color` | TEXT | no | sí | Color hex para el histórico |
| `is_archived` | INTEGER (0/1) | sí (def. 0) | vía acción | Marca de archivado |
| `is_seed` | INTEGER (0/1) | sí (def. 0) | no | Vino de la semilla inicial (informativo) |
| `created_at` | INTEGER | auto | no | Epoch millis, para orden estable |

\* **`measurement_type` es inmutable una vez la actividad tiene ≥1 sesión registrada.** Antes de la primera sesión es editable. Ver §6.

### Enum `measurement_type` — el contrato que define el formulario y las métricas

| Valor | Formulario de registro | Métricas que habilita |
|---|---|---|
| `STRENGTH` | ejercicio + series (peso × reps) | volumen, e1RM/progresión, top weight, estancamiento, volumen y frecuencia por grupo muscular |
| `DISTANCE_TIME` | distancia + duración (+ HR opc.) | km semanales, pace, eficiencia aeróbica |
| `SIMPLE` | presencia (solo "lo hice ese día") | frecuencia (días × ventana) — nada numérico |

El nombre es libre; el tipo de medición es **cerrado en 3**. Es lo que permite crear "Natación" como `DISTANCE_TIME` y heredar pace/km sin código nuevo, o "Movilidad" como `SIMPLE` para que solo cuente en metas de frecuencia.

---

## 3. Campos mostrados (por vista)

### 3.1 Lista de actividades (gestión)
- Icono + color
- Nombre
- Badge de `measurement_type` (Fuerza / Distancia-tiempo / Simple)
- Contador de uso *(derivado: nº de sesiones)* — opcional, ver §8
- Indicador de archivada (si el filtro las incluye)

### 3.2 Formulario crear / editar
- Campo nombre (texto)
- Selector de `measurement_type` — **bloqueado con nota explicativa si la actividad ya tiene sesiones**
- Selector de icono/color
- Acción archivar (solo en modo edición)

### 3.3 Picker embebido (en Registro y en Metas)
- Icono + nombre, agrupado o filtrado por tipo según contexto
- **Solo actividades no archivadas**

---

## 4. Filtrado y ordenación

Estado de filtro observable, combinable:

| Filtro | Valores | Default |
|---|---|---|
| Texto | substring sobre `name` (case-insensitive) | vacío |
| Tipo de medición | STRENGTH / DISTANCE_TIME / SIMPLE / todos | todos |
| Estado | activas / archivadas / todas | **activas** |

Ordenación:

| Criterio | Uso |
|---|---|
| Por uso (nº sesiones desc.) | **default en pickers** — sube lo que más usas |
| Alfabético | default en gestión |
| Recientes (`created_at` desc.) | opcional |

**Dinamismo por contexto**: el picker de Registro y el de Metas fuerzan `estado = activas`; la pantalla de gestión permite alternar a archivadas/todas.

---

## 5. Operaciones CRUD (comportamiento reactivo)

Todas las lecturas son `Flow` sobre SQLDelight (`asFlow().mapToList()`); cualquier escritura re-emite y refresca la UII sin refresh manual.

### Create
- Inserta fila con `is_archived = 0`, `created_at = now`.
- Al confirmar: **la nueva actividad aparece de inmediato** en la lista de gestión y en los pickers de Registro y Metas, porque todos observan la misma query.

### Read
- Lista = query filtrada+ordenada, reactiva.
- Pickers = misma fuente, con filtro `activas` aplicado.

### Update
- Edita `name`, `icon`, `color` libremente.
- `measurement_type`: editable **solo** si `COUNT(sesiones) = 0`; si no, campo bloqueado.
- Los cambios de nombre/icono se propagan a histórico y pickers al instante (la sesión referencia por `id`, no por texto → renombrar no rompe registros pasados).

### Archive (sustituye a Delete)
- `is_archived = 1`.
- Desaparece de pickers de creación; **permanece en históricos, metas pasadas y métricas**.
- Reversible (desarchivar).

### Delete (duro) — restringido
- Permitido **solo** si `COUNT(sesiones) = 0` **y** `COUNT(metas que la referencian) = 0`.
- En cualquier otro caso la UI ofrece **Archivar**, no borrar.

---

## 6. Reglas de integridad

1. **`measurement_type` inmutable con historial.** Cambiarlo con sesiones registradas dejaría datos de sesión en un modelo incompatible (p. ej. series de fuerza bajo una actividad ahora `SIMPLE`), corrompiendo volumen/e1RM/pace. Bloqueo duro.
2. **No borrar con historial → archivar.** Un borrado con sesiones asociadas rompería métricas pasadas y metas históricas. Solo se permite borrado duro sin referencias.
3. **Referencia por `id`, nunca por nombre.** Renombrar es seguro; el histórico no se altera.
4. **Unicidad de nombre** entre actividades no archivadas (evita dos "Running" activos). *(A ratificar: unicidad global vs. solo entre activas — ver §11.)*

---

## 7. Reactividad y dinamismo (cómo se propaga un cambio)

Una sola fuente observable por consumidor:

```
activity table ──asFlow──▶ ┌─ Gestión (todas, con filtro de estado)
                           ├─ Picker Registro (solo activas, orden por uso)
                           └─ Picker Metas    (solo activas)
```

Crear/editar/archivar emite una sola vez y **los tres consumidores se actualizan en el mismo frame**. No hay estado duplicado ni refresco manual. El filtro (texto/tipo/estado) es otro `StateFlow` que se `combine` con el de actividades → la lista filtrada es derivada y siempre consistente.

---

## 8. Impacto en métricas (trazabilidad aguas abajo)

| Decisión en esta pantalla | Efecto downstream |
|---|---|
| `measurement_type` elegido | Determina el formulario de Registro y **qué métricas existen** para esa actividad |
| Actividad archivada (no borrada) | Métricas y metas históricas **se conservan** |
| Contador de uso (nº sesiones) | Alimenta el orden "por uso" del picker; es lectura, no dato nuevo |
| Renombrar | Cambia etiquetas en histórico sin recalcular nada |

El contador de uso es la única métrica que roza esta pantalla, y es **derivada** (COUNT sobre sesiones), no un campo persistido — coherente con la frontera de §1.

---

## 9. Validaciones y estados de error

- Nombre vacío → bloquea guardar.
- Nombre duplicado (según regla de unicidad) → error inline.
- `measurement_type` no seleccionado en creación → bloquea guardar.
- Intento de cambiar `measurement_type` con sesiones → campo deshabilitado + nota.
- Intento de borrado duro con referencias → se ofrece Archivar.

---

## 10. Casos borde

- **Archivar una actividad con meta activa**: la meta sigue evaluándose sobre su historial, pero no puedes crear metas nuevas con ella hasta desarchivar. *(A ratificar.)*
- **Desarchivar**: reaparece en pickers; su historial nunca se perdió.
- **Semilla**: "Músculo" (STRENGTH) y "Running" (DISTANCE_TIME) creadas al primer arranque; editables y archivables como cualquier otra.

---

## 11. Decisiones abiertas (a ratificar)

1. **Unicidad de nombre**: ¿solo entre activas (permite reutilizar el nombre de una archivada) o global?
2. **Meta activa sobre actividad archivada**: ¿se sigue evaluando (recomendado) o se pausa?
3. **Contador de uso en la lista de gestión**: ¿lo mostramos o lo dejamos solo para el orden del picker?
4. **Cuarto tipo de medición**: por ahora 3 (`STRENGTH` / `DISTANCE_TIME` / `SIMPLE`). ¿Suficiente?