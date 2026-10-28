export const formatFecha = (d: string, conDia = false) => {
    const [y, m, day] = d.split('-').map(Number)
    return new Date(y, m - 1, day).toLocaleDateString('es-PE', {
        weekday: conDia ? 'short' : undefined,
        day: '2-digit',
        month: 'short',
        year: 'numeric',
    })
}

export const formatSoles = (n: number | string) =>
    `S/ ${Number(n).toLocaleString('es-PE', { minimumFractionDigits: 2 })}`

export const capitalizar = (s: string) =>
    s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
