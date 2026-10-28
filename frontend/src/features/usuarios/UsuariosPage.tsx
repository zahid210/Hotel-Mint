import { esSesionExpirada } from '@/lib/esErrorSesion'
import { useAuthStore } from '@/store/useAuthStore'
import { useState, useMemo, useEffect, useCallback } from 'react'
import { Modal } from '@/components/Modal'
import { RowAction, FormActions, ErrorBanner, ToolbarActions } from '@/components/ui'
import {
    usuarioService,
    Usuario,
    UsuarioUpdate,
    LABEL_ROL,
} from '@/services/usuarioService'
import { capitalizar } from '@/lib/format'

interface Props {
    onMensaje: (texto: string) => void
}

const ROLES = ['ADMIN', 'RECEPCIONISTA', 'LIMPIEZA'] as const
type RolState = typeof ROLES[number]

type ModalTipo = 'crear' | 'editar' | 'password' | null

const CONST_ROL: Record<RolState, string> = {
    ADMIN: 'bg-brand text-white border-transparent',
    RECEPCIONISTA: 'bg-blue-50 text-blue-700 border-blue-200',
    LIMPIEZA: 'bg-teal-50 text-teal-700 border-teal-200',
}

export function UsuariosPage({ onMensaje }: Props) {
    const miRol = useAuthStore(s => s.rol)

    const [usuarios,  setUsuarios]  = useState<Usuario[]>([])
    const [cargando,  setCargando]  = useState(true)
    const [error,     setError]     = useState<string | null>(null)
    const [busqueda,  setBusqueda]  = useState('')

    const [modalTipo, setModalTipo] = useState<ModalTipo>(null)
    const [seleccion, setSeleccion] = useState<Usuario | null>(null)
    const [form,      setForm]      = useState<{ nombre: string; email: string;
                                                 rol: RolState; activo: boolean;
                                                 password: string }>({
        nombre: '', email: '', rol: 'RECEPCIONISTA', activo: true, password: '',
    })
    const [guardando, setGuardando] = useState(false)
    const [errorForm, setErrorForm] = useState<string | null>(null)

    const cargar = useCallback(async () => {
        try {
            setCargando(true)
            setError(null)
            setUsuarios(await usuarioService.listar())
        } catch (e: unknown) {
            if (esSesionExpirada(e)) return
            setError(e instanceof Error ? e.message : 'Error de conexión')
        } finally {
            setCargando(false)
        }
    }, [])

    useEffect(() => { void cargar() }, [cargar])

    const filtrados = useMemo(() => {
        const q = busqueda.toLowerCase().trim()
        if (!q) return usuarios
        return usuarios.filter(u =>
            u.nombre.toLowerCase().includes(q) ||
            u.email.toLowerCase().includes(q) ||
            u.rol.toLowerCase().includes(q)
        )
    }, [usuarios, busqueda])

    const abrirCrear = () => {
        setSeleccion(null)
        setForm({ nombre: '', email: '', rol: 'RECEPCIONISTA', activo: true, password: '' })
        setErrorForm(null)
        setModalTipo('crear')
    }

    const abrirEditar = (u: Usuario) => {
        setSeleccion(u)
        setForm({ nombre: u.nombre, email: u.email, rol: u.rol, activo: u.activo, password: '' })
        setErrorForm(null)
        setModalTipo('editar')
    }

    const abrirPassword = (u: Usuario) => {
        setSeleccion(u)
        setForm({ nombre: u.nombre, email: u.email, rol: u.rol, activo: u.activo, password: '' })
        setErrorForm(null)
        setModalTipo('password')
    }

    const validarBase = (): string | null => {
        if (!form.nombre.trim()) return 'El nombre es obligatorio'
        if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) return 'Ingresa un email válido'
        return null
    }

    const crear = async () => {
        const err = validarBase()
        if (err) return setErrorForm(err)
        if (form.password.length < 12) return setErrorForm('La contraseña debe tener mínimo 12 caracteres')

        setGuardando(true)
        setErrorForm(null)
        try {
            await usuarioService.crear({
                nombre: form.nombre.trim(),
                email: form.email.trim().toLowerCase(),
                password: form.password,
                rol: form.rol,
            })
            onMensaje('Usuario creado')
            setModalTipo(null)
            await cargar()
        } catch (e: unknown) {
            setErrorForm(e instanceof Error ? e.message : 'Error al crear')
        } finally {
            setGuardando(false)
        }
    }

    const actualizar = async () => {
        if (!seleccion) return
        const err = validarBase()
        if (err) return setErrorForm(err)

        setGuardando(true)
        setErrorForm(null)
        try {
            const dto: UsuarioUpdate = {
                nombre: form.nombre.trim(),
                email: form.email.trim().toLowerCase(),
                rol: form.rol,
                activo: form.activo,
            }
            await usuarioService.actualizar(seleccion.id, dto)
            onMensaje('Usuario actualizado')
            setModalTipo(null)
            await cargar()
        } catch (e: unknown) {
            setErrorForm(e instanceof Error ? e.message : 'Error al actualizar')
        } finally {
            setGuardando(false)
        }
    }

    const resetPassword = async () => {
        if (!seleccion) return
        if (form.password.length < 12) return setErrorForm('La contraseña debe tener mínimo 12 caracteres')

        setGuardando(true)
        setErrorForm(null)
        try {
            await usuarioService.resetPassword(seleccion.id, form.password)
            onMensaje('Contraseña actualizada')
            setModalTipo(null)
        } catch (e: unknown) {
            setErrorForm(e instanceof Error ? e.message : 'Error al actualizar')
        } finally {
            setGuardando(false)
        }
    }

    if (cargando && usuarios.length === 0) return (
        <div className="flex items-center justify-center h-64 text-gray-400 text-[13px] gap-2">
      <span className="w-4 h-4 border-2 border-gray-200 border-t-apple
                       t-spin animate-spin" />
            Cargando usuarios...
        </div>
    )

    return (
        <div className="flex flex-col h-full min-h-0 gap-4">

            {/* ── Controles ─────────────────────────────────── */}
            <div className="flex items-center gap-2.5 flex-wrap flex-shrink-0">
                <input
                    type="text"
                    value={busqueda}
                    onChange={e => setBusqueda(e.target.value)}
                    placeholder="Buscar por nombre, email o rol..."
                    aria-label="Buscar usuarios"
                    autoComplete="off"
                    name="buscar-usuarios"
                    className="t-input flex-1 min-w-40"
                />

                <ToolbarActions
                    onRefresh={() => cargar().then(() => onMensaje('Usuarios actualizados'))}
                    refreshAriaLabel="Actualizar usuarios"
                    crear={{ onClick: abrirCrear, label: '+ Usuario' }}
                />
            </div>

            {error && (
                <ErrorBanner>{error}</ErrorBanner>
            )}

            {/* ── Tabla ─────────────────────────────────────── */}
            <div className="flex-1 min-h-0 overflow-auto t-card">
                {filtrados.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-48 text-gray-400 gap-2">
                        <div className="text-3xl opacity-10">○</div>
                        <div className="text-[13px]">No hay usuarios</div>
                    </div>
                ) : (
                    <table className="w-full border-collapse text-[13px]">
                        <thead className="sticky top-0 z-10">
                        <tr className="bg-fog/80 backdrop-blur-sm border-b border-gray-200/60">
                            {['Nombre', 'Email', 'Rol', 'Estado', '']
                                .map((col, i) => (
                                    <th key={i} className="t-th">{col}</th>
                                ))}
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100/80">
                        {filtrados.map(u => (
                            <tr key={u.id}
                                className="bg-white hover:bg-gray-50/80 transition-colors">
                                <td className="t-td font-medium whitespace-nowrap text-ink">
                                    {u.nombre}
                                </td>
                                <td className="t-td font-mono text-gray-400">
                                    {u.email}
                                </td>
                                <td className="t-td">
                    <span className={`t-badge ${CONST_ROL[u.rol]}`}>
                        {capitalizar(LABEL_ROL[u.rol])}
                    </span>
                                </td>
                                <td className="t-td">
                                    {u.activo ? (
                                        <span className="text-[11px] text-green-600 font-medium flex items-center gap-1.5">
                                            <span className="w-1.5 h-1.5 t-dot bg-green-500" />
                                            Activo
                                        </span>
                                    ) : (
                                        <span className="text-[11px] text-red-500 font-medium flex items-center gap-1.5">
                                            <span className="w-1.5 h-1.5 t-dot bg-red-500" />
                                            Inactivo
                                        </span>
                                    )}
                                </td>
                                <td className="t-td text-right whitespace-nowrap">
                                    <RowAction
                                        onClick={() => abrirEditar(u)}
                                        title="Editar usuario"
                                    >
                                        Editar
                                    </RowAction>
                                    <RowAction
                                        onClick={() => abrirPassword(u)}
                                        title="Cambiar contraseña"
                                    >
                                        Contraseña
                                    </RowAction>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            <div className="text-[11px] text-gray-400 text-right flex-shrink-0">
                {filtrados.length} de {usuarios.length} usuarios
            </div>

            {/* ── Modales ─────────────────────────────────────── */}
            <Modal abierto={modalTipo === 'crear'} titulo="Nuevo usuario"
                   onCerrar={() => setModalTipo(null)}>
                <FormUsuario
                    nombre={form.nombre} email={form.email}
                    password={form.password} rol={form.rol}
                    onChange={setForm}
                    errorForm={errorForm}
                    guardando={guardando}
                    accionTexto="Crear usuario"
                    onGuardar={crear}
                    onCerrar={() => setModalTipo(null)}
                    mostrarPassword
                />
            </Modal>

            <Modal abierto={modalTipo === 'editar'} titulo="Editar usuario"
                   onCerrar={() => setModalTipo(null)}>
                <FormUsuario
                    nombre={form.nombre} email={form.email}
                    password={form.password} rol={form.rol} activo={form.activo}
                    onChange={setForm}
                    errorForm={errorForm}
                    guardando={guardando}
                    accionTexto="Guardar cambios"
                    onGuardar={actualizar}
                    onCerrar={() => setModalTipo(null)}
                    mostrarActivo
                />
            </Modal>

            <Modal abierto={modalTipo === 'password'}
                   titulo={seleccion ? `Contraseña de ${seleccion.nombre}` : 'Cambiar contraseña'}
                   onCerrar={() => setModalTipo(null)}>
                <FormUsuario
                    nombre={seleccion?.nombre ?? ''}
                    email={seleccion?.email ?? ''}
                    password={form.password} rol={seleccion?.rol ?? 'RECEPCIONISTA'}
                    onChange={setForm}
                    errorForm={errorForm}
                    guardando={guardando}
                    soloPassword
                    accionTexto="Guardar contraseña"
                    onGuardar={resetPassword}
                    onCerrar={() => setModalTipo(null)}
                />
            </Modal>

            {miRol !== 'ADMIN' && (
                <div className="text-[11px] text-gray-400 text-right">
                    Solo un administrador puede gestionar usuarios.
                </div>
            )}
        </div>
    )
}

// ── Formulario reutilizable del modal ─────────────────────────
function FormUsuario({
                         nombre, email, password, rol, activo,
                         onChange, errorForm, guardando, accionTexto,
                         onGuardar, onCerrar, mostrarPassword, mostrarActivo, soloPassword,
                     }: {
    nombre: string
    email: string
    password: string
    rol: RolState
    activo?: boolean
    onChange: (f: { nombre: string; email: string; rol: RolState;
                     activo: boolean; password: string }) => void
    errorForm: string | null
    guardando: boolean
    accionTexto: string
    onGuardar: () => void
    onCerrar: () => void
    mostrarPassword?: boolean
    mostrarActivo?: boolean
    soloPassword?: boolean
}) {
    const set = (p: Partial<{ nombre: string; email: string; rol: RolState;
                               activo: boolean; password: string }>) =>
        onChange({ nombre, email, rol, activo: activo ?? true, password, ...p })

    return (
        <div className="space-y-4">
            {!soloPassword && (
                <>
                    <div>
                        <label className="t-label" htmlFor="us-nombre">
                            Nombre completo *
                        </label>
                        <input
                            id="us-nombre"
                            value={nombre}
                            onChange={e => set({ nombre: e.target.value })}
                            autoComplete="off"
                            autoFocus
                            className="t-input"
                        />
                    </div>

                    <div>
                        <label className="t-label" htmlFor="us-email">
                            Email *
                        </label>
                        <input
                            id="us-email"
                            type="email"
                            value={email}
                            onChange={e => set({ email: e.target.value })}
                            autoComplete="off"
                            className="t-input"
                        />
                    </div>

                    <div>
                        <label className="t-label" htmlFor="us-rol">
                            Rol *
                        </label>
                        <select
                            id="us-rol"
                            value={rol}
                            onChange={e => set({ rol: e.target.value as RolState })}
                            className="t-input"
                        >
                            {ROLES.map(r => (
                                <option key={r} value={r}>{LABEL_ROL[r]}</option>
                            ))}
                        </select>
                    </div>
                </>
            )}

            {mostrarActivo && (
                <label className="flex items-center gap-2 text-[13px] text-gray-600 cursor-pointer">
                    <input
                        type="checkbox"
                        checked={activo ?? true}
                        onChange={e => set({ activo: e.target.checked })}
                        className="w-4 h-4 accent-apple"
                    />
                    Usuario activo (puede iniciar sesión)
                </label>
            )}

            {(mostrarPassword || soloPassword) && (
                <div>
                    <label className="t-label" htmlFor="us-pass">
                        Contraseña nueva * (mínimo 6 caracteres)
                    </label>
                    <input
                        id="us-pass"
                        type="password"
                        value={password}
                        onChange={e => set({ password: e.target.value })}
                        autoComplete="new-password"
                        name="nueva-password"
                        autoFocus={soloPassword}
                        className="t-input"
                    />
                </div>
            )}

            {errorForm && (
                <ErrorBanner variante="sm">{errorForm}</ErrorBanner>
            )}

            <FormActions
                onCancelar={onCerrar}
                onGuardar={onGuardar}
                guardando={guardando}
                texto={accionTexto}
                textoGuardando="Guardando..."
                minAncho="min-w-[110px]"
            />
        </div>
    )
}