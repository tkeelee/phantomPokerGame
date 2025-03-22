// 大厅页面逻辑
let stompClient = null;
let currentPlayer = null;
let connectionAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 3;

document.addEventListener('DOMContentLoaded', function() {
    // 清理游戏缓存
    clearGameCache();
    
    // 检查是否被禁用
    if (checkBanStatus()) {
        console.log('[DEBUG] 检测到用户被禁用，无法进入大厅');
        return;
    }
    
    // 检查是否已登录
    try {
        const playerData = JSON.parse(localStorage.getItem('player'));
        if (!playerData || !playerData.id) {
            console.error('[DEBUG] 未找到有效的玩家信息');
            window.location.href = 'index.html';
            return;
        }

        currentPlayer = playerData.id;
        
        // 显示玩家名称
        document.getElementById('playerName').textContent = playerData.name || playerData.id;

        // 连接WebSocket
        connectWebSocket();
    } catch (error) {
        console.error('[DEBUG] 加载玩家信息失败:', error);
        window.location.href = 'index.html';
        return;
    }
});

function connectWebSocket() {
    try {
        console.log('[DEBUG] 正在连接WebSocket...');
        
        // 直接使用SockJS，它会自动处理协议
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        
        // 禁用STOMP调试日志，减少控制台输出
        stompClient.debug = null;

        // 从localStorage获取完整的玩家信息
        const playerData = JSON.parse(localStorage.getItem('player'));
        if (!playerData) {
            console.error('[DEBUG] 未找到玩家信息');
            window.location.href = 'index.html';
            return;
        }
        
        currentPlayer = playerData.id;
        
        // 配置STOMP客户端
        const connectHeaders = {
            login: currentPlayer
        };

        console.log('[DEBUG] 开始连接，玩家:', currentPlayer);

        stompClient.connect(connectHeaders, 
            function(frame) {
                console.log('[DEBUG] WebSocket连接成功');
                connectionAttempts = 0;
                
                // 隐藏加载提示
                hideLoading();
                
                // 显示连接成功提示
                showSuccess('连接成功！');

                // 设置所有订阅
                setupSubscriptions();

                // 先请求玩家列表更新
                requestPlayerListUpdate();

                // 再发送玩家在线状态
                setTimeout(() => {
                    sendPlayerOnlineStatus();
                }, 300);
                
                // 加载房间列表
                loadRoomList();
            }, 
            function(error) {
                console.error('[DEBUG] WebSocket连接失败:', error);
                handleConnectionError();
            }
        );
    } catch (error) {
        console.error('[DEBUG] WebSocket连接错误:', error);
        handleConnectionError();
    }
}

