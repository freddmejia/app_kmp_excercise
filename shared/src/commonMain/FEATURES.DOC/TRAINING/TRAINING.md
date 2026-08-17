# FEATURE — Registrar entrenamiento (flujo Fuerza)

> Formulario de las actividades de tipo `STRENGTH`. Captura **qué músculos se trabajaron ese día y cuántas series**, nada más. Sin peso, sin reps, sin catálogo de ejercicios. Consume la lista de **Actividades** (aguas arriba) y la lista de **Músculos** (definida aquí).
>
> Nota de consistencia: este documento describe el flujo `STRENGTH` vigente. La versión "ejercicio + peso × reps" del doc de Actividad queda como **mejora futura**, no como contradicción.

---

## 1. Propósito y frontera

Materializa una sesión de fuerza como un conjunto de **entradas músculo → series**. No calcula métricas: produce el dato crudo (presencia + series efectivas/calentamiento) del que viven Progreso y Metas.

Regla de frontera: la unidad atómica es **la serie**, segmentada en *efectiva* vs. *calentamiento*. No entran carga ni repeticiones.

---

## 2. Modelo de datos

Cuatro tablas. Dos son catálogo editable (jerarquía muscular), dos son la sesión.

### 2.1 `muscle_group` (catálogo, editable)
| Campo | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `name` | TEXT | sí | "Pecho", "Espalda", "Hombro", "Pierna", "Glúteo"… |
| `is_archived` | INTEGER (0/1) | def. 0 | Archivar, nunca borrar con historial |
| `created_at` | INTEGER | auto | |

### 2.2 `muscle` (catálogo, editable, hijo de un grupo)
| Campo | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `group_id` | INTEGER FK | sí | Grupo al que pertenece |
| `name` | TEXT | sí | "Pecho alto", "Pecho bajo", "Dorsal ancho"… |
| `is_archived` | INTEGER (0/1) | def. 0 | |
| `created_at` | INTEGER | auto | |

### 2.3 `workout_session` (cabecera)
| Campo | Tipo | Oblig. | Alimenta |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `activity_id` | INTEGER FK | sí | Segmenta metas por actividad; debe ser una actividad `STRENGTH` |
| `date_epoch_day` | INTEGER | sí | Ventana de metas, frecuencia, racha, gaps |
| `notes` | TEXT | no | Contexto cualitativo |
| `created_at` | INTEGER | auto | Auditoría / orden mismo día |

### 2.4 `session_muscle` (la entrada: un músculo trabajado en la sesión)
| Campo | Tipo | Oblig. | Alimenta |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `session_id` | INTEGER FK | sí | |
| `muscle_id` | INTEGER FK | sí | Presencia → "qué músculo hice / no hice"; enlaza a su grupo |
| `effective_sets` | INTEGER | **sí (≥1)** | **Volumen** por músculo y por grupo |
| `warmup_sets` | INTEGER | no (def. 0) | Ratio calentamiento; no suma al volumen |

**`UNIQUE(session_id, muscle_id)`** → un músculo aparece **una sola vez por sesión**. Si se trabaja en dos momentos del día, las series se **suman** en esa misma fila (no se crean dos entradas del mismo músculo el mismo día).

---

## 3. Granularidad de registro (decisión clave)

En tus ejemplos registras a dos niveles: "pecho alto" (músculo hijo) y "espalda 2 series" (grupo padre). Para soportar **ambos sin ambigüedad de agregación**, la regla es:

- **Siempre se registra contra un `muscle` (hoja).** La sesión nunca apunta a un grupo directamente.
- **Cada grupo trae una hoja genérica** en la semilla, p. ej. `Espalda › General`, `Hombro › General`. Cuando no quieres sub-granularidad, registras contra esa hoja "General" → sigue siendo una hoja, sigue agregando limpio a su grupo.
- **El volumen del grupo = suma de las series efectivas de todas sus hojas** (las específicas + la "General").

Así "pecho alto/bajo" conviven con "espalda (general)" y **todo agrega uniforme al grupo** sin ramas especiales en la métrica.

---

## 4. Flujo de registro (el "check")

1. Elegir **actividad** (`STRENGTH`) y **fecha**.
2. Lista de músculos **agrupada por grupo muscular** (secciones colapsables: Pecho, Espalda, Hombro…).
3. **Check por músculo** = "ese día hice este músculo" → al marcarlo se añade a la sesión y se revelan sus dos contadores:
    - **Series efectivas** (obligatorio, ≥1)
    - **Series de calentamiento** (opcional, def. 0)
