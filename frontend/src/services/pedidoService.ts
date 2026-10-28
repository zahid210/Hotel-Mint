import { api } from './api'

// ── Tipos espejo de PedidoResponseDTO ─────────────────────────
export interface PedidoItem {
    id:            number
    menuItemId:    number
    nombre:        string
    cantidad:      number
    precioUnitario: number
    subtotal:      number
}

export interface Pedido {
    id:              string
    reservaId:       string
    habitacionId:    number
    habitacionNumero: string
    nombreHuesped:   string
    estado:          'PENDIENTE' | 'ENTREGADO' | 'CANCELADO'
    total:           number
    notas:           string | null
    creadoPor:       string
    createdAt:       string
    items:           PedidoItem[]
}

export interface ItemPedido {
    menuItemId: number
    cantidad:   number
}

export interface PedidoForm {
    reservaId: string
    items:     ItemPedido[]
    notas:     string
}

export const LABEL_ESTADO: Record<Pedido['estado'], string> = {
    PENDIENTE: 'Pendiente',
    ENTREGADO: 'Entregado',
    CANCELADO: 'Cancelado',
}

// ── Servicio ─────────────────────────────────────────────────
export const pedidoService = {
    listar: (estado?: string, reservaId?: string) =>
        api.get<Pedido[]>('/pedidos', { params: { estado, reservaId } })
            .then(r => r.data),

    crear: (data: PedidoForm, idempotencyKey?: string) => {
        const key = idempotencyKey ?? (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : undefined)
        return api.post<Pedido>('/pedidos', data, {
            headers: key ? { 'Idempotency-Key': key } : undefined,
        }).then(r => r.data)
    },

    cambiarEstado: (id: string, estado: string) =>
        api.patch<Pedido>(`/pedidos/${id}/estado`, { estado }).then(r => r.data),
}