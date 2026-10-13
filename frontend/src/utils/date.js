export function getTodayStr(separator = '/') {
    const d = new Date()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return d.getFullYear() + separator + mm + separator + dd
}

export function getTodayDashStr() {
    return getTodayStr('-')
}

export function toStorageDate(isoDate) {
    if (!isoDate) return ''
    const parts = isoDate.split('-')
    return parts[0] + '/' + parseInt(parts[1]) + '/' + parseInt(parts[2])
}

export function formatDateForQuery(dateStr) {
    if (!dateStr) return ''
    const parts = dateStr.split('-')
    return parts[0] + '/' + parseInt(parts[1]) + '/' + parseInt(parts[2])
}

export function trimApplyTime(raw) {
    if (!raw) return ''
    const idx = raw.indexOf('.')
    return idx > -1 ? raw.substring(0, idx) : raw
}

export function getPast3DaysDates() {
    const dates = []
    const d = new Date()
    for (let i = 0; i < 3; i++) {
        const t = new Date(d)
        t.setDate(d.getDate() - i)
        dates.push(t.getFullYear() + '/' + (t.getMonth() + 1) + '/' + t.getDate())
    }
    return dates
}

export function generateCurrentTimestamp() {
    const d = new Date()
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}