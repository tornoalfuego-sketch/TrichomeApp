# Roadmap

Estado: **v1.0.0 publicada** (APK debug + release disponibles como GitHub Release).

## Hecho en 1.0.0 (10 fases)

1. Tema/base y motor de apariencia Glassmorphism (4 temas, sliders, acento, font scale).
2. Carpas y plantas (CRUD + reordenamiento + validación de nombres).
3. Protocolos por bloques (etapas ordenadas + fotoperiodo + transiciones).
4. Motor SuperCycle (presets 18/6 · 12/12 · 24/0 · custom).
5. Bitácora con 16 tipos de evento y formularios dinámicos + multi-planta.
6. Recordatorios recurrentes con re-encolado en WorkManager.
7. Calendario mensual con filtros.
8. Biblias de terpenos y breeding + tracker de proyectos/cruces.
9. Gráficas Canvas nativas + gamificación (XP/niveles/racha/logros).
10. Diagnóstico por foto (reglas locales, reporte glassmorphic, registrar en bitácora).

## Post-1.0.0 (propuesta)

### 1.1 — Pulido
- Sincronización opcional (copia de seguridad en la nube o exportación JSON).
- Modo oscuro forzado por sistema como quinta opción de tema.
- Widget de resumen del cultivo en la pantalla de inicio.
- Notificaciones con acciones directas ("registrar riego ahora").

### 1.2 — Diagnóstico avanzado
- Clasificador TFLite entrenado (hojas por categoría) como primera opción, con
  las reglas actuales como respaldo.
- Historial de diagnósticos por planta con evolución temporal.
- Captura guiada (máscaras sobre la hoja) para mejorar la precisión.

### 2.0 — Colaboración y analítica
- Multiusuario local (perfil de cultivador) y retos entre usuarios.
- Exportación de métricas (CSV) y reporte PDF del ciclo completo.
- Integración con sensores (pH/EC/temperatura) vía BLE.

## Principios de evolución

- **Offline-first**: ninguna funcionalidad depende de red.
- **Compatibilidad de datos**: toda futura versión de BD migra explícitamente
  (patrón `MIGRATION_1_2` mantenido).
- **UI en español**, código y commits en inglés.