// 设置所有WebSocket订阅
function setupSubscriptions() {
    if (!stompClient || !stompClient.connected) {
        console.error('[DEBUG] 无法设置订阅：WebSocket未连接');
        return;
    }

    console.log('[DEBUG] 开始设置订阅');
    console.log('[DEBUG] 当前玩家:', currentPlayer);
    
    // 订阅创建房间响应
    console.log('[DEBUG] 准备订阅创建房间响应，路径: /user/queue/createRoom');
    const createRoomSub = stompClient.subscribe('/user/queue/createRoom', function(message) {
        console.log('[DEBUG] 进入createRoom消息处理函数');
        console.log('[DEBUG] 原始消息:', message);
        
        try {
            console.log('[DEBUG] 收到创建房间响应原始数据:', message.body);
            const response = JSON.parse(message.body);
            console.log('[DEBUG] 解析后的创建房间响应:', response);
            
            // 隐藏加载提示
            hideLoading();
            
            if (response.success) {
                console.log('[DEBUG] 创建房间成功，准备跳转');
                console.log('[DEBUG] 房间ID:', response.roomId);
                
                // 保存房间ID到localStorage
                localStorage.setItem('currentRoomId', response.roomId);
                console.log('[DEBUG] 已保存房间ID到localStorage:', response.roomId);
                
                // 显示成功提示
                showSuccess('房间创建成功！');
                
                // 添加延迟确保消息显示后再跳转
                setTimeout(function() {
                    // 立即跳转到游戏页面
                    const gameUrl = `game.html?roomId=${response.roomId}`;
                    console.log('[DEBUG] 即将跳转到:', gameUrl);
                    window.location.href = gameUrl;
                }, 500);
            } else {
                console.warn('[DEBUG] 创建房间失败:', response.message);
                showError(response.message || '创建房间失败');
            }
        } catch (error) {
            console.error('[DEBUG] 处理createRoom消息时发生错误:', error);
            console.error('[DEBUG] 错误堆栈:', error.stack);
            hideLoading();
            showError('创建房间失败');
        }
    });
    console.log('[DEBUG] 创建房间响应订阅ID:', createRoomSub.id);

    // 订阅系统广播消息 - 接收包括用户特定消息的备份广播
    stompClient.subscribe('/topic/system', function(message) {
        try {
            console.log('[DEBUG] 收到系统广播消息:', message.body);
            const systemMsg = JSON.parse(message.body);
            
            // 检查是否是针对当前用户的消息
            if (systemMsg.targetUser === currentPlayer) {
                console.log('[DEBUG] 收到针对当前用户的系统消息:', systemMsg);
                
                // 处理创建房间成功的备份通知
                if (systemMsg.action === 'CREATE_ROOM_SUCCESS') {
                    console.log('[DEBUG] 收到创建房间成功的备份通知');
                    const roomId = systemMsg.roomId;
                    
                    // 检查是否已经处理过这个房间ID
                    if (!localStorage.getItem('currentRoomId')) {
                        console.log('[DEBUG] 通过备份通知处理房间创建成功');
                        
                        // 保存房间ID到localStorage
                        localStorage.setItem('currentRoomId', roomId);
                        console.log('[DEBUG] 通过备份通知保存房间ID:', roomId);
                        
                        // 显示成功提示
                        showSuccess('房间创建成功！');
                        
                        // 添加延迟确保消息显示后再跳转
                        setTimeout(function() {
                            // 立即跳转到游戏页面
                            const gameUrl = `game.html?roomId=${roomId}`;
                            console.log('[DEBUG] 通过备份通知跳转到:', gameUrl);
                            window.location.href = gameUrl;
                        }, 500);
                    } else {
                        console.log('[DEBUG] 已经处理过该房间创建响应，忽略备份通知');
                    }
                }
            }
        } catch (error) {
            console.error('[DEBUG] 处理系统广播消息时出错:', error);
        }
    });
    
    // 订阅加入房间响应 - 修改订阅路径
    stompClient.subscribe('/user/queue/joinRoom', function(message) {
        console.log('[DEBUG] 进入joinRoom消息处理函数');
        try {
            console.log('[DEBUG] 收到加入房间响应原始数据:', message.body);
            const response = JSON.parse(message.body);
            console.log('[DEBUG] 解析后的加入房间响应:', response);
            
            // 清除加入超时
            clearJoinTimeout();
            
            // 隐藏加载提示
            hideLoading();
            
            if (response.success) {
                console.log('[DEBUG] 加入房间成功，准备跳转');
                console.log('[DEBUG] 房间ID:', response.roomId);
                
                // 保存房间ID到localStorage
                localStorage.setItem('currentRoomId', response.roomId);
                console.log('[DEBUG] 已保存房间ID到localStorage');
                
                // 显示成功提示
                showSuccess('成功加入房间！');
                
                // 立即跳转到游戏页面
                const gameUrl = `game.html?roomId=${response.roomId}`;
                console.log('[DEBUG] 即将跳转到:', gameUrl);
                window.location.href = gameUrl;
            } else {
                console.warn('[DEBUG] 加入房间失败:', response.message);
                showError(response.message || '加入房间失败');
            }
        } catch (error) {
            console.error('[DEBUG] 处理joinRoom消息时发生错误:', error);
            console.error('[DEBUG] 错误堆栈:', error.stack);
            hideLoading();
            showError('加入房间失败');
        }
    });
    
    // 订阅房间列表更新
    stompClient.subscribe('/topic/rooms', function(message) {
        console.log('[DEBUG] 收到房间列表更新');
        updateRoomList(JSON.parse(message.body));
    });
    
    // 订阅在线玩家更新
    stompClient.subscribe('/topic/players', function(message) {
        try {
            const players = JSON.parse(message.body);
            updatePlayerList(players);
        } catch (error) {
            console.error('解析玩家列表失败:', error);
        }
    });

    // 订阅个人错误消息
    stompClient.subscribe('/user/queue/errors', function(message) {
        try {
            const response = JSON.parse(message.body);
            showError(response.message);
        } catch (error) {
            console.error('解析错误消息失败:', error);
        }
    });
    
    // 订阅用户通知消息
    stompClient.subscribe('/user/queue/notifications', function(message) {
        try {
            const notification = JSON.parse(message.body);
            console.log('[DEBUG] 收到用户通知:', notification);
            
            // 处理不同类型的通知
            if (notification.type === 'ROOM_LEFT') {
                showSuccess(notification.message || '已离开房间');
                // 请求更新房间列表
                loadRoomList();
            } else if (notification.type === 'roomDissolved' || notification.type === 'FORCE_ROOM_EXIT') {
                showWarning(notification.message || '房间已解散');
                // 请求更新房间列表
                loadRoomList();
            } else if (notification.type === 'FORCE_LOGOUT') {
                // 处理强制登出消息
                handleForceLogout(notification);
            }
        } catch (error) {
            console.error('[DEBUG] 处理用户通知出错:', error);
        }
    });

    console.log('[DEBUG] 订阅设置完成');
}

