// 大厅页面逻辑
let stompClient = null;
let currentPlayer = null;
let connectionAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 3;

document.addEventListener('DOMContentLoaded', function() {
    // 清理游戏缓存
    clearGameCache();
    
    // 检查是否已登录
    currentPlayer = localStorage.getItem('playerName');
    if (!currentPlayer) {
        window.location.href = 'index.html';
        return;
    }

    // 显示玩家名称
    document.getElementById('playerName').textContent = currentPlayer;

    // 连接WebSocket
    connectWebSocket();
});

function connectWebSocket() {
    try {
        console.log('[DEBUG] 正在连接WebSocket...');
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        
        // 启用STOMP调试日志
        stompClient.debug = function(str) {
            console.log('[STOMP DEBUG]', str);
        };

        // 配置STOMP客户端
        const connectHeaders = {
            login: currentPlayer,
            passcode: 'unused'
        };

        console.log('[DEBUG] 开始连接，headers:', connectHeaders);

        // 在连接成功后设置订阅
        stompClient.connect(connectHeaders, function(frame) {
            console.log('[DEBUG] WebSocket连接成功，frame:', frame);
            connectionAttempts = 0;
            
            // 隐藏加载提示
            const loadingIndicator = document.getElementById('loadingIndicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }

            // 设置所有订阅
            setupSubscriptions();

            // 发送玩家在线状态
            const onlineStatus = {
                id: currentPlayer,
                name: currentPlayer,
                status: 'ONLINE'
            };
            console.log('[DEBUG] 发送在线状态:', onlineStatus);
            stompClient.send("/app/players/online", {}, JSON.stringify(onlineStatus));

            // 加载房间列表
            loadRoomList();
        }, function(error) {
            console.error('[DEBUG] WebSocket连接失败:', error);
            handleConnectionError();
        });
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
    console.log('[DEBUG] 准备订阅创建房间响应');
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
                console.log('[DEBUG] 已保存房间ID到localStorage');
                
                // 显示成功提示
                showSuccess('房间创建成功！');
                
                // 立即跳转到游戏页面
                const gameUrl = `game.html?roomId=${response.roomId}`;
                console.log('[DEBUG] 即将跳转到:', gameUrl);
                window.location.href = gameUrl;
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

    console.log('[DEBUG] 订阅设置完成');
}

function loadRoomList() {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/rooms/list", {}, JSON.stringify({
            playerId: currentPlayer
        }));
    } else {
        console.error('WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
    }
}

function updateRoomList(rooms) {
    const roomList = document.getElementById('roomList');
    if (!roomList) return;

    // 清除加载提示
    roomList.innerHTML = '';

    if (rooms.length === 0) {
        roomList.innerHTML = `
            <div class="text-center p-4">
                <p class="text-muted">暂无房间，点击"创建房间"开始游戏</p>
            </div>
        `;
        return;
    }

    rooms.forEach(room => {
        const roomElement = document.createElement('div');
        roomElement.className = 'room-item';
        roomElement.innerHTML = `
            <div class="room-info">
                <h5 class="mb-1">${room.name}</h5>
                <small>玩家数: ${room.playerCount}/${room.maxPlayers}</small>
            </div>
            <button onclick="joinRoom('${room.id}')" class="btn btn-primary btn-sm">
                加入房间
            </button>
        `;
        roomList.appendChild(roomElement);
    });
}

function updatePlayerList(players) {
    const playerList = document.getElementById('playerList');
    if (!playerList) return;

    playerList.innerHTML = '';
    
    if (!players || players.length === 0) {
        playerList.innerHTML = '<p class="text-muted text-center">暂无在线玩家</p>';
        return;
    }

    players.forEach(player => {
        const playerElement = document.createElement('div');
        playerElement.className = 'player-item';
        const statusText = player.status === 'PLAYING' ? '游戏中' : '在线';
        const statusClass = player.status === 'PLAYING' ? 'success' : 'primary';
        
        // 如果是当前玩家状态发生变化，显示提示
        if (player.id === currentPlayer && player.status === 'PLAYING') {
            showInfo('您已进入游戏状态');
        }
        
        playerElement.innerHTML = `
            <span class="player-name">${player.name}</span>
            <span class="badge bg-${statusClass}">${statusText}</span>
        `;
        playerList.appendChild(playerElement);
    });
}

