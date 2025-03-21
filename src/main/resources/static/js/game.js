// 游戏页面逻辑
let stompClient = null;
let currentPlayer = null;
let currentRoomId = null;
let gameState = {
    isHost: false,
    isReady: false,
    isMyTurn: false,
    currentPlayer: null,
    lastClaim: null,
    selectedCards: new Set(),
    soundEnabled: true
};

// 添加加载提示相关函数
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

document.addEventListener('DOMContentLoaded', function() {
    console.log('[DEBUG] 游戏页面初始化开始');
    console.log('[DEBUG] 当前URL:', window.location.href);
    
    // 检查是否被禁用
    if (checkBanStatus()) {
        console.log('[DEBUG] 检测到用户被禁用，无法进入游戏');
        return;
    }
    
    // 检查是否已登录
    try {
        const playerData = JSON.parse(localStorage.getItem('player'));
        if (!playerData || !playerData.id) {
            console.error('[DEBUG] 未找到有效的玩家信息');
            layer.msg('登录状态异常，请重新登录', {
                icon: 2,
                time: 1500,
                end: function() {
                    window.location.href = 'index.html';
                }
            });
            return;
        }

        currentPlayer = playerData;
        console.log('[DEBUG] 当前玩家信息:', currentPlayer);

        // 获取房间ID（优先使用URL参数）
        const urlParams = new URLSearchParams(window.location.search);
        currentRoomId = urlParams.get('roomId') || localStorage.getItem('currentRoomId');
        
        console.log('[DEBUG] URL参数中的房间ID:', urlParams.get('roomId'));
        console.log('[DEBUG] localStorage中的房间ID:', localStorage.getItem('currentRoomId'));
        console.log('[DEBUG] 最终使用的房间ID:', currentRoomId);
        
        if (!currentRoomId) {
            console.error('[DEBUG] 房间ID无效');
            layer.msg('房间ID无效，返回大厅', {
                icon: 2,
                time: 1500,
                end: function() {
                    window.location.href = 'lobby.html';
                }
            });
            return;
        }

        // 保存当前会话信息
        sessionStorage.setItem('gameSession', JSON.stringify({
            playerId: playerData.id,
            playerName: playerData.name,
            roomId: currentRoomId,
            timestamp: new Date().getTime()
        }));

        // 显示加载提示
        showLoading('正在连接到游戏房间...');

        // 连接WebSocket
        connectWebSocket();

        // 设置自动重连检查
        setInterval(checkConnectionStatus, 5000);
        
    } catch (error) {
        console.error('[DEBUG] 游戏页面初始化失败:', error);
        layer.msg('初始化失败，请重新登录', {
            icon: 2,
            time: 1500,
            end: function() {
                window.location.href = 'index.html';
            }
        });
    }
});