function loadRoomList() {
    if (stompClient && stompClient.connected) {
        // 确保发送玩家ID为字符串
        const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        
        stompClient.send("/app/rooms/list", {}, JSON.stringify({
            playerId: playerIdStr
        }));
    } else {
        console.error('WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
    }
}

function updateRoomList(rooms) {
    const roomList = document.getElementById('roomList');
    if (!roomList) return;

    roomList.innerHTML = '';

    if (rooms.length === 0) {
        roomList.innerHTML = `
            <div class="text-center p-4">
                <p class="text-light">暂无房间，点击"创建房间"开始游戏</p>
            </div>
        `;
        return;
    }

    rooms.forEach(room => {
        // 确保玩家数量不为负数或零（当房间应该解散时）
        const playerCount = Math.max(room.playerCount || 0, 0);
        // 保证robotCount不为负
        const robotCount = Math.max(room.robotCount || 0, 0);
        // 计算真实人类玩家数
        const humanCount = Math.max(playerCount - robotCount, 0);
        
        // 如果房间人数为0，不显示该房间（应该已被解散）
        if (playerCount === 0) {
            return;
        }
        
        const roomElement = document.createElement('div');
        roomElement.className = 'room-item';
        
        // 设置状态标签颜色
        const statusClass = room.status === "WAITING" ? "bg-info" : 
                            room.status === "PLAYING" ? "bg-warning" : "bg-secondary";
        
        // 设置人数标签颜色
        const playerCountClass = playerCount >= room.maxPlayers ? "text-danger" : 
                                playerCount >= room.maxPlayers / 2 ? "text-warning" : "text-success";
        
        roomElement.innerHTML = `
            <div class="room-info">
                <h5 class="mb-1">${room.name}</h5>
                <div class="d-flex align-items-center flex-wrap">
                    <span class="badge ${statusClass} me-2">${getStatusText(room.status)}</span>
                    <small class="${playerCountClass} me-2">玩家: ${humanCount}</small>
                    ${robotCount > 0 ? `<small class="text-info me-2">机器人: ${robotCount}</small>` : ''}
                    <small class="${playerCountClass}">总数: ${playerCount}/${room.maxPlayers}</small>
                    <small class="ms-2 text-light opacity-75">房主: ${room.hostName || '未知'}</small>
                </div>
            </div>
            <button onclick="joinRoom('${room.id}')" class="btn ${room.status === 'WAITING' ? 'btn-primary' : 'btn-secondary'}" 
                ${room.status !== 'WAITING' || playerCount >= room.maxPlayers ? 'disabled' : ''}>
                ${room.status === 'WAITING' && playerCount < room.maxPlayers ? '加入房间' : '已满/游戏中'}
            </button>
        `;
        roomList.appendChild(roomElement);
    });
    
    console.log('[DEBUG] 已更新房间列表');
}

// 获取房间状态文本
function getStatusText(status) {
    switch(status) {
        case 'WAITING': return '<span class="status-text status-waiting">等待中</span>';
        case 'PLAYING': return '<span class="status-text status-playing">游戏中</span>';
        case 'FINISHED': return '<span class="status-text">已结束</span>';
        default: return '<span class="status-text">未知状态</span>';
    }
}

function updatePlayerList(players) {
    const playerList = document.getElementById('playerList');
    if (!playerList) return;

    playerList.innerHTML = '';

    if (!players || !Array.isArray(players) || players.length === 0) {
        playerList.innerHTML = '<div class="text-center p-3 text-light">暂无在线玩家</div>';
        return;
    }

    // 获取当前玩家ID
    const currentPlayerData = JSON.parse(localStorage.getItem('player'));
    const currentPlayerId = currentPlayerData ? currentPlayerData.id : '';

    // 遍历所有玩家，包括机器人
    players.forEach(player => {
        const isCurrentPlayer = player.id === currentPlayerId;
        // 识别机器人玩家（通过id前缀或type属性）
        const isRobot = player.robot === true || player.type === 'ROBOT' || (player.id && player.id.startsWith('robot_'));
        
        let statusHtml;
        if (player.status === 'ONLINE') {
            statusHtml = '<span class="status-text status-ready">在线</span>';
        } else if (player.status === 'PLAYING') {
            statusHtml = '<span class="status-text status-playing">游戏中</span>';
        } else if (player.status === 'READY') {
            statusHtml = '<span class="status-text status-ready">已准备</span>';
        } else if (player.status === 'WAITING') {
            statusHtml = '<span class="status-text status-waiting">等待中</span>';
        } else if (player.status === 'OFFLINE') {
            statusHtml = '<span class="status-text status-offline">离线</span>';
        } else {
            statusHtml = '<span class="status-text">' + player.status + '</span>';
        }
        
        const playerElement = document.createElement('div');
        playerElement.className = `player-item ${isCurrentPlayer ? 'current-player' : ''}`;
        playerElement.innerHTML = `
            <div class="d-flex justify-content-between align-items-center">
                <div>
                    <span class="player-name">${player.name || player.id}</span>
                    ${isRobot ? '<span class="robot-badge">机器人</span>' : ''}
                    ${isCurrentPlayer ? '<span class="badge bg-info ms-2">你</span>' : ''}
                </div>
                <span class="badge">${statusHtml}</span>
            </div>
        `;
        playerList.appendChild(playerElement);
    });
    
    console.log('[DEBUG] 已更新玩家列表，包含机器人');
}

// 获取玩家状态文本
function getPlayerStatusText(status) {
    switch(status) {
        case 'ONLINE': return '<span class="status-text status-ready">在线</span>';
        case 'PLAYING': return '<span class="status-text status-playing">游戏中</span>';
        case 'READY': return '<span class="status-text status-ready">已准备</span>';
        case 'WAITING': return '<span class="status-text status-waiting">等待中</span>';
        case 'OFFLINE': return '<span class="status-text status-offline">离线</span>';
        default: return '<span class="status-text">' + status + '</span>';
    }
}

function sendCreateRoomRequest() {
    // 获取房间信息
    const roomName = document.getElementById('roomName').value;
    const maxPlayers = parseInt(document.getElementById('maxPlayers').value);
    
    // 验证房间名称
    if (!roomName || roomName.trim() === '') {
        showError('请输入房间名称');
        return;
    }
    
    // 验证最大玩家数
    if (isNaN(maxPlayers) || maxPlayers < 2 || maxPlayers > 8) {
        showError('玩家数量必须在2-8之间');
        return;
    }
    
    // 显示加载提示
    showLoading('正在创建房间...');
    
    // 获取当前玩家信息
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        console.error('[DEBUG] 无效的玩家数据');
        hideLoading();
        showError('登录状态异常，请重新登录');
        return;
    }
    
    // 封装请求数据
    const createRequest = {
        roomName: roomName,
        maxPlayers: maxPlayers,
        hostId: playerData.id,
        hostName: playerData.name,
        createTime: new Date().getTime()
    };
    
    console.log('[DEBUG] 发送创建房间请求数据:', createRequest);
    
    try {
        // 保存当前请求信息到session storage（用于恢复）
        sessionStorage.setItem('lastCreateRoomRequest', JSON.stringify({
            request: createRequest,
            timestamp: new Date().getTime()
        }));
        
        // 添加延迟以确保订阅已就绪
        setTimeout(() => {
            console.log('[DEBUG] 正在发送创建房间请求...');
            stompClient.send("/app/rooms/create", {}, JSON.stringify(createRequest));
            console.log('[DEBUG] 创建房间请求已发送');
            
            // 5秒后检查是否收到响应
            setTimeout(() => {
                console.log('[DEBUG] 检查创建房间响应状态...');
                const currentRoomId = localStorage.getItem('currentRoomId');
                if (!currentRoomId) {
                    console.warn('[DEBUG] 5秒内未收到创建房间响应，尝试恢复');
                    handleCreateRoomTimeout();
                }
            }, 5000);
        }, 100);
    } catch (error) {
        console.error('[DEBUG] 发送创建房间请求时发生错误:', error);
        hideLoading();
        showError('发送创建房间请求失败');
        return;
    }
    
    // 关闭创建房间模态框
    const modal = bootstrap.Modal.getInstance(document.getElementById('createRoomModal'));
    if (modal) {
        modal.hide();
    }
}

