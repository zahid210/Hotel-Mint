import { esSesionExpirada } from '@/lib/esErrorSesion'
import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { habitacionService, Habitacion } from '@/services/habitacionService'
import { reservaService, Reserva } from '@/services/reservaService'
import { getServerTime } from '@/services/api'

export interface HabitacionConReserva extends Habitacion {
    reservaActiva: Reserva | null
    excedida:      boolean   // true si fechaSalida < HOY y estado CHECKIN
}

// ── Mapa de transiciones reserva → habitación ────────────
const RESERVA_A_HAB: Partial<Record<string, Habitacion['estado']>> = {
    CHECKIN:   'OCUPADA',
    CHECKOUT:  'LIMPIEZA',
}

export function useDashboard(refreshSignal = 0) {
    const [habitaciones, setHabitaciones] = useState<Habitacion[]>([])
    const [reservas,     setReservas]     = useState<Reserva[]>([])
    const [cargando,     setCargando]     = useState(true)
    const [error,        setError]        = useState<string | null>(null)
    const [procesando,   setProcesando]   = useState<string | null>(null)

    // ── Fecha del servidor — fuente de verdad para "hoy" ─────
    // Inicializa con la fecha local como fallback mientras carga
    const [HOY, setHOY] = useState<string>(
        () => new Date().toISOString().split('T')[0]
    )

    // Contador de peticiones: descarta respuestas obsoletas si se disparan
    // dos cargas solapadas (botón ↻ + refreshSignal), como en ReportesPage.
    const secuencia = useRef(0)

    // ── Carga paralela: habitaciones + reservas + tiempo ─────
    const cargar = useCallback(async () => {
        const id = ++secuencia.current
        try {
            setCargando(true)
            setError(null)
            const [habs, revs, tiempo] = await Promise.all([
                habitacionService.listarTodas(),
                reservaService.listarTodas(),
                getServerTime(),
            ])
            if (id !== secuencia.current) return
            setHabitaciones(habs)
            setReservas(revs)
            setHOY(tiempo.fecha)
        } catch (e: unknown) {
            // Si es sesión expirada, no mostrar error:
            // PrivateRoute ya reacciona al logout() del interceptor
            if (esSesionExpirada(e)) return
            if (id !== secuencia.current) return
            setError(e instanceof Error ? e.message : 'Error de conexión')
        } finally {
            if (id === secuencia.current) setCargando(false)
        }
    }, [])

    useEffect(() => { void cargar() }, [cargar, refreshSignal])

    // ── Cruce habitación ↔ reserva activa ─────────────────────
    const habitacionesConReserva = useMemo<HabitacionConReserva[]>(() =>
            habitaciones.map(hab => {
                const deEstaHab = reservas.filter(r =>
                    r.habitacionId === hab.id &&
                    (r.estado === 'CHECKIN' || r.estado === 'CONFIRMADA')
                )

                // P1-04: Prioridad 1: Si hay alguna reserva actualmente en CHECKIN, esa es la estancia en curso.
                // Prioridad 2: Si no hay CHECKIN, buscar la CONFIRMADA con fecha de entrada más próxima.
                const enCheckIn = deEstaHab.find(r => r.estado === 'CHECKIN')
                const siguienteConfirmada = deEstaHab
                    .filter(r => r.estado === 'CONFIRMADA')
                    .sort((a, b) => a.fechaEntrada.localeCompare(b.fechaEntrada))[0]

                const reservaActiva = enCheckIn ?? siguienteConfirmada ?? null

                // Excedida = en hotel + fecha de salida ya pasó (comparación de strings ISO)
                // Usa HOY del servidor para evitar errores si la PC tiene hora incorrecta
                const excedida =
                    reservaActiva?.estado === 'CHECKIN' &&
                    !!reservaActiva.fechaSalida &&
                    reservaActiva.fechaSalida < HOY   // "2026-03-15" < "2026-03-16" → true

                return { ...hab, reservaActiva, excedida }
            })
        , [habitaciones, reservas, HOY]) // ← HOY como dependencia

    // ── Stats — todos usan HOY del servidor ───────────────────
    const stats = useMemo(() => ({
        libre:          habitaciones.filter(h => h.estado === 'LIBRE').length,
        ocupada:        habitaciones.filter(h => h.estado === 'OCUPADA').length,
        limpieza:       habitaciones.filter(h => h.estado === 'LIMPIEZA').length,
        entradasHoy:    reservas.filter(r =>
            r.estado === 'CONFIRMADA' && r.fechaEntrada === HOY   // ← HOY del server
        ).length,
        salidasHoy:     reservas.filter(r =>
            r.estado === 'CHECKIN' && r.fechaSalida === HOY       // ← HOY del server
        ).length,
        ocupacion: habitaciones.length
            ? Math.round(
                habitaciones.filter(h => h.estado === 'OCUPADA').length
                / habitaciones.length * 100
            )
            : 0,
        excedidas: habitacionesConReserva.filter(h => h.excedida).length,
    }), [habitaciones, reservas, HOY, habitacionesConReserva])

    // ── Ejecutor genérico ─────────────────────────────────────
    const ejecutar = useCallback(async (
        reservaId: string,
        accion: () => Promise<Reserva>
    ): Promise<Reserva> => {
        setProcesando(reservaId)
        try {
            const actualizada = await accion()
            setReservas(prev =>
                prev.map(r => r.id === reservaId ? actualizada : r)
            )

            // P2-07: Usar el estado real de la habitación devuelto por el backend
            if (actualizada.habitacionEstado) {
                setHabitaciones(prev =>
                    prev.map(h =>
                        h.id === actualizada.habitacionId
                            ? { ...h, estado: actualizada.habitacionEstado! }
                            : h
                    )
                )
            } else {
                const nuevoEstado = RESERVA_A_HAB[actualizada.estado]
                if (nuevoEstado) {
                    setHabitaciones(prev =>
                        prev.map(h =>
                            h.id === actualizada.habitacionId
                                ? { ...h, estado: nuevoEstado }
                                : h
                        )
                    )
                }
            }
            // Sincronizar fuente de verdad
            void cargar()
            return actualizada
        } finally {
            setProcesando(null)
        }
    }, [cargar])

    // ── Acciones de reserva ───────────────────────────────────
    const checkIn  = useCallback(
        (id: string) => ejecutar(id, () => reservaService.checkIn(id)),
        [ejecutar]
    )
    const checkOut = useCallback(
        (id: string) => ejecutar(id, () => reservaService.checkOut(id)),
        [ejecutar]
    )
    const cancelar = useCallback(
        (id: string) => ejecutar(id, () => reservaService.cancelar(id)),
        [ejecutar]
    )

    // ── Marcar habitación en limpieza como libre ──────────────
    const marcarLista = useCallback(async (habitacionId: number): Promise<void> => {
        setProcesando(String(habitacionId))
        try {
            const actualizada = await habitacionService.cambiarEstado(habitacionId, 'LIBRE')
            setHabitaciones(prev =>
                prev.map(h => h.id === habitacionId ? actualizada : h)
            )
        } finally {
            setProcesando(null)
        }
    }, [])

    const onReservaCreada = useCallback(async () => {
        const revs = await reservaService.listarTodas()
        setReservas(revs)
    }, [])

    return {
        habitacionesConReserva,
        stats,
        HOY,                   // ← expuesto para que RoomCard lo use
        cargando, error, procesando,
        cargar,
        checkIn, checkOut, cancelar,
        marcarLista,
        onReservaCreada,
    }
}