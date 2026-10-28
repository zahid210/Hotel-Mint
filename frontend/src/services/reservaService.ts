import { api } from './api'

// ── Tipos ────────────────────────────────────────────────────
export interface Reserva {
    id:               string
    habitacionId:     number
    habitacionNumero: string
    habitacionEstado?: 'LIBRE' | 'OCUPADA' | 'MANTENIMIENTO' | 'LIMPIEZA'
    tipoHabitacion:   string
    precioNoche:      number
    nombreHuesped:    string
    apellidoHuesped:  string
    nroDocumento:     string
    fechaEntrada:     string
    fechaSalida:      string
    numHuespedes:     number
    estado:           'PENDIENTE' | 'CONFIRMADA' | 'CHECKIN' | 'CHECKOUT' | 'CANCELADA'
    notas:            string | null
    createdAt:        string

    // ── Nivel 1: horas acordadas ────────────────────────────
    horaEntradaAcordada: string | null   // "15:00:00"
    horaSalidaAcordada:  string | null   // "12:00:00"

    // ── Nivel 1: timestamps reales ──────────────────────────
    checkinReal:  string | null          // "2026-03-16T14:32:00"
    checkoutReal: string | null          // "2026-03-16T11:45:00"

    // ── Nivel 2: late checkout ──────────────────────────────
    horasExtra:      number              // 0 si salió a tiempo
    cargoHorasExtra: number              // 0.00 si sin cargo

    // ── Totales calculados por el backend ───────────────────
    noches:         number
    totalEstancia:  number
    totalConsumo:   number   // pedidos de servicio de habitación
}

export interface NuevaReservaForm {
    habitacionId:        number
    nombreHuesped:       string
    apellidoHuesped:     string
    tipoDocumento:       string
    nroDocumento:        string
    fechaEntrada:        string
    fechaSalida:         string
    horaEntradaAcordada: string    // "15:00"
    horaSalidaAcordada:  string    // "12:00"
    numHuespedes:        number
    notas?:              string
}

// ── Servicio ─────────────────────────────────────────────────
export const reservaService = {
    listarTodas: () =>
        api.get<Reserva[]>('/reservas').then(r => r.data),

    crear: (data: NuevaReservaForm, idempotencyKey?: string) => {
        const key = idempotencyKey ?? (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : undefined)
        return api.post<Reserva>('/reservas', data, {
            headers: key ? { 'Idempotency-Key': key } : undefined,
        }).then(r => r.data)
    },

    checkIn: (id: string) =>
        api.patch<Reserva>(`/reservas/${id}/checkin`).then(r => r.data),

    checkOut: (id: string) =>
        api.patch<Reserva>(`/reservas/${id}/checkout`).then(r => r.data),

    cancelar: (id: string) =>
        api.patch<Reserva>(`/reservas/${id}/cancelar`).then(r => r.data),
}