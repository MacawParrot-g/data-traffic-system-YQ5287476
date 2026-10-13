import { ref, onUnmounted } from 'vue'

export function useTimer() {
    const timerSeconds = ref(60)
    const timerCountdown = ref(0)
    const timerMsg = ref('')
    let timerInterval = null

    function startTimer(queryFn) {
        if (!timerSeconds.value || timerSeconds.value < 1) return
        timerCountdown.value = timerSeconds.value
        timerMsg.value = '将在 ' + timerCountdown.value + ' 秒后自动查询事件'
        timerInterval = setInterval(() => {
            timerCountdown.value--
            if (timerCountdown.value <= 0) {
                clearInterval(timerInterval)
                timerInterval = null
                timerCountdown.value = 0
                timerMsg.value = '定时已到，正在自动查询事件...'
                queryFn().then(() => {
                    timerMsg.value = '✅ 定时查询已完成'
                    setTimeout(() => { timerMsg.value = '计时结束后内容会在这里显示' }, 5000)
                })
            }
        }, 1000)
    }

    function cancelTimer() {
        if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
        timerCountdown.value = 0
        timerMsg.value = ''
    }

    onUnmounted(() => cancelTimer())

    return { timerSeconds, timerCountdown, timerMsg, startTimer, cancelTimer }
}