// 添加新的超时处理函数
function handleCreateRoomTimeout() {
    console.log('[DEBUG] 处理创建房间超时...');
    
    try {
        // 获取上次创建请求信息
        const lastRequest = JSON.parse(sessionStorage.getItem('lastCreateRoomRequest'));
        if (!lastRequest) {
            console.warn('[DEBUG] 未找到上次创建请求信息');
            return;
        }
        
        // 检查是否真的超时（给10秒的余地）
        const now = new Date().getTime();
        if (now - lastRequest.timestamp < 10000) {
            console.log('[DEBUG] 尚未真正超时，继续等待');
            return;
        }
        
        // 尝试重新连接
        console.log('[DEBUG] 尝试重新连接并恢复状态');
        showWarning('正在尝试恢复连接...');
        
        // 重新连接WebSocket
        if (!stompClient || !stompClient.connected) {
            connectWebSocket();
        }
        
        // 尝试手动查找房间
        tryManualEntryToNewRoom(lastRequest.request.hostId);
        
    } catch (error) {
        console.error('[DEBUG] 处理创建房间超时出错:', error);
        showError('创建房间失败，请重试');
    } finally {
        // 清理临时存储
        sessionStorage.removeItem('lastCreateRoomRequest');
    }
}

function joinRoom(roomId) {
    if (!roomId) return;
    
    // 获取玩家数据
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        showError('请先登录');
        return;
    }
    
    // 检查玩家是否已在某个房间
    if (playerData.roomId) {
        // 如果已经在同一个房间，显示返回房间消息并直接跳转
        if (playerData.roomId === roomId) {
            showInfo('您已在该房间中，正在返回...');
            setTimeout(() => {
                window.location.href = `/game.html?roomId=${roomId}`;
            }, 1000);
            return;
        }
        
        // 如果在另一个房间，提示先离开当前房间
        showWarning('您已在其他房间中，请先离开当前房间');
        return;
    }
    
    // 正常加入房间逻辑
    showLoading('正在加入房间...');
    
    fetch(`/api/room/${roomId}/join`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ playerId: playerData.id })
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        
        if (data.error) {
            showError(data.error);
            return;
        }
        
        // 成功加入房间，更新玩家数据
        playerData.roomId = roomId;
        localStorage.setItem('player', JSON.stringify(playerData));
        
        // 跳转到游戏页面
        showSuccess('加入房间成功，正在进入游戏...');
        setTimeout(() => {
            window.location.href = `/game.html?roomId=${roomId}`;
        }, 1000);
    })
    .catch(error => {
        hideLoading();
        showError('加入房间失败: ' + error.message);
        console.error('加入房间失败:', error);
    });
}

