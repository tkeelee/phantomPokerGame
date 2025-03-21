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
            layer.msg(message, {icon: 2, time: 2000});
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
                        layer.msg(message, {icon: 2, time: 3000});
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
            
            console.log('[DEBUG] 清除用户数据成功');
        } catch (error) {
            console.error('[DEBUG] 清除用户数据失败:', error);
        }
    }
}); 