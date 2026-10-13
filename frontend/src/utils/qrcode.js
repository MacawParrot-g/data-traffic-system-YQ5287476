import QRCode from 'qrcode'

export async function renderQRToCanvas(canvas, url, size = 256) {
    if (!canvas) return
    if (!url || !url.trim()) {
        const ctx = canvas.getContext('2d')
        ctx.clearRect(0, 0, canvas.width, canvas.height)
        return
    }
    try {
        await QRCode.toCanvas(canvas, url, {
            width: size, margin: 2,
            color: { dark: '#000000', light: '#ffffff' }
        })
    } catch (e) {
        console.error('QR码生成失败:', e)
    }
}