function createRoom() {
    const roomName = document.getElementById('roomName').value.trim();
    const maxPlayers = parseInt(document.getElementById('maxPlayers').value);

    if (!roomName) {
        console.warn('[DEBUG] 创建房间失败: 房间名称为空');
        showError('请输入房间名称');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('[DEBUG] 创建房间失败: WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
        return;
    }

    console.log('[DEBUG] 发送创建房间请求');
    console.log('[DEBUG] 房间名:', roomName);
    console.log('[DEBUG] 最大玩家数:', maxPlayers);
    console.log('[DEBUG] 当前玩家:', currentPlayer);
    
    // 显示加载提示
    showLoading('正在创建房间...');
    
    // 发送创建房间请求
    const createRequest = {
        name: roomName,
        maxPlayers: maxPlayers,
        hostId: currentPlayer
    };
    console.log('[DEBUG] 发送创建房间请求数据:', createRequest);
    
    try {
        stompClient.send("/app/rooms/create", {}, JSON.stringify(createRequest));
        console.log('[DEBUG] 创建房间请求已发送');
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

function joinRoom(roomId) {
    if (!roomId) {
        console.error('[DEBUG] 加入房间失败: 房间ID为空');
        showError('房间ID无效');
        return;
    }

    if (stompClient && stompClient.connected) {
        console.log('[DEBUG] 发送加入房间请求');
        console.log('[DEBUG] 房间ID:', roomId);
        console.log('[DEBUG] 当前玩家:', currentPlayer);
        
        // 显示加载提示
        showLoading('正在加入房间...');
        
        // 设置超时处理
        const joinTimeout = setTimeout(() => {
            console.error('[DEBUG] 加入房间超时');
            hideLoading();
            showError('加入房间超时，请重试');
        }, 10000);
        
        // 存储超时处理器
        window.currentJoinTimeout = joinTimeout;
        
        // 发送加入房间请求
        const joinRequest = {
            roomId: roomId,
            playerId: currentPlayer
        };
        console.log('[DEBUG] 发送加入房间请求数据:', joinRequest);
        
        stompClient.send("/app/rooms/join", {}, JSON.stringify(joinRequest));
    } else {
        console.error('[DEBUG] 加入房间失败: WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
    }
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
    const loadingElement = document.createElement('div');
    loadingElement.id = 'loadingOverlay';
    loadingElement.className = 'loading-overlay';
    loadingElement.innerHTML = `
        <div class="loading-content">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">加载中...</span>
            </div>
            <p class="mt-2">${message}</p>
        </div>
    `;
    document.body.appendChild(loadingElement);
}

function hideLoading() {
    const loadingElement = document.getElementById('loadingOverlay');
    if (loadingElement) {
        loadingElement.remove();
    }
}

function showError(message) {
    // 使用 layer 插件显示错误消息
    layer.msg(message, {
        icon: 2,  // 错误图标
        time: 2000,  // 显示2秒
        anim: 6,  // 抖动动画
        shade: [0.3, '#000'],  // 遮罩
        offset: '30%'  // 位置靠上显示
    });
}

function showSuccess(message) {
    // 使用 layer 插件显示成功消息
    layer.msg(message, {
        icon: 1,  // 成功图标
        time: 1500,  // 显示1.5秒
        shade: [0.3, '#000'],  // 遮罩
        offset: '30%'  // 位置靠上显示
    });
}

function showInfo(message) {
    // 使用 layer 插件显示信息提示
    layer.msg(message, {
        icon: 0,  // 信息图标
        time: 2000,  // 显示2秒
        offset: '30%'  // 位置靠上显示
    });
}

function showWarning(message) {
    // 使用 layer 插件显示警告消息
    layer.msg(message, {
        icon: 3,  // 警告图标
        time: 2000,  // 显示2秒
        anim: 6,  // 抖动动画
        offset: '30%'  // 位置靠上显示
    });
}

function logout() {
    try {
        console.log('开始退出操作');
        
        // 清理所有用户数据
        clearAllUserData();
        
        // 发送离线通知
        if (stompClient && stompClient.connected) {
            showInfo('正在退出...');
            stompClient.send("/app/players/offline", {}, JSON.stringify(currentPlayer));
            
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
    console.log('清理所有用户数据');
    
    // 清除用户信息
    localStorage.removeItem('playerName');
    currentPlayer = null;
    
    // 清除房间信息
    localStorage.removeItem('currentRoomId');
    
    // 清除游戏相关数据
    const gameData = [
        'gameHand',
        'gamePile', 
        'gameHistory',
        'gameSettings',
        'lastGameState'
    ];
    
    gameData.forEach(key => {
        localStorage.removeItem(key);
    });
    
    // 重置WebSocket连接
    if (stompClient) {
        try {
            stompClient.disconnect();
        } catch (error) {
            console.error('断开WebSocket连接时发生错误:', error);
        }
        stompClient = null;
    }
    
    // 重置连接尝试次数
    connectionAttempts = 0;
}

function handleConnectionError() {
    connectionAttempts++;
    
    const loadingIndicator = document.getElementById('loadingIndicator');
    if (loadingIndicator) {
        if (connectionAttempts < MAX_RECONNECT_ATTEMPTS) {
            loadingIndicator.innerHTML = `
                <div class="text-center p-4">
                    <p class="text-warning">连接失败，正在尝试重新连接 (${connectionAttempts}/${MAX_RECONNECT_ATTEMPTS})...</p>
                </div>
            `;
            // 3秒后重试
            setTimeout(connectWebSocket, 3000);
        } else {
            loadingIndicator.innerHTML = `
                <div class="text-center p-4">
                    <p class="text-danger">无法连接到服务器</p>
                    <button onclick="forceReconnect()" class="btn btn-primary mt-2">重试连接</button>
                    <button onclick="backToLogin()" class="btn btn-secondary mt-2 ms-2">返回登录</button>
                </div>
            `;
        }
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