4. Desmarcar el check elimina la entrada de la sesión.

El check no es un campo aparte: **es la existencia de la fila `session_muscle`**. Marcar = crear entrada; series efectivas = el dato que se cuenta.

---

## 5. Campos mostrados

**Cabecera**: actividad, fecha.
**Lista de músculos** (picker de registro): secciones por grupo; por músculo → nombre, checkbox, y si está marcado, inputs de efectivas/calentamiento.
**Resumen de sesión** (lectura): por músculo, efectivas + calentamiento; total de series efectivas de la sesión.

---

## 6. Filtrado y ordenación de músculos

| Filtro | Valores | Default |
|---|---|---|
| Texto | substring sobre `name` | vacío |
| Grupo | uno / todos | todos (secciones) |
| Estado | activos / archivados / todos | **activos** |

Ordenación dentro de cada grupo: por uso (nº de sesiones desc.) para subir lo que más entrenas; alfabético como alternativa. En el picker de registro se fuerza `estado = activos`.

---

## 7. CRUD reactivo

Dos CRUD conviven, ambos vía `Flow` de SQLDelight (escritura re-emite, UI sin refresco manual):

**A. Entradas de la sesión** (`session_muscle`): check/uncheck y edición de series actualizan el resumen y los totales al instante.

**B. Catálogo muscular** (`muscle_group` + `muscle`): mismo patrón que Actividad — referencia por `id`, **archivar en vez de borrar**, renombrar seguro (el histórico apunta al `id`). Crear un músculo nuevo aparece de inmediato en su grupo dentro del picker.

Una sola query observable del catálogo alimenta el picker de registro y la pantalla de gestión muscular; un alta se refleja en ambos en el mismo frame.

---

## 8. Reglas de integridad

1. `session_muscle.effective_sets ≥ 1` — sin serie efectiva no hay entrada (equivale a no haber marcado el check).
2. `UNIQUE(session_id, muscle_id)` — series se suman, no se duplican filas.
3. **No borrar** grupo/músculo con historial → archivar. Borrado duro solo sin sesiones que lo referencien.
4. Referencia por `id`, nunca por nombre → renombrar no altera histórico.
5. `workout_session.activity_id` debe ser de tipo `STRENGTH`.

---

## 9. Impacto en métricas (trazabilidad)

| Dato capturado | Métrica que habilita |
|---|---|
| Existencia de `session_muscle` (presencia) | **Qué músculo/grupo entrené y cuál no** (req 2), por ventana |
| `effective_sets` por músculo | **Volumen por músculo** (series/semana) |
| `effective_sets` agregado por grupo | **Volumen por grupo** |
| Días distintos con un músculo/grupo | **Frecuencia** por músculo/grupo |
| `warmup_sets` vs `effective_sets` | **Ratio de calentamiento** (calidad de sesión) |
| Tendencia de series efectivas semana a semana | **Progresión** (req 4) — por volumen, no por carga |
| `date_epoch_day` + `activity_id` | Cumplimiento de metas por ventana, racha, gaps |

**Consecuencia explícita**: al no capturar peso/reps, **no existe progresión por carga** (e1RM, "cuánto levanté", "qué ejercicio mejorar" del planteamiento original). La progresión de fuerza aquí es **por volumen de series**. Decisión válida y coherente con "qué músculo hice, nada más"; la carga se puede reintroducir después sin romper este modelo.

---

## 10. Validaciones y casos borde

- Marcar músculo sin efectivas → bloquea guardar la entrada (o auto-desmarca).
- Actividad no `STRENGTH` → este flujo no aplica (usa el formulario de su tipo).
- Archivar un músculo con historial → sale del picker, su volumen histórico se conserva.
- Grupo sin hojas activas → no se puede registrar contra él hasta tener al menos una hoja (la "General" lo evita por semilla).

---

## 11. Decisiones abiertas (a ratificar)

1. **Hoja "General" por grupo** para registro a nivel de grupo (mi solución al doble nivel de tus ejemplos). ¿Te encaja, o prefieres poder registrar el grupo directamente sin hoja?
2. **Semilla del catálogo muscular**: ¿arrancamos con grupos + hojas típicas (Pecho: alto/bajo/General; Espalda: dorsal/General…) editables, o vacío?
3. **Orden del picker**: por uso (recomendado) vs. alfabético como default.
4. **Confirmar** que este flujo aplica solo a `STRENGTH` (tu punto 4 quedó sin responder).