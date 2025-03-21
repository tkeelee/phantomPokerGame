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

document.addEventListener('DOMContentLoaded', function() {
    console.log('[DEBUG] 游戏页面初始化开始');
    console.log('[DEBUG] 当前URL:', window.location.href);
    
    // 检查是否已登录
    currentPlayer = localStorage.getItem('playerName');
    console.log('[DEBUG] 当前玩家:', currentPlayer);
    
    if (!currentPlayer) {
        console.warn('[DEBUG] 未登录，重定向到登录页面');
        window.location.href = 'index.html';
        return;
    }

    // 获取房间ID
    const urlParams = new URLSearchParams(window.location.search);
    currentRoomId = urlParams.get('roomId') || localStorage.getItem('currentRoomId');
    console.log('[DEBUG] URL参数中的房间ID:', urlParams.get('roomId'));
    console.log('[DEBUG] localStorage中的房间ID:', localStorage.getItem('currentRoomId'));
    console.log('[DEBUG] 最终使用的房间ID:', currentRoomId);
    
    if (!currentRoomId) {
        console.error('[DEBUG] 房间ID无效');
        showError('房间ID无效');
        setTimeout(() => {
            console.log('[DEBUG] 重定向到大厅页面');
            window.location.href = 'lobby.html';
        }, 2000);
        return;
    }

    console.log('[DEBUG] 初始化游戏页面');
    console.log('[DEBUG] 玩家:', currentPlayer);
    console.log('[DEBUG] 房间:', currentRoomId);

    // 显示加载提示
    const loadingIndicator = document.createElement('div');
    loadingIndicator.id = 'loadingIndicator';
    loadingIndicator.className = 'loading-indicator';
    loadingIndicator.innerHTML = `
        <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">加载中...</span>
        </div>
        <p class="mt-2">正在连接游戏房间...</p>
    `;
    document.body.appendChild(loadingIndicator);

    // 显示玩家名称
    const playerElement = document.getElementById('currentPlayer');
    if (playerElement) {
        console.log('[DEBUG] 设置当前玩家名称显示');
        playerElement.textContent = currentPlayer;
    } else {
        console.error('[DEBUG] 未找到currentPlayer元素');
    }

    // 连接WebSocket
    console.log('[DEBUG] 开始连接WebSocket');
    connectWebSocket();

    // 初始化音效
    console.log('[DEBUG] 初始化音效');
    initializeSound();
});