function showCreateRoomModal() {
    const modal = new bootstrap.Modal(document.getElementById('createRoomModal'));
    modal.show();
}

function forceReconnect() {
    connectionAttempts = 0;
    const loadingIndicator = document.getElementById('loadingIndicator');
    if (loadingIndicator) {
        loadingIndicator.innerHTML = `
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">加载中...</span>
            </div>
            <p class="mt-2">正在连接服务器...</p>
        `;
    }
    connectWebSocket();
}

function backToLogin() {
    localStorage.removeItem('playerName');
    window.location.href = 'index.html';
}

function showLoading(message) {
    if (typeof layer !== 'undefined') {
        // 确保没有重复的loading
        if (window.loadingIndex) {
            layer.close(window.loadingIndex);
        }
        
        // 使用与深色主题协调的样式
        window.loadingIndex = layer.load(1, {
            shade: [0.6, '#121a2b'],
            time: 30000 // 30秒后自动关闭
        });
        
        // 如果有消息，也显示
        if (message) {
            layer.msg(message, {
                icon: 16,
                shade: false,
                time: 10000, // 10秒后自动关闭
                skin: 'game-loading-msg',
                offset: '15px'
            });
        }
    }
}

function hideLoading() {
    if (typeof layer !== 'undefined' && window.loadingIndex) {
        layer.close(window.loadingIndex);
        window.loadingIndex = null;
    }
}

function showError(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message);
    } else {
        alert(message);
    }
    console.error(message);
}

function showSuccess(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message);
    } else {
        alert(message);
    }
    console.log(message);
}

function showInfo(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message);
    }
    console.log(message);
}

function showWarning(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message);
    } else {
        alert(message);
    }
    console.warn(message);
}

function logout() {
    try {
        console.log('开始退出操作');
        
        // 清理所有用户数据
        clearAllUserData();
        
        // 发送离线通知
        if (stompClient && stompClient.connected) {
            showInfo('正在退出...');
            
            // 确保发送的是字符串ID而不是对象
            const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
            stompClient.send("/app/players/offline", {}, JSON.stringify(playerIdStr));
            
            // 断开WebSocket连接
            stompClient.disconnect(() => {
                console.log('WebSocket连接已断开');
                // 跳转到登录页面
                window.location.href = 'index.html';
            });
        } else {
            console.log('WebSocket未连接，直接返回登录页面');
            window.location.href = 'index.html';
        }
    } catch (error) {
        console.error('退出时发生错误:', error);
        // 发生错误时也要清理数据
        clearAllUserData();
        showError('退出时发生错误，将返回登录页面');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 1500);
    }
}

// 清理所有用户数据
function clearAllUserData() {
    try {
        console.log('[DEBUG] 清除所有用户数据...');
        
        // 清除localStorage中的用户数据
        localStorage.removeItem('player');
        localStorage.removeItem('currentRoomId');
        localStorage.removeItem('gameState');
        localStorage.removeItem('token');
        localStorage.removeItem('lastLogin');
        localStorage.removeItem('userSettings');
        
        // 清除所有游戏相关数据
        const gameData = [
            'currentRoomId',
            'gameHand',
            'gamePile',
            'gameHistory',
            'gameSettings',
            'lastGameState',
            'playerStatus',
            'gameRole',
            'readyState',
            'turnOrder',
            'currentTurn',
            'gamePhase'
        ];
        
        // 清除localStorage和sessionStorage中的游戏数据
        gameData.forEach(key => {
            localStorage.removeItem(key);
            sessionStorage.removeItem(key);
        });
        
        // 清除sessionStorage
        sessionStorage.clear();
        
        console.log('[DEBUG] 用户数据清除完成');
    } catch (error) {
        console.error('[DEBUG] 清除用户数据失败:', error);
    }
}

