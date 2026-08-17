# FEATURE — Peso corporal

> CRUD **aislado**: sin dependencias aguas arriba ni aguas abajo. Una medida por día, filtrable por fecha/mes, con gráfico de líneas para ver la evolución.
>
> Nota de consistencia: la métrica "fuerza relativa" (e1RM ÷ peso) que en su día conectaría esta pantalla con Fuerza **queda sin efecto**, porque el flujo de Fuerza soltó carga/reps. Por tanto el peso corporal es hoy **completamente autónomo**.

---

## 1. Propósito y frontera

Registra el peso corporal en el tiempo y lo grafica. No alimenta ninguna otra pantalla. Su valor está en la **lectura de tendencia**, no en el dato suelto.

Regla de frontera: una medida numérica por día + su evolución. Nada de composición corporal, medidas, ni % graso (extensión futura si se quiere).

---

## 2. Modelo de datos

Tabla `body_weight`.

| Campo | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `id` | INTEGER PK | auto | |
| `date_epoch_day` | INTEGER **UNIQUE** | sí | Una medida por día |
| `weight_kg` | REAL | sí | Unidad canónica **kg** (conversión a lb solo en UI si algún día se añade) |
| `note` | TEXT | no | Contexto ("en ayunas", "post-viaje"); no alimenta métrica |

`date_epoch_day` es único → registrar dos veces el mismo día **sobrescribe** (upsert), no duplica.

---

## 3. Campos mostrados

**Fila de histórico**: fecha, peso, delta respecto a la medida anterior (▲/▼ kg), nota si existe.
**Cabecera / resumen del rango**: peso actual, cambio en el rango, tendencia, ritmo (ver §6).
**Gráfico**: ver §5.

---

## 4. Filtrado y ordenación

| Filtro | Valores | Default |
|---|---|---|
| Rango de fechas | from–to | últimos 30 días |
| Mes | selector de mes → puntos diarios de ese mes | — |
| Presets | 30 d / 3 m / 6 m / 1 año / todo | 30 d |

Orden del histórico: fecha desc. El filtro es un `StateFlow` que se `combine` con la serie → gráfico y lista siempre consistentes con el rango elegido.

---

## 5. Gráfico de líneas (decisión de valor)

El peso corporal fluctúa a diario (agua, comida, sueño); **una línea de puntos crudos es engañosa**. Por eso el gráfico tiene dos capas:

1. **Puntos crudos** — cada medida diaria, tenue.
2. **Media móvil (7 días)** — línea principal, es la que revela la tendencia real.

Modo según el rango:
- **Rango corto** (≤ ~90 días): eje X por día; crudos + media móvil.
- **Rango largo** (> ~90 días o vista anual): eje X por mes; **media mensual** como serie principal (más legible que miles de puntos).

Huecos (días sin medida): la línea **se conecta a través del hueco** (interpolación visual), estándar para tendencia de peso. Con una sola medida en el rango → solo el punto, sin línea. La media móvil requiere un mínimo de puntos para dibujarse.

---

## 6. Métricas derivadas (todas calculadas, no guardadas)

| Métrica | Cálculo | Para qué |
|---|---|---|
| **Peso actual** | última medida | referencia |
| **Cambio en el rango** | último − primero (Δ kg y %) | resultado del periodo |
| **Tendencia** | pendiente de la media móvil → subiendo / bajando / estable | dirección real sin ruido |
| **Ritmo** | Δ kg ÷ semanas del rango (kg/semana) | control de déficit/superávit |
| **Mín / máx del rango** | extremos | dispersión |

---

## 7. CRUD reactivo

Serie observable vía `Flow` de SQLDelight; escritura re-emite y refresca lista + gráfico + resumen sin refresco manual.

- **Create / upsert**: una por día; si ya existe el día, sobrescribe.
- **Read**: serie filtrada por rango, reactiva.
- **Update**: editar el peso o la nota de un día.
- **Delete**: **borrado duro permitido sin restricción.**

Aquí está la diferencia con Actividad y Músculos: como esta entidad **no tiene dependientes** (nadie referencia una medida de peso), borrar es seguro y no rompe ninguna métrica histórica. Por eso **no aplica el "archivar en vez de borrar"** — esa regla existía para proteger histórico referenciado, y aquí no lo hay. El criterio es el mismo de siempre (proteger integridad), y aquí simplemente no hay nada que proteger.

---

## 8. Validaciones y casos borde

- `weight_kg` requerido y > 0 → bloquea guardar.
- Registrar día ya existente → confirma sobrescritura (upsert).
- Rango sin medidas → gráfico vacío con estado "sin datos en este rango".
- Una sola medida → punto sin línea; métricas de tendencia/ritmo se muestran "sin dato suficiente".

---

## 9. Decisiones abiertas (a ratificar)

1. **Ventana de la media móvil**: 7 días (recomendado) vs. configurable.
2. **Umbral rango corto/largo** para cambiar a vista mensual: ~90 días vs. otro.
3. **Confirmación de sobrescritura** al registrar un día ya existente: ¿pedirla o upsert silencioso?
4. **Nota por medida**: ¿la mantenemos o la quitamos para dejarlo aún más mínimo?