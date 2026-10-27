import axios from 'axios'
import { useAuthStore } from '@/store/useAuthStore'

// ── Error sentinel para sesión expirada ───────────────────────
// Los componentes lo identifican y no muestran UI de error.
// PrivateRoute toma el control cuando isAuth cambia a false.
export class SesionExpiradaError extends Error {
    constructor() {
        super('Sesión expirada')
        this.name = 'SesionExpiradaError'
    }
}

export const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL ?? '/api/v1',
    timeout: 10000,
    headers: { 'Content-Type': 'application/json' },
})

// ── Inyectar JWT en cada request ─────────────────────────────
api.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
})

// ── Manejo global de errores ─────────────────────────────────
api.interceptors.response.use(
    (res) => res,
    (error) => {
        const url    = error.config?.url ?? ''
        const status = error.response?.status

        // Solo 401 = token inválido/expirado → cerrar sesión.
        // Un 403 (SIN_PERMISOS) NO invalida el token: se propaga como error
        // normal para que el usuario vea el mensaje del backend.
        if (status === 401 && !url.includes('/auth/login')) {
            // 1. Limpia el store — PrivateRoute reacciona y muestra Login
            useAuthStore.getState().logout()

            // 2. Lanza el sentinel — los componentes lo reconocen
            //    y no ejecutan setError() ni setEsError()
            return Promise.reject(new SesionExpiradaError())
        }

        const data = error.response?.data
        const mensaje = (typeof data === 'string' ? data : (data?.mensaje ?? data?.message ?? data?.detail)) ?? 'Error de conexión'
        return Promise.reject(new Error(mensaje))
    }
)

// ── Tiempo del servidor ───────────────────────────────────────
export interface ServerTime {
    fecha:     string
    fechaHora: string
    hora:      number
    minuto:    number
    zona:      string
}

export const getServerTime = (): Promise<ServerTime> =>
    api.get<ServerTime>('/server/time').then(r => r.data)