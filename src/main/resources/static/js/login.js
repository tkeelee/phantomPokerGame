// 登录页面逻辑
document.addEventListener('DOMContentLoaded', function() {
    // 强制清除之前的登录状态
    localStorage.removeItem('playerName');
    
    // 监听登录输入框回车事件
    document.getElementById('playerName').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            login();
        }
    });
});

function login() {
    const playerName = document.getElementById('playerName').value.trim();
    if (!playerName) {
        showError('请输入玩家名称');
        return;
    }

    // 保存玩家名称
    localStorage.setItem('playerName', playerName);
    
    // 跳转到大厅页面
    window.location.href = 'lobby.html';
}

function showError(message) {
    // 使用layer.js显示错误提示
    layer.msg(message, {
        icon: 2,
        time: 2000
    });
} 