function connectWebSocket() {
    try {
        console.log('[DEBUG] 开始WebSocket连接过程');
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        
        // 禁用STOMP调试日志
        stompClient.debug = null;

        console.log('[DEBUG] 配置STOMP连接');
        stompClient.connect({}, function(frame) {
            console.log('[DEBUG] WebSocket连接成功');
            console.log('[DEBUG] 连接帧:', frame);
            
            // 隐藏加载提示
            const loadingIndicator = document.getElementById('loadingIndicator');
            if (loadingIndicator) {
                console.log('[DEBUG] 移除加载提示');
                loadingIndicator.remove();
            }
            
            // 订阅游戏状态更新
            console.log('[DEBUG] 订阅游戏状态更新');
            console.log('[DEBUG] 状态主题:', '/topic/game/state/' + currentRoomId);
            stompClient.subscribe('/topic/game/state/' + currentRoomId, function(message) {
                try {
                    console.log('[DEBUG] 收到游戏状态更新原始数据:', message.body);
                    const state = JSON.parse(message.body);
                    console.log('[DEBUG] 解析后的游戏状态:', state);
                    updateGameState(state);
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

            // 订阅聊天消息
            console.log('[DEBUG] 订阅聊天消息');
            console.log('[DEBUG] 聊天主题:', '/topic/game/chat/' + currentRoomId);
            stompClient.subscribe('/topic/game/chat/' + currentRoomId, function(message) {
                try {
                    console.log('[DEBUG] 收到聊天消息原始数据:', message.body);
                    const chatMsg = JSON.parse(message.body);
                    console.log('[DEBUG] 解析后的聊天消息:', chatMsg);
                    addChatMessage(chatMsg);
                } catch (error) {
                    console.error('[DEBUG] 解析聊天消息失败:', error);
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

            console.log('[DEBUG] 发送加入房间消息');
            // 发送加入房间消息
            stompClient.send("/app/game/action", {}, JSON.stringify({
                type: "JOIN",
                roomId: currentRoomId,
                playerId: currentPlayer
            }));

        }, function(error) {
            console.error('[DEBUG] WebSocket连接失败:', error);
            showError('连接服务器失败，请刷新页面重试');
            // 显示重试按钮
            const loadingIndicator = document.getElementById('loadingIndicator');
            if (loadingIndicator) {
                loadingIndicator.innerHTML = `
                    <div class="text-center">
                        <p class="text-danger">连接失败</p>
                        <button onclick="window.location.reload()" class="btn btn-primary mt-2">重试</button>
                        <button onclick="window.location.href='lobby.html'" class="btn btn-secondary mt-2 ms-2">返回大厅</button>
                    </div>
                `;
            }
        });
    } catch (error) {
        console.error('[DEBUG] WebSocket连接过程出错:', error);
        showError('连接服务器失败，请刷新页面重试');
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
        document.getElementById('roomInfo').textContent = `房间: ${state.roomName || '游戏房间'} (${state.players.length}/${state.maxPlayers})`;
        document.getElementById('gameStatus').textContent = getGameStatusText(state.status);
        
        // 更新玩家列表
        updatePlayerList(state.players);
        
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

function getGameStatusText(status) {
    switch(status) {
        case 'WAITING': return '等待中';
        case 'PLAYING': return '游戏中';
        case 'FINISHED': return '已结束';
        default: return '未知状态';
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

function updatePlayerList(players) {
    const playerList = document.getElementById('playerList');
    playerList.innerHTML = '';

    players.forEach(player => {
        const playerElement = document.createElement('div');
        playerElement.className = 'player-item';
        playerElement.innerHTML = `
            <span>${player.name}</span>
            ${player.isHost ? '<span class="badge bg-primary">房主</span>' : ''}
            ${player.isReady ? '<span class="badge bg-success">已准备</span>' : ''}
            ${player.name === gameState.currentPlayer ? '<span class="badge bg-warning">当前回合</span>' : ''}
        `;
        playerList.appendChild(playerElement);
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
        count: count,
        difficulty: difficulty
    }));
}

function removeRobots() {
    if (!gameState.isHost) return;

    // 发送移除机器人请求
    stompClient.send("/app/game/robots/remove", {}, {});
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
            console.log('开始清理房间信息');
            
            // 清除房间相关数据
            clearRoomData();
            
            if (stompClient && stompClient.connected) {
                console.log('发送离开房间消息');
                showInfo('正在离开房间...');
                
                // 发送离开房间消息
                stompClient.send("/app/game/action", {}, JSON.stringify({
                    type: "LEAVE",
                    roomId: currentRoomId,
                    playerId: currentPlayer
                }));
                
                // 断开WebSocket连接
                stompClient.disconnect(() => {
                    console.log('WebSocket连接已断开，返回大厅');
                    showSuccess('已成功离开房间');
                    setTimeout(() => {
                        window.location.href = 'lobby.html';
                    }, 1000);
                });
            } else {
                console.log('WebSocket未连接，直接返回大厅');
                window.location.href = 'lobby.html';
            }
        } catch (error) {
            console.error('离开房间时发生错误:', error);
            showError('离开房间失败，将返回大厅');
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
    console.log('清理房间数据');
    // 清除房间ID
    localStorage.removeItem('currentRoomId');
    currentRoomId = null;
    
    // 重置游戏状态
    gameState = {
        isHost: false,
        isReady: false,
        isMyTurn: false,
        currentPlayer: null,
        lastClaim: null,
        selectedCards: new Set(),
        soundEnabled: gameState.soundEnabled // 保留声音设置
    };
    
    // 清除游戏相关的临时数据
    const gameData = ['gameHand', 'gamePile', 'gameHistory'];
    gameData.forEach(key => {
        localStorage.removeItem(key);
    });
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
                readyBtn.style.display = 'block';
                readyBtn.textContent = gameState.isReady ? '取消准备' : '准备';
                readyBtn.className = `btn ${gameState.isReady ? 'btn-secondary' : 'btn-primary'}`;
            } else {
                readyBtn.style.display = 'none';
            }
        }

        // 更新开始游戏按钮状态
        const startBtn = document.getElementById('startBtn');
        if (startBtn) {
            const canStart = gameState.isHost && 
                           gameState.status === 'WAITING' && 
                           gameState.players.length >= 2 &&
                           gameState.players.every(player => gameState.readyPlayers.includes(player));
            startBtn.style.display = canStart ? 'block' : 'none';
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