function handleConnectionError() {
    hideLoading();
    
    if (connectionAttempts < MAX_RECONNECT_ATTEMPTS) {
        connectionAttempts++;
        showWarning(`连接失败，正在尝试重新连接(${connectionAttempts}/${MAX_RECONNECT_ATTEMPTS})...`);
        
        // 使用递增的延迟时间重试
        setTimeout(connectWebSocket, 1000 * connectionAttempts);
    } else {
        showError('无法连接到服务器，请检查网络连接后重试');
        // 清除登录状态
        clearAllUserData();
        // 延迟返回登录页
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 2000);
    }
}

// 在加入房间响应处理中清除超时
function clearJoinTimeout() {
    if (window.currentJoinTimeout) {
        clearTimeout(window.currentJoinTimeout);
        window.currentJoinTimeout = null;
    }
}

// 清理游戏相关缓存
function clearGameCache() {
    console.log('[DEBUG] 清理游戏缓存...');
    
    // 清除游戏相关数据
    const gameData = [
        'currentRoomId',    // 当前房间ID
        'gameHand',        // 玩家手牌
        'gamePile',        // 游戏牌堆
        'gameHistory',     // 游戏历史记录
        'gameSettings',    // 游戏设置
        'lastGameState',   // 最后的游戏状态
        'playerStatus',    // 玩家状态
        'gameRole',        // 游戏角色
        'readyState',      // 准备状态
        'turnOrder',       // 回合顺序
        'currentTurn',     // 当前回合
        'gamePhase'        // 游戏阶段
    ];
    
    gameData.forEach(key => {
        if (localStorage.getItem(key)) {
            console.log(`[DEBUG] 清除缓存: ${key}`);
            localStorage.removeItem(key);
        }
    });
    
    console.log('[DEBUG] 游戏缓存清理完成');
}

// 添加新函数，尝试手动查找并进入玩家创建的房间
function tryManualEntryToNewRoom(playerName) {
    console.log('[DEBUG] 尝试手动查找并进入房间 - 玩家:', playerName);
    
    // 发送请求获取当前房间列表
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/rooms/list", {}, JSON.stringify({
            playerId: playerName
        }));
        
        // 临时订阅房间列表以进行一次性处理
        const tempSub = stompClient.subscribe('/topic/rooms', function(message) {
            // 处理完后取消订阅
            tempSub.unsubscribe();
            
            try {
                const rooms = JSON.parse(message.body);
                console.log('[DEBUG] 手动查找房间 - 收到房间列表:', rooms);
                
                // 查找玩家作为房主的房间
                const playerRoom = rooms.find(room => room.hostId === playerName);
                
                if (playerRoom) {
                    console.log('[DEBUG] 找到玩家创建的房间:', playerRoom);
                    
                    // 保存房间ID并跳转
                    localStorage.setItem('currentRoomId', playerRoom.id);
                    console.log('[DEBUG] 手动保存房间ID:', playerRoom.id);
                    
                    // 显示成功提示
                    showSuccess('房间创建成功，正在进入...');
                    
                    // 跳转到游戏页面
                    setTimeout(() => {
                        const gameUrl = `game.html?roomId=${playerRoom.id}`;
                        console.log('[DEBUG] 手动跳转到:', gameUrl);
                        window.location.href = gameUrl;
                    }, 1000);
                } else {
                    console.warn('[DEBUG] 未找到玩家创建的房间');
                    showError('房间创建可能出现问题，请重试');
                }
            } catch (error) {
                console.error('[DEBUG] 手动查找房间时出错:', error);
            }
        });
    } else {
        console.error('[DEBUG] 无法手动查找房间 - WebSocket未连接');
        showError('连接已断开，请刷新页面');
    }
}

function connect() {
    // 获取玩家信息
    const player = JSON.parse(localStorage.getItem('player'));
    if (!player) {
        console.error('未找到玩家信息，请重新登录');
        alert('登录信息已失效，请重新登录');
        window.location.href = 'index.html';
        return;
    }
    
    // 显示连接中状态
    showLoading('正在连接到服务器...');
    
    try {
        // 使用GameState中的统一连接方法
        GameState.connect(player.id, 
            function(frame) {
                // 连接成功回调
                hideLoading();
                console.log('连接成功');
                
                // 设置全局变量
                currentPlayer = player;
                
                // 设置玩家在线状态
                const onlineStatus = {
                    id: player.id,
                    name: player.name,
                    status: 'ONLINE'
                };
                
                // 发送上线消息
                GameState.stompClient.send("/app/players/online", {}, JSON.stringify(onlineStatus));
                
                // 订阅房间列表更新
                GameState.stompClient.subscribe('/topic/rooms', function(message) {
                    handleRoomListUpdate(JSON.parse(message.body));
                });
                
                // 订阅玩家创建房间的结果
                GameState.stompClient.subscribe('/user/' + player.id + '/queue/createRoom', function(message) {
                    handleCreateRoomResponse(JSON.parse(message.body));
                });
                
                // 订阅玩家加入房间的结果
                GameState.stompClient.subscribe('/user/' + player.id + '/queue/joinRoom', function(message) {
                    handleJoinRoomResponse(JSON.parse(message.body));
                });
                
                // 订阅系统消息
                GameState.stompClient.subscribe('/topic/system', function(message) {
                    handleSystemMessage(JSON.parse(message.body));
                });
                
                // 请求房间列表
                refreshRoomList();
                
                // 显示玩家名称
                document.getElementById('playerName').textContent = player.name;
            },
            function(error) {
                // 连接失败回调
                hideLoading();
                console.error('连接失败:', error);
                showError('连接服务器失败，请稍后重试');
                
                // 5秒后重试
                setTimeout(connect, 5000);
            }
        );
    } catch (error) {
        hideLoading();
        console.error('连接过程中出错:', error);
        showError('连接发生错误: ' + error.message);
        
        // 5秒后重试
        setTimeout(connect, 5000);
    }
}