function connectWebSocket() {
    try {
        console.log('[DEBUG] 开始WebSocket连接过程');
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        
        // 启用STOMP调试日志
        stompClient.debug = function(str) {
            console.log('[STOMP DEBUG]', str);
        };
        
        // 确保使用正确的玩家ID
        const playerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        
        // 添加认证信息
        const connectHeaders = {
            login: playerId
        };
        
        console.log('[DEBUG] 配置STOMP连接');
        console.log('[DEBUG] 连接headers:', connectHeaders);
        
        stompClient.connect(connectHeaders, function(frame) {
            console.log('[DEBUG] WebSocket连接成功');
            console.log('[DEBUG] 连接帧:', frame);
            
            // 隐藏加载提示
            hideLoading();
            
            // 订阅游戏状态更新
            console.log('[DEBUG] 订阅游戏状态更新');
            console.log('[DEBUG] 状态主题:', '/topic/game/state/' + currentRoomId);
            stompClient.subscribe('/topic/game/state/' + currentRoomId, function(message) {
                try {
                    console.log('[DEBUG] 收到游戏状态更新原始数据:', message.body);
                    const state = JSON.parse(message.body);
                    console.log('[DEBUG] 解析后的游戏状态:', state);
                    handleGameState(state);
                } catch (error) {
                    console.error('[DEBUG] 解析游戏状态失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅游戏通知
            console.log('[DEBUG] 订阅游戏通知');
            console.log('[DEBUG] 通知主题:', '/topic/game/notification/' + currentRoomId);
            stompClient.subscribe('/topic/game/notification/' + currentRoomId, function(message) {
                try {
                    console.log('[DEBUG] 收到游戏通知原始数据:', message.body);
                    const notification = JSON.parse(message.body);
                    console.log('[DEBUG] 解析后的游戏通知:', notification);
                    showGameNotification(notification);
                } catch (error) {
                    console.error('[DEBUG] 解析游戏通知失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅个人通知消息
            console.log('[DEBUG] 订阅个人通知消息');
            stompClient.subscribe('/user/queue/notifications', function(message) {
                try {
                    console.log('[DEBUG] 收到个人通知原始数据:', message.body);
                    const notification = JSON.parse(message.body);
                    console.log('[DEBUG] 解析后的个人通知:', notification);
                    
                    // 处理强制登出消息
                    if (notification.type === 'FORCE_LOGOUT') {
                        handleForceLogout(notification);
                    } else if (notification.type === 'FORCE_ROOM_EXIT') {
                        // 处理强制离开房间通知
                        handleForceRoomExit(notification);
                    } else {
                        // 其他类型通知显示为游戏通知
                        showGameNotification(notification);
                    }
                } catch (error) {
                    console.error('[DEBUG] 解析个人通知失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅加入房间响应
            console.log('[DEBUG] 订阅加入房间响应');
            stompClient.subscribe('/user/queue/joinRoom', function(message) {
                try {
                    console.log('[DEBUG] 收到加入房间响应原始数据:', message.body);
                    const response = JSON.parse(message.body);
                    handleJoinRoomResponse(response);
                } catch (error) {
                    console.error('[DEBUG] 解析加入房间响应失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅个人错误消息
            console.log('[DEBUG] 订阅个人错误消息');
            stompClient.subscribe('/user/queue/errors', function(message) {
                try {
                    console.log('[DEBUG] 收到错误消息原始数据:', message.body);
                    const response = JSON.parse(message.body);
                    console.error('[DEBUG] 解析后的错误消息:', response);
                    showError(response.message);
                } catch (error) {
                    console.error('[DEBUG] 解析错误消息失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            console.log('[DEBUG] 所有订阅设置完成，开始尝试加入房间');
            // 尝试加入房间
            tryJoinRoom(3);

        }, function(error) {
            console.error('[DEBUG] WebSocket连接失败:', error);
            showError('连接服务器失败，请刷新页面重试');
            hideLoading();
            
            // 显示重试按钮
            const container = document.createElement('div');
            container.className = 'text-center mt-3';
            container.innerHTML = `
                <p class="text-danger">连接失败</p>
                <button onclick="window.location.reload()" class="btn btn-primary mt-2">重试</button>
                <button onclick="window.location.href='lobby.html'" class="btn btn-secondary mt-2 ms-2">返回大厅</button>
            `;
            document.body.appendChild(container);
        });
    } catch (error) {
        console.error('[DEBUG] WebSocket连接过程出错:', error);
        showError('连接服务器失败，请刷新页面重试');
        hideLoading();
    }
}

// 添加尝试加入房间函数
function tryJoinRoom(attempts) {
    if (attempts <= 0) {
        console.error('[DEBUG] 加入房间失败，已达最大尝试次数');
        showError('加入房间失败，请返回大厅后重试');
        hideLoading();
        return;
    }
    
    console.log(`[DEBUG] 尝试加入房间 (剩余尝试: ${attempts})`);
    
    try {
        // 确保currentPlayer是正确的格式
        const playerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        
        if (!playerId) {
            console.error('[DEBUG] 无效的玩家ID');
            showError('登录状态异常，请重新登录');
            setTimeout(() => {
                window.location.href = 'index.html';
            }, 1500);
            return;
        }

        console.log('[DEBUG] 发送加入房间请求:', {
            type: "JOIN",
            roomId: currentRoomId,
            playerId: playerId
        });
        
        // 发送加入房间消息
        stompClient.send("/app/game/action", {}, JSON.stringify({
            type: "JOIN",
            roomId: currentRoomId,
            playerId: playerId
        }));
        
        // 如果还有尝试次数，继续尝试
        setTimeout(() => {
            // 如果还没有收到状态更新，重试
            if (!gameState.roomId) {
                console.log('[DEBUG] 未收到房间状态，重试加入');
                tryJoinRoom(attempts - 1);
            }
        }, 2000); // 增加等待时间到2秒
    } catch (error) {
        console.error('[DEBUG] 发送加入房间消息失败:', error);
        // 继续尝试
        setTimeout(() => tryJoinRoom(attempts - 1), 2000);
    }
}

// 添加一个新的函数来处理加入房间的响应
function handleJoinRoomResponse(response) {
    console.log('[DEBUG] 收到加入房间响应:', response);
    
    if (response.success) {
        console.log('[DEBUG] 成功加入房间');
        hideLoading();
        showSuccess('成功加入房间');
        
        // 更新游戏状态
        if (response.gameState) {
            updateGameState(response.gameState);
        }
    } else {
        console.error('[DEBUG] 加入房间失败:', response.message);
        showError(response.message || '加入房间失败');
        setTimeout(() => {
            window.location.href = 'lobby.html';
        }, 1500);
    }
}

function handleGameState(state) {
    console.log('收到游戏状态更新:', state);

    // 更新全局游戏状态
    gameState = {
        ...gameState,
        roomId: state.roomId,
        roomName: state.roomName,
        status: state.status,
        gameStatus: state.gameStatus,
        currentPlayer: state.currentPlayer,
        hostId: state.hostId,
        players: state.players,
        readyPlayers: state.readyPlayers,
        maxPlayers: state.maxPlayers
    };

    // 更新手牌
    if (state.hand) {
        gameState.hand = state.hand;
    }

    // 更新UI
    updateUI(state);
    
    // 更新玩家列表
    updatePlayerList(state);
    
    // 如果是游戏中而且是当前玩家的回合，更新可用操作
    if (state.gameStatus === 'PLAYING' && state.currentPlayer === currentPlayer) {
        enablePlayerActions(state);
    } else {
        disablePlayerActions();
    }
}

function updateGameState(state) {
    try {
        console.log('更新游戏状态:', state);
        
        // 更新游戏状态
        gameState = {
            ...gameState,
            ...state,
            isHost: state.hostId === currentPlayer,
            isMyTurn: state.currentPlayer === currentPlayer
        };

        // 更新房间信息
        const maxPlayers = state.maxPlayers || 4; // 如果maxPlayers未定义，使用默认值4
        document.getElementById('roomInfo').textContent = `房间: ${state.roomName || '游戏房间'} (${state.players.length}/${maxPlayers})`;
        document.getElementById('gameStatus').textContent = getGameStatusText(state.status);
        
        // 更新玩家列表
        updatePlayerList(state);
        
        // 更新游戏界面
        updateGameUI();
        
        // 显示当前回合玩家
        if (state.currentPlayer) {
            const isMyTurn = state.currentPlayer === currentPlayer;
            showInfo(isMyTurn ? '轮到您出牌了' : `轮到 ${state.currentPlayer} 出牌`);
        }
    } catch (error) {
        console.error('更新游戏状态失败:', error);
        showError('更新游戏状态失败');
    }
}

/**
 * 获取游戏状态的文本表示
 * @param {string} status 游戏状态
 * @returns {string} 状态文本
 */
function getGameStatusText(status) {
    switch (status) {
        case 'WAITING':
            return '等待中';
        case 'PLAYING':
            return '游戏中';
        case 'FINISHED':
            return '已结束';
        default:
            return '未知状态';
    }
}

/**
 * 获取状态的CSS类
 * @param {string} status 游戏状态
 * @returns {string} CSS类
 */
function getStatusClass(status) {
    switch (status) {
        case 'WAITING':
            return 'bg-secondary';
        case 'PLAYING':
            return 'bg-success';
        case 'FINISHED':
            return 'bg-danger';
        default:
            return 'bg-secondary';
    }
}

function showGameNotification(notification) {
    // 根据通知类型选择不同的提示样式
    switch (notification.type) {
        case 'ERROR':
            showError(notification.message);
            break;
        case 'SUCCESS':
            showSuccess(notification.message);
            break;
        case 'WARNING':
            showWarning(notification.message);
            break;
        default:
            showInfo(notification.message);
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

function updatePlayerList(state) {
    const playerListElement = document.getElementById('playerList');
    if (!playerListElement) return;
    
    playerListElement.innerHTML = '';
    
    if (!state || !state.players) return;
    
    state.players.forEach(player => {
        const isHost = player.id === gameState.hostId;
        const isReady = gameState.readyPlayers.includes(player.id);
        const isCurrentPlayer = gameState.currentPlayer === player.id;
        const isMe = player.id === currentPlayer;
        
        // 检查是否是机器人
        const isRobot = player.id && player.id.startsWith('robot_');
        const playerName = isRobot ? `机器人 ${player.id.split('_')[1]}` : player.id;
        
        const playerItem = document.createElement('div');
        playerItem.className = 'player-item' + (isCurrentPlayer ? ' current-player' : '');
        
        let badges = '';
        if (isHost) badges += '<span class="badge bg-primary">房主</span> ';
        if (isReady) badges += '<span class="badge bg-success">已准备</span> ';
        if (isMe) badges += '<span class="badge bg-info">我</span> ';
        if (isRobot) badges += '<span class="badge bg-secondary">机器人</span> ';
        
        playerItem.innerHTML = `
            <div class="player-name">
                ${playerName} ${badges}
            </div>
            <div class="player-status">
                <span class="badge ${isCurrentPlayer ? 'bg-warning' : 'bg-secondary'}">
                    ${isCurrentPlayer ? '当前玩家' : '等待中'}
                </span>
            </div>
            <div class="player-cards">手牌: ${player.cardCount || 0}张</div>
        `;
        
        playerListElement.appendChild(playerItem);
    });
}

function updateHand(hand) {
    const playerHand = document.getElementById('playerHand');
    playerHand.innerHTML = '';

    hand.forEach(card => {
        const cardElement = document.createElement('div');
        cardElement.className = `card ${gameState.selectedCards.has(card.id) ? 'selected' : ''}`;
        cardElement.innerHTML = `
            <span class="lt">${card.value}</span>
            <span class="cm">${card.suit}</span>
            <span class="rb">${card.value}</span>
        `;
        cardElement.onclick = () => toggleCardSelection(card.id);
        playerHand.appendChild(cardElement);
    });
}

function updateCurrentPile(pile) {
    const currentPile = document.getElementById('currentPile');
    currentPile.innerHTML = '';

    pile.forEach(card => {
        const cardElement = document.createElement('div');
        cardElement.className = 'card pile-card';
        cardElement.innerHTML = `
            <span class="lt">${card.value}</span>
            <span class="cm">${card.suit}</span>
            <span class="rb">${card.value}</span>
        `;
        currentPile.appendChild(cardElement);
    });
}

function updateHistory(history) {
    const historyList = document.getElementById('historyList');
    historyList.innerHTML = '';

    history.forEach(record => {
        const recordElement = document.createElement('div');
        recordElement.className = 'history-item';
        recordElement.innerHTML = `
            <span class="player">${record.player}</span>
            <span class="action">${record.action}</span>
            <span class="claim">${record.claim}</span>
        `;
        historyList.appendChild(recordElement);
    });
}

function toggleCardSelection(cardId) {
    if (!gameState.isMyTurn) return;

    if (gameState.selectedCards.has(cardId)) {
        gameState.selectedCards.delete(cardId);
    } else {
        gameState.selectedCards.add(cardId);
    }

    updateHand(gameState.hand);
}

function selectAllSameValue() {
    if (!gameState.isMyTurn) return;

    const value = document.getElementById('declaredValue').value;
    gameState.hand.forEach(card => {
        if (card.value === value) {
            gameState.selectedCards.add(card.id);
        }
    });

    updateHand(gameState.hand);
}

function clearSelection() {
    if (!gameState.isMyTurn) return;

    gameState.selectedCards.clear();
    updateHand(gameState.hand);
}

function playCards() {
    if (!gameState.isMyTurn || gameState.selectedCards.size === 0) return;

    const value = document.getElementById('declaredValue').value;
    
    // 发送出牌请求
    stompClient.send("/app/game/play", {}, JSON.stringify({
        cardIds: Array.from(gameState.selectedCards),
        declaredValue: value
    }));

    // 清除选择
    clearSelection();
    playSound('cardSound');
}

function pass() {
    if (!gameState.isMyTurn) return;

    // 发送过牌请求
    stompClient.send("/app/game/pass", {}, {});
}

function challenge() {
    if (!gameState.isMyTurn) return;

    // 发送质疑请求
    stompClient.send("/app/game/challenge", {}, {});
    playSound('challengeSound');
}

function toggleReady() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
        return;
    }

    console.log('切换准备状态');
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "READY",
        roomId: currentRoomId,
        playerId: currentPlayer,
        ready: !gameState.isReady
    }));
}

function startGame() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
        return;
    }

    if (!gameState.isHost) {
        console.error('非房主无法开始游戏');
        showError('只有房主可以开始游戏');
        return;
    }

    console.log('开始游戏');
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "START",
        roomId: currentRoomId,
        playerId: currentPlayer
    }));
}

function addRobots() {
    if (!gameState.isHost) return;

    const count = parseInt(document.getElementById('robotCount').value);
    const difficulty = document.getElementById('robotDifficulty').value;

    // 发送添加机器人请求
    stompClient.send("/app/game/robots/add", {}, JSON.stringify({
        roomId: gameState.roomId,
        playerId: currentPlayer,
        count: count,
        difficulty: difficulty
    }));
}

function removeRobots() {
    if (!gameState.isHost) return;

    // 发送移除机器人请求
    stompClient.send("/app/game/robots/remove", {}, JSON.stringify({
        roomId: gameState.roomId,
        playerId: currentPlayer
    }));
}

function sendMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    
    if (message) {
        // 发送聊天消息
        stompClient.send("/app/game/chat", {}, JSON.stringify({
            content: message
        }));
        input.value = '';
    }
}

function addChatMessage(message) {
    const chatMessages = document.getElementById('chatMessages');
    const messageElement = document.createElement('div');
    messageElement.className = `chat-message ${message.player === currentPlayer ? 'self' : ''}`;
    messageElement.innerHTML = `
        <span class="player">${message.player}:</span>
        <span class="content">${message.content}</span>
    `;
    chatMessages.appendChild(messageElement);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function showGameResult(result) {
    const modal = new bootstrap.Modal(document.getElementById('gameResultModal'));
    const rankingList = document.getElementById('playerRankingFinal');
    
    rankingList.innerHTML = '';
    result.ranking.forEach((player, index) => {
        const playerElement = document.createElement('div');
        playerElement.className = 'ranking-item';
        playerElement.innerHTML = `
            <span class="rank">${index + 1}</span>
            <span class="player">${player.name}</span>
        `;
        rankingList.appendChild(playerElement);
    });

    modal.show();
    playSound('winSound');
}

function restartGame() {
    // 发送重新开始游戏请求
    stompClient.send("/app/game/restart", {}, {});
}

function leaveRoom() {
    if (confirm('确定要离开房间吗？')) {
        try {
            console.log('开始离开房间流程');
            
            if (stompClient && stompClient.connected) {
                console.log('发送离开房间消息');
                showInfo('正在离开房间...');
                
                // 获取当前房间ID
                const roomId = gameState.roomId || currentRoomId;
                
                // 获取当前玩家信息
                const playerData = JSON.parse(localStorage.getItem('player'));
                const playerId = playerData ? playerData.id : currentPlayer;
                
                // 确保playerId是字符串而不是对象
                const playerIdStr = typeof playerId === 'object' ? playerId.id : playerId;
                
                console.log(`准备离开房间: ${roomId}, 玩家: ${playerIdStr}`);
                
                // 发送离开房间消息 - 使用专用端点
                stompClient.send("/app/rooms/leave", {}, JSON.stringify({
                    roomId: roomId,
                    playerId: playerIdStr
                }));
                
                // 清除房间相关数据
                clearRoomData();
                
                // 延迟一下以确保服务器处理完成
                setTimeout(() => {
                    // 断开WebSocket连接
                    stompClient.disconnect(() => {
                        console.log('WebSocket连接已断开，返回大厅');
                        showSuccess('已成功离开房间');
                        setTimeout(() => {
                            window.location.href = 'lobby.html';
                        }, 500);
                    });
                }, 500);
            } else {
                console.log('WebSocket未连接，直接返回大厅');
                
                // 清除房间相关数据
                clearRoomData();
                
                window.location.href = 'lobby.html';
            }
        } catch (error) {
            console.error('离开房间时发生错误:', error);
            showError('离开房间时出错，将返回大厅');
            
            // 确保即使发生错误也清理数据
            clearRoomData();
            
            setTimeout(() => {
                window.location.href = 'lobby.html';
            }, 1500);
        }
    }
}

// 清理房间相关数据
function clearRoomData() {
    console.log('开始清理房间数据和相关缓存');
    
    // 清除房间ID和会话信息
    localStorage.removeItem('currentRoomId');
    sessionStorage.removeItem('gameSession');
    
    // 清除临时房间会话数据
    sessionStorage.removeItem('lastCreateRoomRequest');
    sessionStorage.removeItem('lastJoinRoomRequest');
    
    // 重置游戏状态
    gameState = {
        roomId: null,
        roomName: null,
        hostId: null,
        status: null,
        players: [],
        readyPlayers: [],
        currentPlayer: null,
        hand: [],
        pile: [],
        history: []
    };
    
    // 清除所有游戏相关临时数据
    const gameKeys = [
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
    gameKeys.forEach(key => {
        localStorage.removeItem(key);
        sessionStorage.removeItem(key);
    });
    
    // 重置变量
    currentRoomId = null;
    
    console.log('房间数据清理完成');
}

function initializeSound() {
    // 初始化音效
    const sounds = {
        cardSound: document.getElementById('cardSound'),
        winSound: document.getElementById('winSound'),
        loseSound: document.getElementById('loseSound'),
        challengeSound: document.getElementById('challengeSound')
    };

    // 添加音效开关按钮
    const soundButton = document.createElement('button');
    soundButton.className = 'sound-toggle';
    soundButton.onclick = () => toggleSound();
    soundButton.innerHTML = gameState.soundEnabled ? '🔊' : '🔇';
    document.body.appendChild(soundButton);
}

function toggleSound() {
    gameState.soundEnabled = !gameState.soundEnabled;
    const soundButton = document.querySelector('.sound-toggle');
    soundButton.innerHTML = gameState.soundEnabled ? '🔊' : '🔇';
}

function playSound(soundId) {
    if (!gameState.soundEnabled) return;
    
    const sound = document.getElementById(soundId);
    if (sound) {
        sound.currentTime = 0;
        sound.play();
    }
}

function updateGameUI() {
    try {
        // 更新准备按钮状态
        const readyBtn = document.getElementById('readyBtn');
        if (readyBtn) {
            if (gameState.status === 'WAITING') {
                readyBtn.style.display = 'inline-block';
                readyBtn.textContent = gameState.isReady ? '取消准备' : '准备';
                readyBtn.className = `btn ${gameState.isReady ? 'btn-secondary' : 'btn-primary'} w-100 mt-2`;
            } else {
                readyBtn.style.display = 'none';
            }
        }

        // 更新房主控制面板
        const hostControls = document.getElementById('hostControls');
        if (hostControls) {
            hostControls.style.display = gameState.isHost ? 'block' : 'none';
        }

        // 更新开始游戏按钮状态
        const startBtn = document.getElementById('startBtn');
        if (startBtn) {
            const canStart = gameState.isHost && 
                           gameState.status === 'WAITING' && 
                           gameState.players.length >= 2 &&
                           gameState.players.every(player => gameState.readyPlayers.includes(player));
            startBtn.style.display = canStart ? 'inline-block' : 'none';
        }

        // 更新游戏操作按钮状态
        const gameControls = document.getElementById('gameControls');
        if (gameControls) {
            gameControls.style.display = gameState.status === 'PLAYING' ? 'block' : 'none';
            
            // 更新出牌和过牌按钮状态
            const playBtn = document.getElementById('playBtn');
            const passBtn = document.getElementById('passBtn');
            if (playBtn && passBtn) {
                const canPlay = gameState.isMyTurn && gameState.status === 'PLAYING';
                playBtn.disabled = !canPlay;
                passBtn.disabled = !canPlay;
            }
        }

        // 更新玩家手牌
        if (gameState.hand) {
            updateHand(gameState.hand);
        }

        // 更新当前牌堆
        if (gameState.currentPile) {
            updateCurrentPile(gameState.currentPile);
        }

        // 更新游戏历史记录
        if (gameState.history) {
            updateHistory(gameState.history);
        }
    } catch (error) {
        console.error('更新游戏界面失败:', error);
        showError('更新游戏界面失败');
    }
}

// 添加连接状态检查函数
function checkConnectionStatus() {
    if (!stompClient || !stompClient.connected) {
        console.warn('[DEBUG] 检测到WebSocket连接断开');
        
        // 获取会话信息
        const gameSession = JSON.parse(sessionStorage.getItem('gameSession'));
        if (!gameSession) {
            console.error('[DEBUG] 未找到游戏会话信息');
            showError('连接断开，请重新进入游戏');
            setTimeout(() => {
                window.location.href = 'lobby.html';
            }, 1500);
            return;
        }
        
        // 检查会话是否过期（30分钟）
        const now = new Date().getTime();
        if (now - gameSession.timestamp > 30 * 60 * 1000) {
            console.warn('[DEBUG] 游戏会话已过期');
            showError('游戏会话已过期，请重新进入');
            setTimeout(() => {
                window.location.href = 'lobby.html';
            }, 1500);
            return;
        }
        
        // 尝试重新连接
        console.log('[DEBUG] 尝试重新连接WebSocket...');
        showWarning('正在尝试重新连接...');
        connectWebSocket();
    }
}

// 处理强制登出消息
function handleForceLogout(notification) {
    console.log('[DEBUG] 收到强制登出通知:', notification);
    
    // 显示消息
    showWarning(notification.message || '您已被登出，请重新登录');
    
    // 清除游戏数据
    clearRoomData();
    
    // 断开连接并清除用户数据
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(function() {
            console.log('[DEBUG] WebSocket连接已断开');
            
            // 清理所有本地存储数据
            localStorage.removeItem('player');
            localStorage.removeItem('currentRoomId');
            sessionStorage.clear();
            
            // 延迟跳转以确保消息显示
            setTimeout(() => {
                console.log('[DEBUG] 重定向到登录页面');
                window.location.href = 'index.html';
            }, 2000);
        });
    } else {
        // 如果已经断开连接，直接清理并跳转
        localStorage.removeItem('player');
        localStorage.removeItem('currentRoomId');
        sessionStorage.clear();
        
        setTimeout(() => {
            window.location.href = 'index.html';
        }, 2000);
    }
}

// 处理强制离开房间通知
function handleForceRoomExit(notification) {
    console.log('[DEBUG] 收到强制离开房间通知:', notification);
    
    // 显示消息
    showWarning(notification.message || '房间已解散，您将返回大厅');
    
    // 清除游戏数据
    clearRoomData();
    
    // 断开连接，然后返回大厅
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(function() {
            console.log('[DEBUG] WebSocket连接已断开，返回大厅');
            setTimeout(() => {
                window.location.href = 'lobby.html';
            }, 1500);
        });
    } else {
        // 如果已经断开连接，直接返回大厅
        setTimeout(() => {
            window.location.href = 'lobby.html';
        }, 1500);
    }
}

function updateUI(state) {
    // 更新房间信息
    document.getElementById('roomInfo').textContent = `房间: ${state.roomName || '游戏房间'} (${state.players.length}/${state.maxPlayers || 4})`;
    
    // 更新游戏状态
    const statusElement = document.getElementById('gameStatus');
    statusElement.textContent = getGameStatusText(state.gameStatus);
    statusElement.className = 'badge ' + getStatusClass(state.gameStatus);
    
    // 更新当前玩家显示
    document.getElementById('currentPlayer').textContent = `当前玩家: ${currentPlayer}`;
    
    // 检查是否是房主
    gameState.isHost = currentPlayer === state.hostId;
    
    // 显示/隐藏准备按钮和房主控制
    const readyBtn = document.getElementById('readyBtn');
    const hostControls = document.getElementById('hostControls');
    const startBtn = document.getElementById('startBtn');
    
    if (state.gameStatus === 'WAITING') {
        // 显示房主控制面板（如果是房主）
        hostControls.style.display = gameState.isHost ? 'block' : 'none';
        
        // 显示/隐藏机器人控制面板
        const robotControls = hostControls.querySelector('.robot-controls');
        if (robotControls) {
            // 在游戏未开始且是房主的情况下显示机器人控制
            robotControls.style.display = gameState.isHost ? 'block' : 'none';
        }
        
        // 显示/隐藏准备按钮
        readyBtn.style.display = 'block';
        
        // 更新准备按钮文本
        const isReady = state.readyPlayers.includes(currentPlayer);
        readyBtn.textContent = isReady ? '取消准备' : '准备';
        readyBtn.className = 'btn ' + (isReady ? 'btn-warning' : 'btn-primary') + ' w-100 mt-2';
        
        // 显示/隐藏开始游戏按钮
        const allReady = state.readyPlayers.length === state.players.length && state.players.length >= 2;
        startBtn.style.display = (gameState.isHost && allReady) ? 'block' : 'none';
    } else {
        // 游戏已开始，隐藏准备按钮和房主控制
        readyBtn.style.display = 'none';
        hostControls.style.display = 'none';
        startBtn.style.display = 'none';
    }
    
    // 更新游戏控制面板
    if (state.gameStatus === 'PLAYING') {
        document.getElementById('gameControls').style.display = 'block';
    } else {
        document.getElementById('gameControls').style.display = 'none';
    }
    
    // 更新当前牌堆信息
    updateCurrentPile(state.currentPile);
    
    // 更新上一个声明
    updateLastClaim(state.lastClaim);
    
    // 更新手牌和可用操作
    updateHandAndActions(state);
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
                
                layer.msg(message, {
                    icon: 2,
                    time: 3000,
                    end: function() {
                        window.location.href = 'index.html';
                    }
                });
                
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