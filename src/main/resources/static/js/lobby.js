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

                // 发送玩家在线状态
                sendPlayerOnlineStatus();
                
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
            <button onclick="joinRoom('${room.id}')" class="btn btn-primary btn-sm" style="white-space: nowrap;">
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

    // 获取完整的玩家信息
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        console.error('[DEBUG] 创建房间失败: 无效的玩家信息');
        showError('登录状态异常，请重新登录');
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 1500);
        return;
    }

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
    console.log('[DEBUG] 当前玩家:', playerData);
    
    // 显示加载提示
    showLoading('正在创建房间...');
    
    // 发送创建房间请求
    const createRequest = {
        name: roomName,
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
            loginTime: playerData.loginTime
        };
        
        stompClient.send("/app/players/online", {}, JSON.stringify(onlineStatus));
        console.log('[DEBUG] 发送在线状态成功:', onlineStatus);
    } catch (error) {
        console.error('[DEBUG] 发送在线状态失败:', error);
    }
}

// 处理强制登出消息
function handleForceLogout(notification) {
    console.log('[DEBUG] 收到强制登出通知:', notification);
    
    // 如果包含禁用信息，保存到sessionStorage
    if (notification.reason === 'BANNED' || notification.bannedUntil) {
        let banInfo = {
            reason: notification.reason,
            message: notification.message,
            bannedUntil: notification.bannedUntil,
            timestamp: new Date().getTime()
        };
        
        // 保存禁用信息到sessionStorage
        sessionStorage.setItem('banInfo', JSON.stringify(banInfo));
        
        // 显示禁用消息
        showWarning(notification.message || '您的账号已被暂时禁用，请稍后再试');
    } else {
        // 显示一般登出消息
        showWarning(notification.message || '您已被登出，请重新登录');
    }
    
    // 断开连接并清除用户数据
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(function() {
            console.log('[DEBUG] WebSocket连接已断开');
            
            // 清理所有用户数据
            clearAllUserData();
            
            // 延迟跳转以确保消息显示
            setTimeout(() => {
                console.log('[DEBUG] 重定向到登录页面');
                window.location.href = 'index.html';
            }, 2000);
        });
    } else {
        // 如果已经断开连接，直接清理并跳转
        clearAllUserData();
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 2000);
    }
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