function sendPlayerOnlineStatus() {
    if (!stompClient || !stompClient.connected) {
        console.error('[DEBUG] 无法发送在线状态：WebSocket未连接');
        return;
    }
    
    try {
        const playerData = JSON.parse(localStorage.getItem('player'));
        if (!playerData) {
            console.error('[DEBUG] 无法发送在线状态：未找到玩家信息');
            return;
        }
        
        const onlineStatus = {
            id: playerData.id,
            name: playerData.name,
            status: 'ONLINE',
            loginTime: playerData.loginTime || new Date().getTime()
        };
        
        console.log('[DEBUG] 准备发送在线状态:', onlineStatus);
        
        // 发送在线状态
        stompClient.send("/app/players/online", {}, JSON.stringify(onlineStatus));
        console.log('[DEBUG] 发送在线状态成功');
        
        // 发送成功后，立即请求一次玩家列表更新
        setTimeout(() => {
            requestPlayerListUpdate();
        }, 500);
        
        // 设置定时发送在线状态的定时器(每60秒发送一次)
        if (!window.playerOnlineStatusInterval) {
            window.playerOnlineStatusInterval = setInterval(() => {
                if (stompClient && stompClient.connected) {
                    stompClient.send("/app/players/online", {}, JSON.stringify({
                        ...onlineStatus,
                        timestamp: new Date().getTime()
                    }));
                    console.log('[DEBUG] 定时发送在线状态');
                }
            }, 60000);
        }
    } catch (error) {
        console.error('[DEBUG] 发送在线状态失败:', error);
    }
}

// 添加一个新的函数，请求更新玩家列表
function requestPlayerListUpdate() {
    if (stompClient && stompClient.connected) {
        console.log('[DEBUG] 请求更新玩家列表');
        
        // 确保发送玩家ID为字符串
        const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        
        stompClient.send("/app/players/list", {}, JSON.stringify({
            requestTime: new Date().getTime(),
            playerId: playerIdStr  // 添加当前玩家ID以便后台能够识别请求来源
        }));
    } else {
        console.warn('[DEBUG] 无法请求玩家列表：WebSocket未连接');
    }
}

// 处理强制登出通知
function handleForceLogout(notification) {
    console.log('[DEBUG] 收到强制登出通知:', notification);
    
    // 显示被踢出的消息
    const reason = notification.reason || '违反规则';
    const message = notification.message || `您已被管理员踢出游戏，原因：${reason}`;
    
    // 使用layer显示消息
    if (typeof layer !== 'undefined') {
        layer.msg(message, {time: 3000});
    } else {
        alert(message);
    }
    
    // 清除本地存储中的玩家数据
    localStorage.removeItem('player');
    localStorage.removeItem('token');
    localStorage.removeItem('currentRoom');
    localStorage.removeItem('readyPlayers');
    
    // 清除会话存储
    sessionStorage.clear();
    
    // 尝试删除游戏缓存
    try {
        if (window.indexedDB) {
            const request = window.indexedDB.deleteDatabase('gameCache');
            request.onsuccess = function() {
                console.log('[DEBUG] 游戏缓存已成功删除');
            };
            request.onerror = function() {
                console.error('[DEBUG] 无法删除游戏缓存');
            };
        }
    } catch (e) {
        console.error('[DEBUG] 删除游戏缓存时出错:', e);
    }
    
    // 调用其他清理函数（如果存在）
    if (typeof clearAllUserData === 'function') {
        clearAllUserData();
    }
    
    if (typeof clearGameCache === 'function') {
        clearGameCache();
    }
    
    // 断开WebSocket连接
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
        console.log('[DEBUG] WebSocket连接已断开');
    }
    
    // 设置踢出标志和时间
    localStorage.setItem('kicked_out', 'true');
    localStorage.setItem('kicked_time', Date.now().toString());
    localStorage.setItem('kicked_reason', reason);
    
    // 延迟跳转到登录页面，给用户一点时间看到消息
    setTimeout(function() {
        window.location.href = '/index.html?reason=' + encodeURIComponent('您已被踢出游戏');
    }, 2000);
}

