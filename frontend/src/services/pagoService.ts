import { api } from './api'

export interface Pago {
    id:            string
    reservaId:     string
    monto:         number
    metodoPago:    'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA'
    estadoPago:    'PENDIENTE' | 'PAGADO' | 'REEMBOLSADO'
    referencia:    string | null
    fechaPago:     string
    registradoPor: string
}

export interface CuentaReserva {
    reservaId:              string
    habitacionNumero:       string
    huespedNombreCompleto:  string
    estadoReserva:          string
    totalEstancia:          number
    totalConsumo:           number
    totalPagado:            number
    saldoPendiente:         number
    pagadoCompletamente:    boolean
    pagos:                  Pago[]
}

export interface PagoForm {
    monto:       number
    metodoPago:  'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA'
    referencia?: string
}

export const pagoService = {
    registrarPago: (reservaId: string, form: PagoForm) =>
        api.post<Pago>(`/reservas/${reservaId}/pagos`, form).then(r => r.data),

    listarPorReserva: (reservaId: string) =>
        api.get<Pago[]>(`/reservas/${reservaId}/pagos`).then(r => r.data),

    obtenerCuenta: (reservaId: string) =>
        api.get<CuentaReserva>(`/reservas/${reservaId}/cuenta`).then(r => r.data),

    reembolsarPago: (pagoId: string) =>
        api.patch<Pago>(`/pagos/${pagoId}/reembolsar`).then(r => r.data),
}
