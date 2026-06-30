# Netflix Media Document - Sistema de Base de Datos Políglota

> Una implementación de persistencia políglota basada en el [Modelo de Datos de Línea de Tiempo de Media de Netflix MediaDatabase](https://netflixtechblog.com/netflix-mediadatabase-media-timeline-data-model-4e657e6ffe93)

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Arquitectura](#arquitectura)
- [El Modelo de Media Document](#el-modelo-de-media-document)
- [Filosofía de Diseño de Base de Datos](#filosofía-de-diseño-de-base-de-datos)
- [Implementación en PostgreSQL](#implementación-en-postgresql)
- [Implementación en Cassandra](#implementación-en-cassandra)
- [Implementación en MongoDB](#implementación-en-mongodb)
- [Patrones de Consulta](#patrones-de-consulta)
- [Primeros Pasos](#primeros-pasos)
- [Consultas de Ejemplo](#consultas-de-ejemplo)

---

## Descripción General

Este proyecto implementa el modelo de datos Media Document de Netflix utilizando una arquitectura de **persistencia políglota**. En lugar de forzar todos los datos en una única base de datos, aprovechamos las fortalezas de tres sistemas de bases de datos diferentes:

| Base de Datos | Propósito | Optimizado Para |
|---------------|-----------|-----------------|
| **PostgreSQL** | Datos maestros y gestión de esquemas | Integridad relacional, transacciones ACID |
| **Cassandra** | Almacenamiento de eventos en series temporales | Escrituras de alto rendimiento, consultas temporales |
| **MongoDB** | Almacenamiento de documentos completos | Documentos anidados, consultas espaciales |

### ¿Por qué Persistencia Políglota?

Netflix procesa millones de activos multimedia, cada uno generando grandes cantidades de metadatos. Una única base de datos no puede manejar de manera óptima:

1. **Consultas estructurales** - "¿Qué pistas existen en este documento?"
2. **Consultas temporales** - "¿Qué eventos ocurrieron entre el frame 100 y 500?"
3. **Consultas espaciales** - "¿Dónde en la pantalla aparecieron los rostros?"
4. **Recuperación de documentos** - "Dame el Media Document completo con todos los datos anidados"

Al usar la herramienta correcta para cada trabajo, logramos:
- ✅ Mejor rendimiento de consultas
- ✅ Escalabilidad mejorada
- ✅ Eficiencia de almacenamiento optimizada
- ✅ Complejidad operacional reducida para cargas de trabajo específicas

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                Pipeline de Procesamiento de Media                │
│      (Detección de Rostros, Análisis VMAF, Subtítulos, etc.)    │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Servicio de Ingesta                          │
│                   (Valida y Enruta Datos)                        │
└───────┬─────────────────────┬───────────────────────┬───────────┘
        │                     │                       │
        ▼                     ▼                       ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────────────┐
│  PostgreSQL   │    │   Cassandra   │    │       MongoDB         │
│               │    │               │    │                       │
│ • Documentos  │    │ • Eventos     │    │ • Documentos Completos│
│ • Pistas      │    │ • Series temp.│    │ • Índices Espaciales  │
│ • Componentes │    │ • Particionado│    │ • Agregaciones        │
│ • Esquemas    │    │   por tiempo  │    │                       │
└───────────────┘    └───────────────┘    └───────────────────────┘
        │                     │                       │
        └─────────────────────┼───────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ Enrutador de    │
                    │ Consultas       │
                    │ Dirige consultas│
                    │ a la BD óptima  │
                    └─────────────────┘
```

### Diagramas de Arquitectura

Ver los diagramas PlantUML en la carpeta `/docs`:
- `architecture.puml` - Arquitectura general del sistema y modelo de datos
- `query-patterns.puml` - Patrones de consulta detallados para cada base de datos

---

## El Modelo de Media Document

Basado en el diseño de Netflix, un Media Document tiene una estructura jerárquica:

```
MediaDocument
├── metadata (algoritmo, serie, episodio, etc.)
├── tracks[]
│   ├── id: "0"
│   ├── metadata: {type: "video"}
│   └── components[]
│       ├── id: "0"
│       ├── eventRateNumerator: 24000
│       ├── eventRateDenominator: 1001  (= 23.976 fps)
│       ├── xSize: 1920
│       ├── ySize: 1080
│       └── events[]
│           ├── startTime: 0
│           ├── endTime: 71
│           ├── metadata: {shotEnvironment: "outdoors", character: "Eleven"}
│           └── regions[]
│               ├── xmin: 1152
│               ├── xmax: 1536
│               ├── ymin: 108
│               └── ymax: 648
```

### Conceptos Clave

| Concepto | Descripción |
|----------|-------------|
| **Modelo Temporal** | Los eventos ocupan intervalos de tiempo en una línea temporal (startTime → endTime) |
| **Modelo Espacial** | Los eventos pueden tener regiones espaciales (cajas delimitadoras en el frame) |
| **Pistas (Tracks)** | Agrupan eventos por modalidad de media (video, audio, texto) |
| **Componentes** | Sub-agrupaciones dentro de las pistas (ej., canales de audio izquierdo/derecho) |
| **Esquema de Documento** | JSON Schema para validación (enfoque de esquema en escritura) |

---

## Filosofía de Diseño de Base de Datos

### ¿Por qué PostgreSQL para Datos Maestros?

**PostgreSQL** sobresale en:

| Fortaleza | Aplicación |
|-----------|------------|
| **Transacciones ACID** | Asegurar que los cambios de esquema sean atómicos |
| **Integridad Referencial** | Aplicar relaciones documento → pista → componente |
| **JOINs Complejos** | Consultar a través de toda la jerarquía del documento |
| **Soporte JSONB** | Almacenar metadatos flexibles con indexación |

**Qué almacenamos en PostgreSQL:**
- Metadatos de documentos e información del catálogo
- Definiciones de JSON Schema para validación
- Información estructural de pistas y componentes
- Relaciones de claves foráneas

**Justificación:** Cuando Netflix crea un nuevo tipo de Media Document, el esquema debe ser validado y almacenado de manera confiable. Las garantías transaccionales de PostgreSQL aseguran que las definiciones de esquemas nunca se escriban parcialmente o se corrompan.

---

### ¿Por qué Cassandra para Eventos?

**Cassandra** sobresale en:

| Fortaleza | Aplicación |
|-----------|------------|
| **Rendimiento de Escritura** | Ingestar millones de eventos por segundo |
| **Escalabilidad Lineal** | Agregar nodos para manejar más datos |
| **Consultas Basadas en Partición** | Escaneos eficientes de rangos de tiempo dentro de una partición |
| **Consistencia Ajustable** | Balance entre disponibilidad y consistencia |

**Qué almacenamos en Cassandra:**
- Datos de eventos particionados por (document_id, component_id)
- Índices con buckets temporales para consultas entre documentos
- Datos de eventos desnormalizados para lecturas rápidas

**Diseño de Tabla:**

```sql
-- Clave de Partición: (document_id, component_id)
-- Clave de Clustering: (start_time, event_id)
-- 
-- Este diseño permite:
-- 1. Todos los eventos de un componente se almacenan juntos
-- 2. Los eventos se ordenan por start_time para escaneos de rango eficientes
-- 3. Sin hotspots ya que cada documento/componente tiene su propia partición

CREATE TABLE events_by_document (
    document_id UUID,
    component_id TEXT,
    event_id UUID,
    start_time BIGINT,    -- Columna de clustering (ordenada)
    end_time BIGINT,
    event_type TEXT,
    metadata MAP<TEXT, TEXT>,
    regions LIST<FROZEN<MAP<TEXT, INT>>>,
    PRIMARY KEY ((document_id, component_id), start_time, event_id)
) WITH CLUSTERING ORDER BY (start_time ASC);
```

**Justificación:** Netflix procesa terabytes de media diariamente, generando miles de millones de eventos. La arquitectura basada en particiones de Cassandra permite que los eventos de cada documento se almacenen en diferentes nodos, habilitando procesamiento paralelo y escalabilidad lineal.

---

### ¿Por qué MongoDB para Documentos Completos?

**MongoDB** sobresale en:

| Fortaleza | Aplicación |
|-----------|------------|
| **Documentos Anidados** | Almacenar toda la jerarquía del Media Document en un solo registro |
| **Esquema Flexible** | Los metadatos específicos del dominio varían ampliamente |
| **Pipeline de Agregación** | Análisis y transformaciones complejas |
| **Índices Geoespaciales** | Consultar regiones por coordenadas espaciales |

**Qué almacenamos en MongoDB:**
- Media Documents completos con todos los datos anidados
- Índices 2D para consultas de regiones espaciales
- Metadatos específicos del dominio sin restricciones de esquema

**Estructura del Documento:**

```javascript
{
  _id: UUID("11111111-1111-1111-1111-111111111111"),
  externalId: "NMDB-ST-S4E1-001",
  documentType: "video_face_detection",
  metadata: {
    algorithm: "video_face_detection",
    series: "Stranger Things",
    season: 4,
    episode: 1
  },
  tracks: [{
    id: "0",
    components: [{
      id: "0",
      eventRateNumerator: 24000,
      eventRateDenominator: 1001,
      xSize: 1920,
      ySize: 1080,
      events: [{
        startTime: 0,
        endTime: 71,
        metadata: { character: "Eleven" },
        regions: [{ xmin: 1152, xmax: 1536, ymin: 108, ymax: 648 }]
      }]
    }]
  }]
}
```

**Justificación:** Cuando un consumidor posterior necesita el Media Document completo (ej., una herramienta de control de calidad), MongoDB puede devolver la estructura anidada completa en una sola lectura. Esto evita los costosos JOINs que serían requeridos en PostgreSQL.

---

## Patrones de Consulta

### Cuándo Consultar Cada Base de Datos

```
┌─────────────────────────────────────────────────────────────────┐
│                   Árbol de Decisión de Consultas                 │
└─────────────────────────────────────────────────────────────────┘

¿Es una consulta ESTRUCTURAL?
├── SÍ: "Obtener todas las pistas del documento X"
│   └── → PostgreSQL (JOINs entre tablas)
│
├── ¿Es una consulta TEMPORAL?
│   ├── SÍ: "Obtener eventos entre el frame 100-500"
│   │   └── → Cassandra (escaneo de partición con clustering)
│   │
│   ├── ¿Es una consulta ESPACIAL?
│   │   ├── SÍ: "Encontrar rostros en la mitad derecha de la pantalla"
│   │   │   └── → MongoDB (índice 2D + agregación)
│   │   │
│   │   └── ¿Es una recuperación de DOCUMENTO COMPLETO?
│   │       ├── SÍ: "Obtener el Media Document completo"
│   │       │   └── → MongoDB (lectura de documento único)
│   │       │
│   │       └── ¿Es una consulta ENTRE DOCUMENTOS?
│   │           ├── SÍ: "Todas las detecciones de rostros de hoy"
│   │           │   └── → Cassandra (tabla events_by_time_range)
│   │           │
│   │           └── → Evaluar según requisitos específicos
```

### Consultas de Ejemplo por Base de Datos

#### PostgreSQL - Consultas Estructurales

```sql
-- Obtener jerarquía del documento
SELECT md.title, t.track_type, c.component_id, 
       c.x_size || 'x' || c.y_size as resolution
FROM media_documents md
JOIN tracks t ON t.document_id = md.id
JOIN components c ON c.track_id = t.id
WHERE md.external_id = 'NMDB-ST-S4E1-001';

-- Encontrar documentos por tipo de esquema
SELECT md.title, ds.schema_name
FROM media_documents md
JOIN document_schemas ds ON ds.id = md.schema_id
WHERE ds.schema_name = 'video_face_detection';
```

#### Cassandra - Consultas Temporales

```sql
-- Eventos en rango de tiempo (escaneo de partición eficiente)
SELECT * FROM events_by_document 
WHERE document_id = 11111111-1111-1111-1111-111111111111
  AND component_id = '0'
  AND start_time >= 0 
  AND start_time <= 100;

-- Búsqueda de eventos entre documentos (con buckets temporales)
SELECT * FROM events_by_time_range 
WHERE event_type = 'face_detection' 
  AND time_bucket = '2024-01-15-10';
```

#### MongoDB - Consultas Espaciales y de Documentos

```javascript
// Consulta espacio-temporal: rostros en el centro de la pantalla durante frames 0-200
db.media_documents.aggregate([
  { $match: { documentType: "video_face_detection" }},
  { $unwind: "$tracks" },
  { $unwind: "$tracks.components" },
  { $unwind: "$tracks.components.events" },
  { $match: {
      "tracks.components.events.startTime": { $lte: 200 },
      "tracks.components.events.endTime": { $gte: 0 }
  }},
  { $unwind: "$tracks.components.events.regions" },
  { $match: {
      "tracks.components.events.regions.xmin": { $gte: 400 },
      "tracks.components.events.regions.xmax": { $lte: 1500 }
  }},
  { $project: {
      character: "$tracks.components.events.metadata.character",
      region: "$tracks.components.events.regions"
  }}
]);

// Recuperación de documento completo
db.media_documents.findOne({ externalId: "NMDB-ST-S4E1-001" });
```

---

## Primeros Pasos

### Prerrequisitos

- Docker & Docker Compose
- Java 21+ (para la aplicación Spring Boot)
- Maven 3.9+

### Iniciar las Bases de Datos

```bash
# Iniciar todas las bases de datos
docker compose up -d

# Esperar la inicialización (Cassandra tarda ~60s en estar saludable)
docker compose logs -f cassandra-init

# Verificar que todos los contenedores estén corriendo
docker compose ps
```

### Puertos de Base de Datos

| Base de Datos | Puerto | Cadena de Conexión |
|---------------|--------|-------------------|
| PostgreSQL | 5432 | `postgresql://media_user:media_secure_password@localhost:5432/media_db` |
| Cassandra | 9042 | `cqlsh localhost 9042` |
| MongoDB | 27017 | `mongodb://localhost:27017/media_db?replicaSet=rs0` |

### Conectarse a las Bases de Datos

```bash
# PostgreSQL
docker exec -it postgres_media psql -U media_user -d media_db

# Cassandra
docker exec -it cassandra_media cqlsh

# MongoDB
docker exec -it mongodb_media mongosh media_db
```

---

## Consultas de Ejemplo

### Resumen de Datos Semilla

Los scripts de inicialización crean datos semilla para **"Stranger Things S4E1"** con:

| Tipo de Documento | Descripción | Pistas | Eventos |
|-------------------|-------------|--------|---------|
| `video_face_detection` | Cajas delimitadoras de rostros | 1 (video) | 4 (Eleven, Mike, Dustin, Hopper) |
| `subtitle_ttml` | Subtítulos en inglés | 1 (texto) | 6 eventos de subtítulos |
| `vmaf_quality` | Puntuaciones de calidad de video | 1 (video) | 6 rangos de frames |
| `audio_analysis` | Volumen estéreo | 1 (audio, 2 componentes) | 8 (4 por canal) |

### Prueba Estas Consultas

#### PostgreSQL

```sql
-- Listar todos los documentos
SELECT title, document_type, metadata->>'series' as series 
FROM media_documents;

-- Mostrar estructura del documento
SELECT md.title, t.track_type, 
       c.event_rate_numerator::float / c.event_rate_denominator as fps
FROM media_documents md
JOIN tracks t ON t.document_id = md.id
JOIN components c ON c.track_id = t.id;
```

#### Cassandra

```sql
USE media_timeline;

-- Obtener eventos de detección de rostros
SELECT start_time, end_time, metadata['character'] as character 
FROM events_by_document 
WHERE document_id = 11111111-1111-1111-1111-111111111111 
  AND component_id = '0';

-- Obtener todos los eventos de subtítulos
SELECT * FROM events_by_document 
WHERE document_id = 22222222-2222-2222-2222-222222222222 
  AND component_id = '0';
```

#### MongoDB

```javascript
// Contar documentos por tipo
db.media_documents.aggregate([
  { $group: { _id: "$documentType", count: { $sum: 1 } }}
]);

// Encontrar segmentos de baja calidad (VMAF < 90)
db.media_documents.aggregate([
  { $match: { documentType: "vmaf_quality" }},
  { $unwind: "$tracks.components.events" },
  { $match: { "tracks.components.events.metadata.vmaf": { $lt: 90 } }},
  { $project: {
      startTime: "$tracks.components.events.startTime",
      vmaf: "$tracks.components.events.metadata.vmaf"
  }}
]);
```

---

## Estructura de Archivos

```
src/main/resources/
├── application.yaml              # Configuración de Spring Boot
└── db/
    ├── postgres/
    │   └── init.sql              # Esquema + datos semilla + consultas de ejemplo
    ├── cassandra/
    │   └── init.cql              # Keyspace + tablas + datos semilla
    └── mongodb/
        └── init.js               # Colecciones + índices + datos semilla

docs/
├── architecture.puml             # Diagrama de arquitectura del sistema
├── query-patterns.puml           # Patrones de consulta por base de datos
└── README.md                     # Este archivo
```

---

## Lecturas Adicionales

- [Netflix MediaDatabase - Modelo de Datos de Línea de Tiempo de Media](https://netflixtechblog.com/netflix-mediadatabase-media-timeline-data-model-4e657e6ffe93)
- [Persistencia Políglota - Martin Fowler](https://martinfowler.com/bliki/PolyglotPersistence.html)
- [Modelado de Datos en Cassandra](https://cassandra.apache.org/doc/latest/cassandra/data_modeling/index.html)
- [Pipeline de Agregación de MongoDB](https://www.mongodb.com/docs/manual/core/aggregation-pipeline/)

---

## Licencia

Este proyecto es con fines educativos, demostrando patrones de persistencia políglota basados en publicaciones del blog de ingeniería de Netflix disponibles públicamente.