// 检查禁用状态
function checkBanStatus() {
    try {
        // 从sessionStorage中获取禁用信息
        const banInfoStr = sessionStorage.getItem('banInfo');
        if (!banInfoStr) {
            return false;
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
                
                showWarning(message);
                
                // 延迟跳转
                setTimeout(() => {
                    window.location.href = 'index.html';
                }, 2000);
                
                return true;
            } else {
                // 禁用时间已过，清除禁用信息
                sessionStorage.removeItem('banInfo');
                return false;
            }
        }
        
        return false;
    } catch (error) {
        console.error('[DEBUG] 检查禁用状态出错:', error);
        // 出错时清除禁用信息
        sessionStorage.removeItem('banInfo');
        return false;
    }
}

// 添加createRoom函数
function createRoom() {
    sendCreateRoomRequest();
}

// 添加一个新的函数，请求更新玩家列表
function requestPlayerListUpdate() {
    if (stompClient && stompClient.connected) {
        console.log('[DEBUG] 请求更新玩家列表');
        stompClient.send("/app/players/list", {}, JSON.stringify({
            requestTime: new Date().getTime()
        }));
    }
}

// 添加机器人到房间
function addRobots() {
    const roomId = currentRoom.id;
    if (!roomId) {
        showMessage('错误', '请先创建或加入房间');
        return;
    }
    
    const count = prompt('请输入要添加的机器人数量 (1-3):', '1');
    if (!count) return;
    
    const robotCount = parseInt(count);
    if (isNaN(robotCount) || robotCount < 1 || robotCount > 3) {
        showMessage('错误', '请输入1-3之间的数字');
        return;
    }
    
    // 调用API添加机器人
    fetch(`/rooms/${roomId}/robots?count=${robotCount}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            showMessage('错误', data.error);
        } else {
            showMessage('成功', `已添加${robotCount}个机器人`);
        }
    })
    .catch(error => {
        console.error('添加机器人失败:', error);
        showMessage('错误', '添加机器人失败: ' + error.message);
    });
}

// 从房间移除所有机器人
function removeRobots() {
    const roomId = currentRoom.id;
    if (!roomId) {
        showMessage('错误', '请先创建或加入房间');
        return;
    }
    
    // 调用API移除机器人
    fetch(`/rooms/${roomId}/robots`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            showMessage('错误', data.error);
        } else {
            showMessage('成功', `已移除所有机器人`);
        }
    })
    .catch(error => {
        console.error('移除机器人失败:', error);
        showMessage('错误', '移除机器人失败: ' + error.message);
    });
}

// 更新UI
function updateUI() {
    const playerData = JSON.parse(localStorage.getItem('player'));
    const playerName = playerData ? playerData.name : '游客';
    document.getElementById('playerName').textContent = playerName;
    
    // 修复房间操作区的显示逻辑
    const roomInfo = document.getElementById('roomInfo');
    const createRoomBtn = document.getElementById('createRoomBtn');
    const refreshRoomsBtn = document.getElementById('refreshRoomsBtn');
    const hostActions = document.getElementById('hostActions');
    
    if (playerData && playerData.roomId) {
        // 已在房间中
        if (createRoomBtn) createRoomBtn.style.display = 'none';
        if (refreshRoomsBtn) refreshRoomsBtn.style.display = 'none';
        
        // 房主特殊权限
        if (hostActions && currentRoom && playerData.id === currentRoom.hostId) {
            hostActions.style.display = 'block';
        } else if (hostActions) {
            hostActions.style.display = 'none';
        }
    } else {
        // 未在房间中
        if (createRoomBtn) createRoomBtn.style.display = 'inline-block';
        if (refreshRoomsBtn) refreshRoomsBtn.style.display = 'inline-block';
        if (hostActions) hostActions.style.display = 'none';
    }
}

// 修复加载函数，避免重复显示按钮
window.onload = function() {
    // 检查玩家登录状态
    checkLoginStatus();
    
    // 连接WebSocket
    connectWebSocket();
    
    // 初始化页面
    initializeLobby();
    
    // 更新UI
    updateUI();
};

// 初始化大厅
function initializeLobby() {
    // 移除重复的按钮和元素
    const duplicateButtons = document.querySelectorAll('.room-actions');
    if (duplicateButtons.length > 1) {
        for (let i = 1; i < duplicateButtons.length; i++) {
            duplicateButtons[i].remove();
        }
    }
    
    // 加载房间列表
    loadRoomList();
}

// 检查登录状态
function checkLoginStatus() {
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        // 未登录，跳转到登录页
        window.location.href = '/login.html';
    }
} 