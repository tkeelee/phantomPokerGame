// 登录页面逻辑
$(function() {
    // 清除之前的登录状态，但保留禁用信息
    clearAllUserData(true);
    
    // 检查是否之前被禁用
    checkBanStatus();
    
    // 添加昵称输入框的事件监听
    $('#nickname').keypress(function(event) {
        if (event.keyCode === 13) { // Enter键
            login();
        }
    });
    
    // 添加登录按钮的点击事件
    $('#login-btn').click(function() {
        login();
    });
    
    // 显示错误信息的函数
    window.showError = function(message) {
        // 如果layer已加载，使用layer提示
        if (typeof layer !== 'undefined') {
            layer.msg(message);
        } else {
            // 否则使用alert
            alert(message);
        }
    };
    
    // 检查禁用状态
    function checkBanStatus() {
        try {
            // 从sessionStorage中获取禁用信息
            const banInfoStr = sessionStorage.getItem('banInfo');
            if (!banInfoStr) {
                return;
            }
            
            const banInfo = JSON.parse(banInfoStr);
            const now = new Date().getTime();
            
            // 如果有禁用结束时间
            if (banInfo.bannedUntil) {
                const banUntil = new Date(banInfo.bannedUntil);
                
                // 如果禁用时间未过
                if (banUntil > now) {
                    // 计算剩余时间
                    const remainingMinutes = Math.ceil((banUntil - now) / (60 * 1000));
                    const message = `您的账号已被暂时禁用，${remainingMinutes}分钟后可再次登录`;
                    
                    if (typeof layer !== 'undefined') {
                        layer.msg(message);
                    } else {
                        alert(message);
                    }
                } else {
                    // 禁用时间已过，清除禁用信息
                    sessionStorage.removeItem('banInfo');
                }
            }
        } catch (error) {
            console.error('[DEBUG] 检查禁用状态出错:', error);
            // 出错时清除禁用信息
            sessionStorage.removeItem('banInfo');
        }
    }
    
    // 登录页面加载时检查踢出状态
    document.addEventListener('DOMContentLoaded', function() {
        // 检查URL参数
        const urlParams = new URLSearchParams(window.location.search);
        const reason = urlParams.get('reason');
        if (reason) {
            document.getElementById('loginMessage').innerHTML = `<div class="alert alert-warning">${reason}</div>`;
        }
        
        // 检查是否被踢出
        checkKickedStatus();
        
        // 清理任何潜在的过期用户数据
        cleanupUserData();
    });

    // 检查用户是否被踢出
    function checkKickedStatus() {
        const kickedOut = localStorage.getItem('kicked_out');
        const kickedTime = localStorage.getItem('kicked_time');
        const kickReason = localStorage.getItem('kicked_reason');
        
        if (kickedOut === 'true' && kickedTime) {
            const now = Date.now();
            const kickTime = parseInt(kickedTime);
            const elapsedSeconds = (now - kickTime) / 1000;
            
            // 如果原因是封禁，显示不同的消息
            const isBanned = kickReason === 'BAN' || kickReason === 'ADMIN_BAN';
            const waitTimeMax = isBanned ? 30 : 10; // 封禁30秒，普通踢出10秒
            
            // 如果被踢出后不到等待时间，禁止重新登录
            if (elapsedSeconds < waitTimeMax) {
                const waitTime = Math.ceil(waitTimeMax - elapsedSeconds);
                const loginButton = document.getElementById('login-btn');
                if (loginButton) {
                    loginButton.disabled = true;
                }
                
                // 显示不同的消息取决于踢出原因
                let message = '';
                if (isBanned) {
                    message = `<div class="alert alert-danger">您的账号已被临时封禁，请等待 ${waitTime} 秒后再尝试登录</div>`;
                } else {
                    message = `<div class="alert alert-warning">您已被踢出游戏，请等待 ${waitTime} 秒后再尝试登录</div>`;
                }
                
                // 添加消息显示区域
                if (!document.getElementById('loginMessage')) {
                    const messageDiv = document.createElement('div');
                    messageDiv.id = 'loginMessage';
                    document.querySelector('.login-form').prepend(messageDiv);
                }
                
                document.getElementById('loginMessage').innerHTML = message;
                
                // 设置定时器定期更新等待时间
                const waitInterval = setInterval(function() {
                    const currentTime = Date.now();
                    const newElapsed = (currentTime - kickTime) / 1000;
                    const newWaitTime = Math.ceil(waitTimeMax - newElapsed);
                    
                    if (newWaitTime <= 0) {
                        clearInterval(waitInterval);
                        if (loginButton) {
                            loginButton.disabled = false;
                        }
                        
                        document.getElementById('loginMessage').innerHTML = 
                            `<div class="alert alert-warning">您现在可以重新登录</div>`;
                            
                        // 清除踢出标记
                        localStorage.removeItem('kicked_out');
                        localStorage.removeItem('kicked_time');
                        localStorage.removeItem('kicked_reason');
                    } else {
                        // 更新倒计时
                        if (isBanned) {
                            document.getElementById('loginMessage').innerHTML = 
                                `<div class="alert alert-danger">您的账号已被临时封禁，请等待 ${newWaitTime} 秒后再尝试登录</div>`;
                        } else {
                            document.getElementById('loginMessage').innerHTML = 
                                `<div class="alert alert-warning">您已被踢出游戏，请等待 ${newWaitTime} 秒后再尝试登录</div>`;
                        }
                    }
                }, 1000);
            } else {
                // 超过等待时间，清除踢出标记
                localStorage.removeItem('kicked_out');
                localStorage.removeItem('kicked_time');
                localStorage.removeItem('kicked_reason');
            }
        }
    }

    // 彻底清理用户数据
    function cleanupUserData() {
        // 清理任何可能导致问题的旧数据
        localStorage.removeItem('player');
        localStorage.removeItem('token');
        localStorage.removeItem('currentRoom');
        localStorage.removeItem('readyPlayers');
        
        // 清除会话存储
        sessionStorage.clear();
        
        // 清除任何游戏相关的缓存
        if (typeof clearGameCache === 'function') {
            clearGameCache();
        }
    }
    
    // 登录验证
    function login() {
        var nickname = $('#nickname').val().trim();
        
        if (!nickname) {
            showError('请输入昵称');
            return;
        }
        
        if (nickname.length < 2 || nickname.length > 10) {
            showError('昵称长度必须在2-10个字符之间');
            return;
        }
        
        // 检查特殊字符
        if (!/^[\u4e00-\u9fa5_a-zA-Z0-9]+$/.test(nickname)) {
            showError('昵称只能包含中文、英文、数字和下划线');
            return;
        }
        
        // 检查是否被禁用
        try {
            const banInfoStr = sessionStorage.getItem('banInfo');
            if (banInfoStr) {
                const banInfo = JSON.parse(banInfoStr);
                
                // 如果存在禁用结束时间
                if (banInfo.bannedUntil) {
                    const banUntil = new Date(banInfo.bannedUntil);
                    const now = new Date();
                    
                    // 如果禁用时间未过
                    if (banUntil > now) {
                        // 计算剩余时间
                        const remainingMinutes = Math.ceil((banUntil - now) / (60 * 1000));
                        showError(`您的账号已被暂时禁用，${remainingMinutes}分钟后可再次登录`);
                        return;
                    } else {
                        // 禁用时间已过，清除禁用信息
                        sessionStorage.removeItem('banInfo');
                    }
                }
            }
        } catch (error) {
            console.error('[DEBUG] 检查禁用状态出错:', error);
            // 出错时清除禁用信息
            sessionStorage.removeItem('banInfo');
        }
        
        // 检查是否被踢出且仍在冷却期
        const kickedOut = localStorage.getItem('kicked_out');
        const kickedTime = localStorage.getItem('kicked_time');
        if (kickedOut === 'true' && kickedTime) {
            const now = Date.now();
            const kickTime = parseInt(kickedTime);
            const elapsedSeconds = (now - kickTime) / 1000;
            
            if (elapsedSeconds < 30) {
                const waitTime = Math.ceil(30 - elapsedSeconds);
                document.getElementById('loginMessage').innerHTML = 
                    `<div class="alert alert-danger">请等待 ${waitTime} 秒后再尝试登录</div>`;
                return;
            }
        }
        
        try {
            // 保存玩家信息
            var player = {
                id: nickname,
                name: nickname,
                loginTime: new Date().getTime()
            };
            localStorage.setItem('player', JSON.stringify(player));
            
            // 显示加载提示
            showLoading('正在进入游戏...');
            
            // 跳转到大厅页面
            window.location.href = 'lobby.html';
        } catch (error) {
            console.error('[DEBUG] 登录过程出错:', error);
            showError('登录失败，请重试');
        }
    }
    
    function showLoading(message) {
        if (typeof layer !== 'undefined') {
            layer.load(2, {
                shade: [0.3, '#000']
            });
            layer.msg(message, {
                icon: 16,
                shade: 0.3,
                time: -1
            });
        }
    }
    
    function clearAllUserData(keepBanInfo = false) {
        try {
            // 如果需要保留禁用信息，先保存
            let banInfo = null;
            if (keepBanInfo) {
                banInfo = sessionStorage.getItem('banInfo');
            }
            
            localStorage.removeItem('player');
            localStorage.removeItem('currentRoomId');
            localStorage.removeItem('gameState');
            
            // 清除所有sessionStorage，除了禁用信息
            if (keepBanInfo && banInfo) {
                sessionStorage.clear();
                sessionStorage.setItem('banInfo', banInfo);
            } else {
                sessionStorage.clear();
            }
            
            console.debug('清除用户数据成功');
        } catch (error) {
            console.error('[DEBUG] 清除用户数据失败:', error);
        }
    }
}); 