// 游戏页面逻辑
let stompClient = null;
let currentPlayer = null;
let currentRoomId = null;
let maxTryIoin = 1;
let gameState = {
    isHost: false,
    isReady: false,
    isMyTurn: false,
    currentPlayer: null,
    lastClaim: null,
    selectedCards: new Set(),
    soundEnabled: true
};

// 声音效果管理
const sounds = {
    cardSound: new Audio('/sounds/card.mp3'),
    dealSound: new Audio('/sounds/deal.mp3'),
    clickSound: new Audio('/sounds/click.mp3'),
    challengeSound: new Audio('/sounds/challenge.mp3'),
    victorySound: new Audio('/sounds/victory.mp3'),
    defeatSound: new Audio('/sounds/defeat.mp3'),
    notificationSound: new Audio('/sounds/notification.mp3')
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
    console.debug('游戏页面初始化开始');
    console.debug('当前URL:', window.location.href);
    
    // 初始化聊天输入框事件
    initializeChatInput();
    
    // 检查是否被禁用
    if (checkBanStatus()) {
        console.debug('检测到用户被禁用，无法进入游戏');
        return;
    }
    
    // 检查是否已登录
    try {
        const playerData = JSON.parse(localStorage.getItem('player'));
        if (!playerData || !playerData.id) {
            console.error('[DEBUG] 未找到有效的玩家信息');
            layer.msg('登录状态异常，请重新登录', {
                time: 1500,
                end: function() {
                    window.location.href = 'index.html';
                }
            });
            return;
        }

        currentPlayer = playerData;
        console.debug('当前玩家信息:', currentPlayer);

        // 获取房间ID（优先使用URL参数）
        const urlParams = new URLSearchParams(window.location.search);
        currentRoomId = urlParams.get('roomId') || localStorage.getItem('currentRoomId');
        
        console.debug('URL参数中的房间ID:', urlParams.get('roomId'));
        console.debug('localStorage中的房间ID:', localStorage.getItem('currentRoomId'));
        console.debug('最终使用的房间ID:', currentRoomId);
        
        if (!currentRoomId) {
            console.error('[DEBUG] 房间ID无效');
            layer.msg('房间ID无效，返回大厅', {
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
            time: 1500,
            end: function() {
                window.location.href = 'index.html';
            }
        });
    }
});

function connectWebSocket() {
    try {
        console.debug('开始WebSocket连接过程');
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        
        // 启用STOMP调试日志
        stompClient.debug = function(str) {
            console.log('[STOMP DEBUG]', str);
        };
        
        // 设置连接成功和失败的回调
        stompClient.connect({}, function(frame) {
            // 确保使用正确的玩家ID
            
            // 订阅游戏状态更新
            console.debug('订阅游戏状态更新');
            console.debug('状态主题:', '/topic/game/state/' + currentRoomId);
            stompClient.subscribe('/topic/game/state/' + currentRoomId, function(message) {
                try {
                    console.debug('收到游戏状态更新原始数据:', message.body);
                    const state = JSON.parse(message.body);
                    console.debug('解析后的游戏状态:', state);
                    handleGameState(state); 
                } catch (error) {
                    console.error('[DEBUG] 解析游戏状态失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅游戏通知
            console.debug('订阅游戏通知');
            console.debug('通知主题:', '/topic/game/notification/' + currentRoomId);
            stompClient.subscribe('/topic/game/notification/' + currentRoomId, function(message) {
                try {
                    console.debug('收到游戏通知原始数据:', message.body);
                    const notification = JSON.parse(message.body);
                    console.debug('解析后的游戏通知:', notification);
                    showGameNotification(notification);
                } catch (error) {
                    console.error('[DEBUG] 解析游戏通知失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅聊天消息
            console.debug('订阅聊天消息');
            console.debug('聊天主题:', '/topic/game/chat/' + currentRoomId);
            stompClient.subscribe('/topic/game/chat/' + currentRoomId, function(message) {
                try {
                    console.debug('收到聊天消息原始数据:', message.body);
                    const chatMessage = JSON.parse(message.body);
                    console.debug('解析后的聊天消息:', chatMessage);
                    
                    // 如果不是自己发送的消息，则显示（自己的消息在发送时就已显示）
                    const senderID = typeof chatMessage.playerId === 'object' ? 
                        chatMessage.playerId.id : chatMessage.playerId;
                    const currentPlayerID = typeof currentPlayer === 'object' ? 
                        currentPlayer.id : currentPlayer;
                    
                    // 确保不会重复显示自己发送的消息
                    if (senderID !== currentPlayerID) {
                        const formattedMessage = {
                            player: chatMessage.playerName || chatMessage.playerId,
                            content: chatMessage.content,
                            timestamp: chatMessage.timestamp,
                            isSelf: false
                        };
                        addChatMessage(formattedMessage);
                    }
                } catch (error) {
                    console.error('[DEBUG] 解析聊天消息失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅个人通知消息
            console.debug('订阅个人通知消息');
            stompClient.subscribe('/user/queue/notifications', function(message) {
                try {
                    console.debug('收到个人通知原始数据:', message.body);
                    const notification = JSON.parse(message.body);
                    console.debug('解析后的个人通知:', notification);
                    
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
            console.debug('订阅加入房间响应');
            stompClient.subscribe('/user/queue/joinRoom', function(message) {
                try {
                    console.debug('收到加入房间响应原始数据:', message.body);
                    const response = JSON.parse(message.body);
                    handleJoinRoomResponse(response);
                } catch (error) {
                    console.error('[DEBUG] 解析加入房间响应失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            // 订阅个人错误消息
            console.debug('订阅个人错误消息');
            stompClient.subscribe('/user/queue/errors', function(message) {
                try {
                    console.debug('收到错误消息原始数据:', message.body);
                    const response = JSON.parse(message.body);
                    console.error('[DEBUG] 解析后的错误消息:', response);
                    showError(response.message);
                } catch (error) {
                    console.error('[DEBUG] 解析错误消息失败:', error);
                    console.error('[DEBUG] 原始消息:', message.body);
                }
            });

            console.debug('所有订阅设置完成，开始尝试加入房间');
            // 尝试加入房间
            tryJoinRoom(maxTryIoin);
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
        hideLoading(); // 确保无论如何都隐藏加载提示
        return;
    }
    
    console.log(`[DEBUG] 尝试加入房间 (剩余尝试: ${attempts})`);
    
    try {
        // 确保currentPlayer是正确的格式
        const playerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        
        if (!playerId) {
            console.error('[DEBUG] 无效的玩家ID');
            showError('登录状态异常，请重新登录');
            hideLoading(); // 确保无论如何都隐藏加载提示
            setTimeout(() => {
                window.location.href = 'index.html';
            }, 1500);
            return;
        }
        
        // 确保房间ID有效 - 优先使用URL参数，其次是localStorage，最后是全局变量
        const urlParams = new URLSearchParams(window.location.search);
        let roomIdToUse = urlParams.get('roomId') || localStorage.getItem('currentRoomId') || currentRoomId;
        
        if (!roomIdToUse) {
            console.error('[DEBUG] 房间ID无效，无法加入房间');
            showError('房间ID无效，请返回大厅重新加入');
            hideLoading();
            setTimeout(() => {
                window.location.href = 'lobby.html';
            }, 1500);
            return;
        }
        
        // 更新当前使用的房间ID
        currentRoomId = roomIdToUse;
        console.debug('使用房间ID:', currentRoomId);

        // 记录请求详情用于调试
        console.debug('发送加入房间请求:', {
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
        
        // 设置检查状态的定时器
        setTimeout(() => {
            // 如果还没有收到状态更新，重试
            if (!gameState.roomId) {
                console.debug('未收到房间状态，重试加入');
                tryJoinRoom(attempts - 1);
            } else {
                // 确保如果收到房间状态但没有调用hideLoading，这里强制隐藏加载提示
                hideLoading();
                console.debug('收到房间状态，房间ID:', gameState.roomId);
                // 确保currentRoomId和gameState.roomId一致
                if (currentRoomId !== gameState.roomId) {
                    console.debug('更新currentRoomId:', gameState.roomId);
                    currentRoomId = gameState.roomId;
                }
            }
        }, 2000); // 增加等待时间到2秒
    } catch (error) {
        console.error('[DEBUG] 发送加入房间消息失败:', error);
        // 继续尝试
        setTimeout(() => tryJoinRoom(attempts - 1), 2000);
    }
}

// 添加一个新的函数在玩家加入房间后发送通知
function announcePlayerJoined() {
    if (!stompClient || !stompClient.connected) {
        console.error('[DEBUG] 无法发送玩家加入通知：WebSocket未连接');
        return;
    }
    
    try {
        // 确保玩家ID和房间ID有效
        const playerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        const roomId = gameState.roomId || currentRoomId;
        
        if (!playerId || !roomId) {
            console.error('[DEBUG] 无法发送玩家加入通知：玩家ID或房间ID无效');
            return;
        }
        
        // 创建加入通知
        const joinNotification = {
            type: "JOIN",
            roomId: roomId,
            playerId: playerId,
            playerName: typeof currentPlayer === 'object' ? currentPlayer.name : playerId,
            content: `玩家 ${typeof currentPlayer === 'object' ? currentPlayer.name : playerId} 加入了房间`,
            timestamp: new Date().getTime()
        };
        
        console.debug('发送玩家加入通知:', joinNotification);
        
        // 发送消息到特定主题
        stompClient.send(`/app/game/notification/${roomId}`, {}, JSON.stringify(joinNotification));
    } catch (error) {
        console.error('[DEBUG] 发送玩家加入通知时出错:', error);
    }
}

// 在handleJoinRoomResponse函数中调用公告函数
function handleJoinRoomResponse(response) {
    // 隐藏加载提示
    hideLoading();

    console.debug('收到加入房间响应:', response);
    
    if (response.success) {
        console.debug('成功加入房间');
        hideLoading();
        showSuccess('成功加入房间');
        
        // 确保保存房间ID
        if (response.roomId) {
            console.debug('从响应中获取房间ID:', response.roomId);
            currentRoomId = response.roomId;
            gameState.roomId = response.roomId;
            
            // 保存到localStorage，便于页面刷新后恢复
            localStorage.setItem('currentRoomId', response.roomId);
        } else if (currentRoomId) {
            console.debug('使用当前房间ID:', currentRoomId);
            // 确保gameState中也有roomId
            gameState.roomId = currentRoomId;
        } else {
            console.warn('[DEBUG] 无法获取有效的房间ID');
        }
        
        // 更新游戏状态
        if (response.gameState) {
            // 确保gameState中有roomId
            if (!response.gameState.roomId && gameState.roomId) {
                response.gameState.roomId = gameState.roomId;
            }
            updateGameState(response.gameState);
        }
        
        // 发送玩家加入通知
        setTimeout(() => announcePlayerJoined(), 500);
    } else {
        console.error('[DEBUG] 加入房间失败:', response.message);
        showError(response.message || '加入房间失败');
        hideLoading(); // 确保隐藏加载提示，即使加入失败
        setTimeout(() => {
            window.location.href = 'lobby.html';
        }, 1500);
    }
}

function handleGameState(state) {
    // 隐藏加载提示
    hideLoading();
    
    // 保存当前状态
    console.debug('处理游戏状态更新:', state);
    
    // 确保 gameState 初始化
    if (!gameState) {
        gameState = {
            roomId: null,
            roomName: null,
            status: null,
            players: [],
            readyPlayers: [],
            hand: [],
            isHost: false,
            isMyTurn: false,
            maxPlayers: 4 // 默认最大玩家数
        };
    }
    
    // 更新游戏房间ID和名称
    if (state.roomId) {
        gameState.roomId = state.roomId;
        // 存储到会话存储
        sessionStorage.setItem('currentRoomId', state.roomId);
        currentRoomId = state.roomId;
    } else if (currentRoomId && !gameState.roomId) {
        // 如果状态更新中没有roomId但currentRoomId存在，使用currentRoomId
        console.debug('状态中没有roomId，使用当前roomId:', currentRoomId);
        gameState.roomId = currentRoomId;
        state.roomId = currentRoomId; // 确保state也有正确的roomId以供后续处理
    }
    
    if (state.roomName) {
        gameState.roomName = state.roomName;
    }
    
    // 更新最大玩家数
    if (state.maxPlayers) {
        console.debug('从服务器更新最大玩家数:', state.maxPlayers);
        gameState.maxPlayers = state.maxPlayers;
    } else if (!gameState.maxPlayers) {
        gameState.maxPlayers = 4; // 设置默认值
    }
    
    // 更新游戏状态
    if (state.gameStatus) {
        gameState.status = state.gameStatus;
    }
    
    // 更新玩家列表
    if (state.players) {
        gameState.players = state.players;
    }
    
    // 更新准备玩家列表
    if (state.readyPlayers) {
        gameState.readyPlayers = state.readyPlayers;
    } else if (!gameState.readyPlayers) {
        gameState.readyPlayers = []; // 确保有一个默认的空数组
    }
    
    // 检查是否是房主
    if (state.hostId) {
        gameState.hostId = state.hostId;
        gameState.isHost = currentPlayer.id === state.hostId || currentPlayer === state.hostId;
    }

    // 更新手牌
    if (state.playerHands) {
        gameState.playerHands = state.playerHands;
    }

    // 如果是游戏中而且是当前玩家的回合，更新可用操作
    if (state.gameStatus === 'PLAYING' && state.currentPlayer === currentPlayer) {
        enablePlayerActions(state);
    } else {
        disablePlayerActions();
    }

    // 更新UI
    updateUI(state);
    
    // 更新玩家列表
    updatePlayerList(state);

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
        case 'READY':
            return '已准备';
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
    // 隐藏加载提示
    hideLoading();
    
    // 如果是JOIN类型的通知，确保房间ID是正确的
    if (notification.type === 'JOIN') {
        // 如果通知中的roomId为null但currentRoomId存在，使用当前房间ID
        if (!notification.roomId && currentRoomId) {
            console.debug('通知中的roomId为null，使用当前房间ID:', currentRoomId);
            notification.roomId = currentRoomId;
        }
        
        // 如果有玩家加入，更新房间玩家信息
        if (notification.playerId && notification.success) {
            console.debug('玩家加入房间成功:', notification.playerId);
            
            // 确保gameState有players数组
            if (!gameState.players) {
                gameState.players = [];
            }
            
            // 检查玩家是否已在列表中
            const playerExists = gameState.players.some(p => {
                if (typeof p === 'object') {
                    return p.id === notification.playerId;
                } else {
                    return p === notification.playerId;
                }
            });
            
            // 如果玩家不在列表中，添加到玩家列表
            if (!playerExists) {
                console.debug('添加新玩家到列表:', notification.playerId);
                gameState.players.push(notification.playerId);
                
                // 更新玩家列表UI
                updatePlayerList(gameState);
            }
        }
    }
    
    // 根据通知类型选择不同的提示样式
    switch (notification.type) {
        case 'ERROR':
            showError(notification.message || notification.content);
            break;
        case 'SUCCESS':
            showSuccess(notification.message || notification.content);
            break;
        case 'WARNING':
            showWarning(notification.message || notification.content);
            break;
        case 'JOIN':
            // 玩家加入特殊处理
            showInfo(notification.content || `玩家 ${notification.playerId} 加入了房间`);
            break;
        case 'LEAVE':
            // 玩家离开特殊处理
            showInfo(notification.content || `玩家 ${notification.playerId} 离开了房间`);
            
            // 如果有玩家离开，从玩家列表中移除
            if (notification.playerId && gameState.players) {
                gameState.players = gameState.players.filter(p => {
                    if (typeof p === 'object') {
                        return p.id !== notification.playerId;
                    } else {
                        return p !== notification.playerId;
                    }
                });
                
                // 更新玩家列表UI
                updatePlayerList(gameState);
            }
            break;
        default:
            showInfo(notification.message || notification.content || '系统消息');
    }
}

function showError(message) {
    // 使用 layer 插件显示错误消息
    layer.msg(message, {
        time: 2000,  // 显示2秒
        shade: [0.3, '#000'],  // 遮罩
        offset: '30%'  // 位置靠上显示
    });
}

function showSuccess(message) {
    // 使用 layer 插件显示成功消息
    layer.msg(message, {
        time: 1500,  // 显示1.5秒
        shade: [0.3, '#000'],  // 遮罩
        offset: '30%'  // 位置靠上显示
    });
}

function showInfo(message) {
    // 使用 layer 插件显示信息提示
    layer.msg(message, {
        time: 2000,  // 显示2秒
        offset: '30%'  // 位置靠上显示
    });
}

function showWarning(message) {
    // 使用 layer 插件显示警告消息
    layer.msg(message, {
        time: 2000,  // 显示2秒
        offset: '30%'  // 位置靠上显示
    });
}

function updatePlayerList(state) {
    const playerListElement = document.getElementById('playerList');
    if (!playerListElement) return;
    
    playerListElement.innerHTML = '';
    
    if (!state || !state.players) return;
    
    state.players.forEach(player => {
        // 确保player.id存在
        const playerId = player.id || (typeof player === 'string' ? player : '');
        
        const isHost = playerId === gameState.hostId;
        const isReady = gameState.readyPlayers.includes(playerId);
        const isCurrentPlayer = gameState.currentPlayer === playerId;
        
        // 确保currentPlayer正确比较
        const currentPlayerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        const isMe = playerId === currentPlayerId;
        
        // 检查是否是机器人
        const isRobot = playerId && playerId.startsWith('robot_');
        
        // 获取正确的玩家名称
        let playerName;
        if (isRobot) {
            playerName = `机器人 ${playerId.split('_')[1]}`;
        } else if (player.name) {
            playerName = player.name;
        } else if (typeof player === 'string') {
            playerName = player;
        } else {
            playerName = playerId || '未知玩家';
        }
        
        // 获取玩家手牌数量
        let cardCount = 0;
        if (state.playerHands && typeof state.playerHands === 'object') {
            const playerHand = state.playerHands[playerId];
            cardCount = playerHand ? playerHand.length : 0;
        }
        
        const playerItem = document.createElement('div');
        playerItem.className = 'player-item' + (isCurrentPlayer ? ' current-player' : '');
        
        let badges = '';
        if (isHost) badges += '<span class="badge bg-primary">房主</span> ';
        if (isReady) badges += '<span class="badge bg-success">已准备</span> ';
        if (isMe) badges += '<span class="badge bg-info">我</span> ';
        if (isRobot) badges += '<span class="badge bg-secondary">机器人</span> ';
        
        // 创建玩家信息元素
        const playerNameDiv = document.createElement('div');
        playerNameDiv.className = 'player-name';
        playerNameDiv.innerHTML = `${playerName} ${badges}`;
        
        // 创建玩家状态元素
        const playerStatusDiv = document.createElement('div');
        playerStatusDiv.className = 'player-status';
        playerStatusDiv.innerHTML = `
            <span class="badge ${isCurrentPlayer ? 'bg-warning' : 'bg-secondary'}">
                ${isCurrentPlayer ? '当前玩家' : '等待中'}
            </span>
            <span class="player-cards">手牌: ${cardCount}张</span>
        `;
        
        // 添加玩家信息和状态
        playerItem.appendChild(playerNameDiv);
        playerItem.appendChild(playerStatusDiv);
        
        // 如果是房主且当前玩家是机器人，添加删除机器人按钮
        if (gameState.isHost && isRobot) {
            const playerActionsDiv = document.createElement('div');
            playerActionsDiv.className = 'player-actions';
            
            const removeBtn = document.createElement('button');
            removeBtn.className = 'btn btn-danger btn-sm btn-robot-remove';
            removeBtn.textContent = '移除';
            removeBtn.onclick = function() {
                removeSpecificRobot(playerId);
            };
            
            playerActionsDiv.appendChild(removeBtn);
            playerItem.appendChild(playerActionsDiv);
        }
        
        playerListElement.appendChild(playerItem);
    });
}

/**
 * 渲染玩家手牌，所有卡牌一行横排，超出部分叠加，选中卡牌上推
 * @param {Array} hand 手牌数组
 */
function updateHand(hand) {
    const playerHand = document.getElementById('playerHand');
    playerHand.innerHTML = '';

    hand.forEach((card, idx) => {
        const cardElement = document.createElement('div');
        cardElement.className = `card${gameState.selectedCards.has(card) ? ' selected' : ''}`;
        // 首张卡牌不叠加
        cardElement.style.marginLeft = idx === 0 ? '0' : '-40px';

        // 处理特殊牌（大小王）
        if (card.value === '0') {
            cardElement.innerHTML = `
                <div class="rank joker">JOKER</div>
                <div class="center-icon joker">🃏</div>
            `;
        } else {
            // 确定牌面颜色（红色或黑色）
            const suitSymbol = getSuitSymbol(card.suit);
            const valueDisplay = getValueDisplay(card.value);
            const isRed = suitSymbol === '♥' || suitSymbol === '♦';
            const colorClass = isRed ? 'red' : 'black';
            cardElement.innerHTML = `
                <div class="rank ${colorClass}">${valueDisplay}</div>
                <div class="suit ${colorClass}">${suitSymbol}</div>
                <div class="center-icon ${colorClass}">${suitSymbol}</div>
            `;
        }

        // 点击切换选中状态，允许多选
        cardElement.onclick = () => {
            if (gameState.isMyTurn) {
                if (gameState.selectedCards.has(card)) {
                    gameState.selectedCards.delete(card);
                    //待实现复位
                    //updateHand(hand);
                } else {
                    gameState.selectedCards.add(card);
                }
                playSound('clickSound');
                updatePlayInputState();
            }
        };

        cardElement.dataset.cardId = card.value;
        playerHand.appendChild(cardElement);
    });

    // 更新手牌区域的计数显示
    const handCountElement = document.getElementById('handCount');
    if (handCountElement) {
        handCountElement.textContent = `手牌: ${hand.length}张`;
    }

    // 每次渲染后，联动出牌输入区
    updatePlayInputState();

    console.log(`[DEBUG] 更新了玩家手牌，共${hand.length}张`);
}

/**
 * 联动出牌输入区：有选中卡牌时，右侧输入点数和出牌按钮可用，否则禁用
 */
function updatePlayInputState() {
    // 假设输入框id为declaredValue，按钮id为playBtn
    const declaredValueInput = document.getElementById('declaredValue');
    const playBtn = document.getElementById('playBtn');
    const hasSelected = gameState.selectedCards && gameState.selectedCards.size > 0;
    if (declaredValueInput) {
        declaredValueInput.disabled = !hasSelected;
    }
    if (playBtn) {
        playBtn.disabled = !hasSelected;
    }
}

function updateCurrentPile(pile) {
    const currentPile = document.getElementById('currentPile');
    currentPile.innerHTML = '';

    if (!pile || pile.length === 0) {
        const emptyText = document.createElement('div');
        emptyText.className = 'empty-pile-text';
        emptyText.textContent = '牌堆为空';
        currentPile.appendChild(emptyText);
        return;
    }

    pile.forEach(card => {
        const cardElement = document.createElement('div');
        cardElement.className = 'card pile-card';
        
        // 处理特殊牌（大小王）
        if (card.value === '0') {
            cardElement.innerHTML = `
                <div class="rank joker">JOKER</div>
                <div class="center-icon joker">🃏</div>
            `;
        } else {
            // 确定牌面颜色（红色或黑色）
            const suitSymbol = getSuitSymbol(card.suit);
            const valueDisplay = getValueDisplay(card.value);
            const isRed = suitSymbol === '♥' || suitSymbol === '♦';
            const colorClass = isRed ? 'red' : 'black';
            cardElement.innerHTML = `
                <div class="rank ${colorClass}">${valueDisplay}</div>
                <div class="suit ${colorClass}">${suitSymbol}</div>
                <div class="center-icon ${colorClass}">${suitSymbol}</div>
            `;
        }
        
        currentPile.appendChild(cardElement);
    });
    
    console.log(`[DEBUG] 更新了当前牌堆，共${pile.length}张`);
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

    updateHand(gameState.playerHands);
}

function selectAllSameValue() {
    if (!gameState.isMyTurn) return;

    const value = document.getElementById('declaredValue').value;
    gameState.playerHands.forEach(card => {
        if (card.value === value) {
            gameState.selectedCards.add(card);
        }
    });

    updateHand(gameState.playerHands);
}

function clearSelection() {
    if (!gameState.isMyTurn) return;

    gameState.selectedCards.clear();
    updateHand(gameState.playerHands);
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
    
    // 显示出牌动画效果
    showPlayAnimation();
}

// 显示出牌动画效果
function showPlayAnimation() {
    // 创建动画容器
    const animContainer = document.createElement('div');
    animContainer.className = 'play-animation';
    animContainer.innerHTML = '<div class="play-effect">出牌!</div>';
    document.body.appendChild(animContainer);
    
    // 2秒后移除动画
    setTimeout(() => {
        animContainer.remove();
    }, 1000);
}

function pass() {
    if (!gameState.isMyTurn) return;

    // 发送过牌请求
    stompClient.send("/app/game/pass", {}, {});
}

function challenge() {
    if (!gameState.isMyTurn) return;
    
    // 发送挑战请求
    stompClient.send("/app/game/challenge", {}, JSON.stringify({}));
    
    // 播放挑战声音
    playSound('challengeSound');
    
    // 显示挑战动画
    showChallengeAnimation();
    
    // 清除所选卡牌
    clearSelection();
}

// 显示挑战动画
function showChallengeAnimation() {
    // 创建动画容器
    const animContainer = document.createElement('div');
    animContainer.className = 'play-animation';
    animContainer.innerHTML = '<div class="play-effect" style="color: #dc3545; text-shadow: 0 0 10px #dc3545, 0 0 20px #dc3545, 0 0 30px #dc3545;">质疑!</div>';
    document.body.appendChild(animContainer);
    
    // 1.5秒后移除动画
    setTimeout(() => {
        animContainer.remove();
    }, 1500);
}

function toggleReady() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket未连接');
        showError('未连接到服务器，请刷新页面重试');
        return;
    }

    console.log('切换准备状态');
    
    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "READY",
        roomId: currentRoomId,
        playerId: playerIdStr,
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

    // 获取选择的牌组数量
    const deckCount = parseInt(document.getElementById('deckCount').value) || 1;
    
    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    showLoading('正在开始游戏...');
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "START",
        roomId: currentRoomId,
        playerId: playerIdStr,
        deckCount: deckCount
    }));
}

function addRobots() {
    if (!gameState.isHost) return;

    const count = parseInt(document.getElementById('robotCount').value);
    const difficulty = document.getElementById('robotDifficulty').value;

    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;

    // 显示加载提示
    showLoading('正在添加机器人...');
    
    try {
        // 发送添加机器人请求
        stompClient.send("/app/game/robots/add", {}, JSON.stringify({
            roomId: gameState.roomId,
            playerId: playerIdStr,
            count: count,
            difficulty: difficulty
        }));
    } catch (error) {
        console.error('[DEBUG] 添加机器人失败:', error);
        showError('添加机器人失败，请重试');
        hideLoading();
    }
}

function removeRobots() {
    if (!gameState.isHost) return;

    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;

    // 显示加载提示
    showLoading('正在移除机器人...');
    
    try {
        // 发送移除机器人请求
        stompClient.send("/app/game/robots/remove", {}, JSON.stringify({
            roomId: gameState.roomId,
            playerId: playerIdStr
        }));
    } catch (error) {
        console.error('[DEBUG] 移除机器人失败:', error);
        showError('移除机器人失败，请重试');
        hideLoading();
    }
}

function sendMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    
    if (message) {
        try {
            console.debug('准备发送聊天消息:', message);
            
            // 获取当前玩家信息
            const playerData = typeof currentPlayer === 'object' ? 
                currentPlayer : 
                JSON.parse(localStorage.getItem('player'));
            
            if (!playerData) {
                console.error('[DEBUG] 发送聊天消息失败: 未找到玩家信息');
                return;
            }
            
            // 确保我们有正确的房间ID
            const roomId = gameState.roomId || currentRoomId;
            if (!roomId) {
                console.error('[DEBUG] 发送聊天消息失败: 未找到房间ID');
                return;
            }
            
            // 创建消息对象
            const chatMessage = {
                roomId: roomId,
                playerId: typeof playerData === 'object' ? playerData.id : playerData,
                playerName: typeof playerData === 'object' ? playerData.name : playerData,
                content: message,
                timestamp: new Date().getTime()
            };
            
            // 发送聊天消息到房间主题
            stompClient.send(`/app/game/chat/${roomId}`, {}, JSON.stringify(chatMessage));
            
            // 立即在本地显示消息（不等待服务器响应）
            const localMessage = {
                player: playerData.name || playerData.id || playerData,
                content: message,
                timestamp: new Date().getTime(),
                isSelf: true
            };
            addChatMessage(localMessage);
            
            // 清空输入框
            input.value = '';
            
            console.debug('聊天消息已发送');
        } catch (error) {
            console.error('[DEBUG] 发送聊天消息时出错:', error);
            showError('发送消息失败');
        }
    }
}

function addChatMessage(message) {
    const chatMessages = document.getElementById('chatMessages');
    if (!chatMessages) {
        console.error('[DEBUG] 未找到聊天消息容器');
        return;
    }
    
    try {
        console.debug('添加聊天消息:', message);
        
        // 获取当前时间
        const now = new Date();
        const timeStr = message.timestamp ? 
            new Date(message.timestamp).toLocaleTimeString() : 
            now.toLocaleTimeString();
        
        // 确保玩家名称正确显示
        const playerName = typeof message.player === 'object' ? 
            (message.player.name || message.player.id || '未知') : 
            (message.playerName || message.player || '未知');
        
        // 确定是否是当前玩家发送的消息
        const isSelf = message.isSelf || false;
        
        const messageElement = document.createElement('div');
        messageElement.className = `chat-message ${isSelf ? 'self' : ''}`;
        messageElement.innerHTML = `
            <span class="player">${playerName}</span>
            <span class="content">${message.content}</span>
            <span class="time">${timeStr}</span>
        `;
        
        chatMessages.appendChild(messageElement);
        
        // 自动滚动到底部
        chatMessages.scrollTop = chatMessages.scrollHeight;
        
        console.debug('聊天消息已添加到DOM');
    } catch (error) {
        console.error('[DEBUG] 添加聊天消息时出错:', error);
    }
}

function updateGameResult(result) {
    // 获取并重置游戏结果模态框内容
    const gameResultModal = document.getElementById('gameResultModal');
    const resultTitle = document.getElementById('gameResultTitle');
    const resultContent = document.getElementById('gameResultContent');
    
    if (!gameResultModal || !resultTitle || !resultContent) {
        console.error('[DEBUG] 游戏结果模态框不存在');
        return;
    }
    
    // 设置标题和内容
    const isWinner = result.winner === currentPlayer;
    resultTitle.textContent = isWinner ? '恭喜您赢得了游戏！' : '游戏结束';
    resultTitle.className = isWinner ? 'modal-title text-success' : 'modal-title text-info';
    
    // 添加游戏结果信息
    let resultHtml = `
        <div class="game-result ${isWinner ? 'game-result-highlight' : ''}">
            <h4 class="mb-3 ${isWinner ? 'text-success' : 'text-info'}">
                ${isWinner ? '您是本局游戏的赢家！' : `玩家 ${result.winner} 赢得了游戏`}
            </h4>
            <div class="player-results mb-3">
                <h5>游戏成绩：</h5>
                <ul class="list-group">
    `;
    
    // 添加玩家详情
    if (result.playerDetails) {
        Object.entries(result.playerDetails).forEach(([player, details]) => {
            const isCurrentPlayer = player === currentPlayer;
            resultHtml += `
                <li class="list-group-item ${isCurrentPlayer ? 'active' : ''}">
                    ${isCurrentPlayer ? '您' : player}: 
                    <span class="badge ${details.winner ? 'bg-success' : 'bg-secondary'}">
                        ${details.winner ? '获胜' : '失败'}
                    </span>
                    <span class="float-end">
                        手牌: ${details.remainingCards} | 出牌: ${details.playedCards}
                    </span>
                </li>
            `;
        });
    }
    
    resultHtml += `
                </ul>
            </div>
            <div class="text-center mt-3">
                <p>${isWinner ? '祝贺您取得胜利！' : '再接再厉，下次一定会赢！'}</p>
            </div>
        </div>
    `;
    
    resultContent.innerHTML = resultHtml;
    
    // 播放相应的声音
    playSound(isWinner ? 'victorySound' : 'defeatSound');
    
    // 显示模态框
    const bsModal = new bootstrap.Modal(gameResultModal);
    bsModal.show();
}

function restartGame() {
    // 发送重新开始游戏请求
    stompClient.send("/app/game/restart", {}, {});
}

function leaveRoom() {
    if (!stompClient || !stompClient.connected) {
        window.location.href = 'lobby.html';
        return;
    }

    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "LEAVE",
        roomId: currentRoomId,
        playerId: playerIdStr
    }));

    // 清除房间数据
    clearRoomData();
    
    // 返回大厅
    window.location.href = 'lobby.html';
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

function playSound(soundName) {
    if (!gameState.soundEnabled) return;
    
    const sound = sounds[soundName];
    if (sound) {
        // 重置声音以便可以连续播放
        sound.pause();
        sound.currentTime = 0;
        
        // 播放声音
        sound.play().catch(e => {
            console.error('[DEBUG] 播放声音失败:', e);
        });
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
            const players = state.players || gameState.players || [];
            const readyPlayers = state.readyPlayers || gameState.readyPlayers || [];
            const allReady = players.length >= 2 && readyPlayers.length === players.length;
            const isHost = state.hostId === currentPlayer || gameState.isHost;
            
            if (isHost && allReady && (state.gameStatus === 'WAITING' || state.gameStatus === 'READY')) {
                startBtn.style.display = 'block';
                startBtn.disabled = false;
            } else {
                startBtn.style.display = 'none';
            }
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
        if (gameState.playerHands) {
            updateHand(gameState.playerHands);
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
        console.debug('尝试重新连接WebSocket...');
        showWarning('正在尝试重新连接...');
        connectWebSocket();
    }
}

// 处理强制登出消息
function handleForceLogout(notification) {
    console.debug('收到强制登出通知:', notification);
    
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
                console.debug('游戏缓存已成功删除');
            };
            request.onerror = function() {
                console.error('[DEBUG] 无法删除游戏缓存');
            };
        }
    } catch (e) {
        console.error('[DEBUG] 删除游戏缓存时出错:', e);
    }
    
    // 调用其他清理函数（如果存在）
    if (typeof clearRoomData === 'function') {
        clearRoomData();
    }
    
    if (typeof clearGameCache === 'function') {
        clearGameCache();
    }
    
    // 断开WebSocket连接
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
        console.debug('WebSocket连接已断开');
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

// 处理强制离开房间通知
function handleForceRoomExit(notification) {
    console.debug('收到强制离开房间通知:', notification);
    
    // 显示消息
    showWarning(notification.message || '房间已解散，您将返回大厅');
    
    // 清除游戏数据
    clearRoomData();
    
    // 断开连接，然后返回大厅
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(function() {
            console.debug('WebSocket连接已断开，返回大厅');
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
    if (!state) {
        console.error('[DEBUG] 更新UI失败: 状态对象为空');
        return;
    }
    
    console.debug('更新UI:', state);
    
    try {
        // 更新房间信息
        const roomInfoElement = document.getElementById('roomInfo');
        if (roomInfoElement) {
            const players = state.players || [];
            // 优先使用state中的maxPlayers，其次是gameState中的，最后默认为4
            const maxPlayers = state.maxPlayers || gameState.maxPlayers || 4;
            console.debug('显示房间信息 - 当前人数/最大人数:', players.length, '/', maxPlayers);
            roomInfoElement.textContent = `房间: ${state.roomName || gameState.roomName || '游戏房间'} (${players.length}/${maxPlayers})`;
        }
        
        // 更新游戏状态
        const statusElement = document.getElementById('gameStatus');
        if (statusElement) {
            const gameStatus = state.gameStatus || gameState.status || 'WAITING';
            statusElement.textContent = getGameStatusText(gameStatus);
            statusElement.className = 'badge ' + getStatusClass(gameStatus);
        }
        
        // 更新当前玩家显示
        const currentPlayerElement = document.getElementById('currentPlayer');
        if (currentPlayerElement) {
            // 确保当前玩家名称正确显示
            const playerName = typeof currentPlayer === 'object' ? currentPlayer.id || currentPlayer.name || '未知' : currentPlayer || '未知';
            currentPlayerElement.textContent = `当前玩家: ${playerName}`;
        }
        
        // 获取游戏状态
        const gameStatus = state.gameStatus || gameState.status || 'WAITING';
        
        // 显示/隐藏房主控制区
        const hostControls = document.getElementById('hostControls');
        const robotControls = document.getElementById('robotControls');
        const readyBtn = document.getElementById('readyBtn');
        const startBtn = document.getElementById('startBtn');
        
        // 修改这里：在WAITING和READY状态下都显示相应控件
        if (gameStatus === 'WAITING' || gameStatus === 'READY') {
            // 游戏等待中或准备中，显示房主控制
            if (hostControls) {
                // 只有房主能看到房主控制区
                hostControls.style.display = gameState.isHost ? 'block' : 'none';
            }
            
            if (robotControls) {
                robotControls.style.display = gameState.isHost ? 'block' : 'none';
            }
            
            // 显示/隐藏准备按钮
            if (readyBtn) {
                readyBtn.style.display = 'block';
                
                // 更新准备按钮文本，确保readyPlayers存在
                const readyPlayers = state.readyPlayers || gameState.readyPlayers || [];
                const isReady = readyPlayers.includes(currentPlayer.id);
                readyBtn.textContent = isReady ? '取消准备' : '准备';
                readyBtn.className = 'btn ' + (isReady ? 'btn-warning' : 'btn-primary') + ' w-100 mt-2';
            }
            
            // 显示/隐藏开始游戏按钮
            if (startBtn) {
                const players = state.players || gameState.players || [];
                const readyPlayers = state.readyPlayers || gameState.readyPlayers || [];
                const allReady = readyPlayers.length === players.length && players.length >= 2;
                
                // 房主才能看到开始按钮，且只有当所有玩家都准备好时才能点击
                startBtn.style.display = gameState.isHost ? 'block' : 'none';
                startBtn.disabled = !(gameState.isHost && allReady);
            }
        } else {
            // 游戏已开始，隐藏准备按钮和房主控制
            if (readyBtn) readyBtn.style.display = 'none';
            if (hostControls) hostControls.style.display = 'none';
            if (startBtn) startBtn.style.display = 'none';
        }
        
        // 更新游戏控制面板
        const gameControls = document.getElementById('gameControls');
        if (gameControls) {
            gameControls.style.display = (gameStatus === 'PLAYING') ? 'block' : 'none';
        }
        
        // 更新当前牌堆信息
        updateCurrentPile(state.currentPile);
        
        // 更新上一个声明
        updateLastClaim(state.lastClaim);
        
        // 更新手牌和可用操作
        updateHandAndActions(state);
    } catch (error) {
        console.error('[DEBUG] 更新UI出错:', error);
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
                
                layer.msg(message, {
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

// 添加updateLastClaim函数
function updateLastClaim(claim) {
    const lastClaimElement = document.getElementById('lastClaim');
    if (!lastClaimElement) return;
    
    if (claim) {
        // 如果存在span元素，更新它的内容，否则更新整个元素的内容
        const spanElement = lastClaimElement.querySelector('span');
        if (spanElement) {
            spanElement.textContent = claim;
        } else {
            lastClaimElement.textContent = `最后声明: ${claim}`;
        }
        lastClaimElement.style.display = 'block';
    } else {
        lastClaimElement.style.display = 'none';
    }
    
    console.debug('更新最后声明:', claim);
}

// 更新手牌和可用操作
function updateHandAndActions(state) {
    // 更新手牌
    if (state.playerHands && typeof state.playerHands === 'object') {
        // 获取当前玩家ID
        const currentPlayerId = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
        // 获取当前玩家的手牌
        const currentPlayerHand = state.playerHands[currentPlayerId];
        
        if (currentPlayerHand && currentPlayerHand.length > 0) {
            updateHand(currentPlayerHand);
            
            // 判断是否是当前玩家的回合
            const isMyTurn = state.currentPlayer === currentPlayerId;
            gameState.isMyTurn = isMyTurn;
            
            // 启用或禁用玩家操作按钮
            const playBtn = document.getElementById('playBtn');
            const passBtn = document.getElementById('passBtn');
            const challengeBtn = document.getElementById('challengeBtn');
            const selectAllBtn = document.getElementById('selectAllBtn');
            const clearSelectionBtn = document.getElementById('clearSelectionBtn');
            const declaredValueInput = document.getElementById('declaredValue');
            
            if (playBtn) playBtn.disabled = !isMyTurn;
            if (passBtn) passBtn.disabled = !isMyTurn;
            if (challengeBtn) challengeBtn.disabled = !isMyTurn;
            if (selectAllBtn) selectAllBtn.disabled = !isMyTurn;
            if (clearSelectionBtn) clearSelectionBtn.disabled = !isMyTurn;
            if (declaredValueInput) declaredValueInput.disabled = !isMyTurn;
            
            // 添加高亮效果
            if (playBtn) playBtn.classList.add('btn-primary');
            if (passBtn) passBtn.classList.add('btn-primary');
            if (challengeBtn) challengeBtn.classList.add('btn-danger');
            
            // 显示轮到您出牌的提示
            if (isMyTurn) {
                showInfo('轮到您出牌了');
            }
        }
    } else {
        console.debug('没有找到手牌数据或手牌为空');
    }
    
    // 更新游戏操作区可见性
    const gameControls = document.getElementById('gameControls');
    if (gameControls) {
        gameControls.style.display = state.status === 'PLAYING' ? 'block' : 'none';
    }
    
    // 更新玩家列表以显示最新的手牌数量
    updatePlayerList(state);
}

// 启用玩家游戏操作
function enablePlayerActions(state) {
    console.debug('启用玩家操作');
    
    // 更新游戏状态
    gameState.isMyTurn = true;
    gameState.currentPlayer = state.currentPlayer;
    
    // 获取游戏操作按钮
    const playBtn = document.getElementById('playBtn');
    const passBtn = document.getElementById('passBtn');
    const challengeBtn = document.getElementById('challengeBtn');
    const selectAllBtn = document.getElementById('selectAllBtn');
    const clearSelectionBtn = document.getElementById('clearSelectionBtn');
    const declaredValueInput = document.getElementById('declaredValue');
    
    // 启用按钮
    if (playBtn) playBtn.disabled = false;
    if (passBtn) passBtn.disabled = false;
    if (challengeBtn) challengeBtn.disabled = false;
    if (selectAllBtn) selectAllBtn.disabled = false;
    if (clearSelectionBtn) clearSelectionBtn.disabled = false;
    if (declaredValueInput) declaredValueInput.disabled = false;
    
    // 添加高亮效果
    if (playBtn) playBtn.classList.add('btn-primary');
    if (passBtn) passBtn.classList.add('btn-primary');
    if (challengeBtn) challengeBtn.classList.add('btn-danger');
    
    // 显示轮到您出牌的提示
    showInfo('轮到您出牌了');
}

// 禁用玩家游戏操作
function disablePlayerActions() {
    console.debug('禁用玩家操作');
    
    // 更新游戏状态
    gameState.isMyTurn = false;
    
    // 获取游戏操作按钮
    const playBtn = document.getElementById('playBtn');
    const passBtn = document.getElementById('passBtn');
    const challengeBtn = document.getElementById('challengeBtn');
    const selectAllBtn = document.getElementById('selectAllBtn');
    const clearSelectionBtn = document.getElementById('clearSelectionBtn');
    const declaredValueInput = document.getElementById('declaredValue');
    
    // 禁用按钮
    if (playBtn) playBtn.disabled = true;
    if (passBtn) passBtn.disabled = true;
    if (challengeBtn) challengeBtn.disabled = true;
    if (selectAllBtn) selectAllBtn.disabled = true;
    if (clearSelectionBtn) clearSelectionBtn.disabled = true;
    if (declaredValueInput) declaredValueInput.disabled = true;
    
    // 移除高亮效果
    if (playBtn) playBtn.classList.remove('btn-primary');
    if (passBtn) passBtn.classList.remove('btn-primary');
    if (challengeBtn) challengeBtn.classList.remove('btn-danger');
}

// 添加初始化聊天输入框事件函数
function initializeChatInput() {
    const chatInput = document.getElementById('chatInput');
    if (chatInput) {
        // 添加回车键发送消息事件
        chatInput.addEventListener('keypress', function(event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                sendMessage();
            }
        });
        
        // 聚焦时禁用其他键盘事件
        chatInput.addEventListener('focus', function() {
            gameState.chatInputFocused = true;
        });
        
        // 失焦时启用其他键盘事件
        chatInput.addEventListener('blur', function() {
            gameState.chatInputFocused = false;
        });
        
        console.debug('聊天输入框事件初始化完成');
    } else {
        console.debug('未找到聊天输入框元素');
    }
}

function removeSpecificRobot(robotId) {
    if (!gameState.isHost) return;

    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;

    // 显示加载提示
    showLoading(`正在移除机器人 ${robotId}...`);
    
    try {
        // 发送移除特定机器人请求
        stompClient.send("/app/game/robots/remove", {}, JSON.stringify({
            roomId: gameState.roomId,
            playerId: playerIdStr,
            robotId: robotId
        }));
    } catch (error) {
        console.error('[DEBUG] 移除机器人失败:', error);
        showError('移除机器人失败，请重试');
        hideLoading();
    }
}

function updatePlayers(players) {
    const playerList = document.getElementById('playerList');
    if (!playerList) return;
    
    playerList.innerHTML = '';
    
    if (!players || players.length === 0) {
        playerList.innerHTML = '<div class="text-center p-3 text-light">暂无玩家</div>';
        return;
    }
    
    // 获取当前玩家ID
    const currentPlayerData = JSON.parse(localStorage.getItem('player'));
    const currentPlayerId = currentPlayerData ? currentPlayerData.id : '';
    
    players.forEach(player => {
        const isCurrentPlayer = player.id === currentPlayerId;
        // 识别机器人玩家（通过id前缀或type属性）
        const isRobot = player.robot === true || player.type === 'ROBOT' || (player.id && player.id.startsWith('robot_'));
        
        let statusClass = 'status-waiting';
        if (player.status === 'READY') {
            statusClass = 'status-ready';
        } else if (player.status === 'PLAYING') {
            statusClass = 'status-playing';
        } else if (player.status === 'OFFLINE') {
            statusClass = 'status-offline';
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
                <span class="status-text ${statusClass}">${getPlayerStatusText(player.status)}</span>
            </div>
            <div class="player-cards mt-2">
                ${renderPlayerCards(player)}
            </div>
        `;
        playerList.appendChild(playerElement);
    });
    
    console.debug('已更新玩家列表，包含机器人');
}

function getPlayerStatusText(status) {
    switch(status) {
        case 'ONLINE': return '在线';
        case 'READY': return '已准备';
        case 'WAITING': return '等待中';
        case 'PLAYING': return '游戏中';
        case 'OFFLINE': return '离线';
        default: return status;
    }
}

// 渲染玩家手牌
function renderPlayerCards(player) {
    const currentPlayerData = JSON.parse(localStorage.getItem('player'));
    const currentPlayerId = currentPlayerData ? currentPlayerData.id : '';
    const isCurrentPlayer = player.id === currentPlayerId;
    const isRobot = player.robot === true || player.type === 'ROBOT';
    
    // 如果没有牌，显示占位符
    if (!player.cards || player.cards.length === 0) {
        return `<div class="no-cards">暂无手牌</div>`;
    }
    
    // 根据不同情况处理牌的显示
    let cardsHtml = '';
    
    // 当前玩家显示实际牌面
    if (isCurrentPlayer) {
        player.cards.forEach(card => {
            const suitSymbol = getSuitSymbol(card.suit);
            const valueDisplay = getValueDisplay(card.value);
            cardsHtml += `
                <div class="card-item ${card.selected ? 'selected' : ''}" 
                     data-card="${valueDisplay}${suitSymbol}" 
                     onclick="toggleCardSelection(this)">
                    <div class="card-inner">
                        <span class="card-value card-${suitSymbol}">${valueDisplay}</span>
                    </div>
                </div>
            `;
        });
    } else {
        // 非当前玩家只显示牌背面
        for (let i = 0; i < player.cards.length; i++) {
            cardsHtml += `
                <div class="card-item card-back">
                    <div class="card-inner">
                        <div class="back-pattern"></div>
                    </div>
                </div>
            `;
        }
    }
    
    return cardsHtml;
}

// 修复游戏页面加载时的房间进入逻辑
function connectToGame() {
    // 从URL获取房间ID
    const urlParams = new URLSearchParams(window.location.search);
    const roomId = urlParams.get('roomId');
    
    if (!roomId) {
        showError('无效的房间ID，将返回大厅');
        setTimeout(() => {
            window.location.href = '/lobby.html';
        }, 2000);
        return;
    }
    
    // 获取玩家数据
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        showError('请先登录');
        setTimeout(() => {
            window.location.href = '/login.html';
        }, 2000);
        return;
    }
    
    // 检查玩家是否已在指定房间
    if (playerData.roomId === roomId) {
        // 已在该房间，直接连接WebSocket
        connectWebSocket(roomId);
    } else {
        // 不在该房间，尝试加入
        joinRoom(roomId, playerData.id);
    }
}

// 加入房间
function joinRoom(roomId, playerId) {
    if (!roomId || !playerId) {
        showError('房间ID或玩家ID无效');
        return;
    }
    
    showLoading('正在加入房间...');
    
    fetch(`/api/game/room/${roomId}/join`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ playerId: playerId })
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        
        if (data.error) {
            // 检查是否是"玩家已在房间中"的错误
            if (data.error.includes('已在房间') || data.error.includes('already in room')) {
                // 已在房间中，可能是页面刷新，更新本地存储并连接
                const playerData = JSON.parse(localStorage.getItem('player'));
                playerData.roomId = roomId;
                localStorage.setItem('player', JSON.stringify(playerData));
                
                // 连接WebSocket
                connectWebSocket(roomId);
            } else {
                showError(data.error);
                setTimeout(() => {
                    window.location.href = '/lobby.html';
                }, 2000);
            }
            return;
        }
        
        // 成功加入房间，更新玩家数据
        const playerData = JSON.parse(localStorage.getItem('player'));
        playerData.roomId = roomId;
        localStorage.setItem('player', JSON.stringify(playerData));
        
        // 连接WebSocket
        connectWebSocket(roomId);
    })
    .catch(error => {
        hideLoading();
        showError('加入房间失败: ' + error.message);
        console.error('加入房间失败:', error);
        
        setTimeout(() => {
            window.location.href = '/lobby.html';
        }, 2000);
    });
}

// 处理房间最后一个玩家退出时的情况
function leaveRoom() {
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.roomId) {
        window.location.href = '/lobby.html';
        return;
    }
    
    const roomId = playerData.roomId;
    const playerId = playerData.id;
    
    showLoading('正在离开房间...');
    
    fetch(`/api/game/room/${roomId}/leave`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ playerId: playerId })
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        
        // 清除玩家房间信息
        playerData.roomId = null;
        localStorage.setItem('player', JSON.stringify(playerData));
        
        showSuccess('已离开房间');
        setTimeout(() => {
            window.location.href = '/lobby.html';
        }, 1000);
    })
    .catch(error => {
        hideLoading();
        console.error('离开房间失败:', error);
        
        // 即使出错也清除房间信息并返回大厅
        playerData.roomId = null;
        localStorage.setItem('player', JSON.stringify(playerData));
        
        window.location.href = '/lobby.html';
    });
}

// 初始化聊天功能
function initializeChat() {
    console.debug('初始化聊天功能');
    
    // 初始化聊天输入框
    initializeChatInput();
    
    // 初始化聊天区域
    const chatContainer = document.getElementById('chatContainer');
    if (chatContainer) {
        // 清空聊天区域
        chatContainer.innerHTML = '';
        
        // 添加欢迎消息
        const welcomeMessage = {
            player: '系统',
            content: '欢迎来到游戏房间，请文明聊天！',
            timestamp: new Date().toISOString(),
            isSelf: false
        };
        addChatMessage(welcomeMessage);
        
        console.debug('聊天区域初始化完成');
    } else {
        console.debug('未找到聊天区域元素');
    }
}

// 页面加载时执行
window.onload = function() {
    // 连接游戏
    connectToGame();
    
    // 初始化各种监听器和UI元素
    initializeChat();
    initializeControls();
};

/**
 * 初始化游戏控制按钮和事件监听器
 */
function initializeControls() {
    console.debug('初始化游戏控制按钮');
    
    // 初始化准备按钮
    const readyBtn = document.getElementById('readyBtn');
    if (readyBtn) {
        readyBtn.addEventListener('click', function() {
            toggleReady();
            playSound('clickSound');
        });
        console.debug('准备按钮事件初始化完成');
    } else {
        console.debug('未找到准备按钮元素');
    }
    
    // 初始化开始游戏按钮
    const startBtn = document.getElementById('startBtn');
    if (startBtn) {
        startBtn.addEventListener('click', function() {
            startGame();
            playSound('clickSound');
        });
        console.debug('开始游戏按钮事件初始化完成');
    } else {
        console.debug('未找到开始游戏按钮元素');
    }
    
    // 初始化离开房间按钮
    const leaveBtn = document.getElementById('leaveBtn');
    if (leaveBtn) {
        leaveBtn.addEventListener('click', function() {
            leaveRoom();
            playSound('clickSound');
        });
        console.debug('离开房间按钮事件初始化完成');
    } else {
        console.debug('未找到离开房间按钮元素');
    }
    
    // 初始化添加机器人按钮
    const addRobotsBtn = document.getElementById('addRobotsBtn');
    if (addRobotsBtn) {
        addRobotsBtn.addEventListener('click', function() {
            addRobots();
            playSound('clickSound');
        });
        console.debug('添加机器人按钮事件初始化完成');
    }
    
    // 初始化移除机器人按钮
    const removeRobotsBtn = document.getElementById('removeRobotsBtn');
    if (removeRobotsBtn) {
        removeRobotsBtn.addEventListener('click', function() {
            removeRobots();
            playSound('clickSound');
        });
        console.debug('移除机器人按钮事件初始化完成');
    }
    
    // 初始化游戏操作按钮（出牌、过牌、质疑）
    // 这些按钮在HTML中已经设置了onclick属性，不需要在这里添加事件监听器
    
    // 初始化声音控制
    initializeSound();
    
    console.debug('游戏控制按钮初始化完成');
}

// 手牌选择相关
let selectedCards = new Set();

function toggleCardSelection(cardElement, card) {
    if (!gameState.isMyTurn) {
        showWarning('还没到您的回合');
        return;
    }
    
    if (cardElement.classList.contains('selected')) {
        cardElement.classList.remove('selected');
        cardElement.style.transform = '';
        selectedCards.delete(card);
    } else {
        cardElement.classList.add('selected');
        cardElement.style.transform = 'translateY(-20px)';
        selectedCards.add(card);
    }
    
    // 更新声明的牌数
    updateDeclaredCount();
}

function updateDeclaredCount() {
    const declareInput = document.getElementById('declareValue');
    if (declareInput) {
        declareInput.setAttribute('placeholder', `声明点数（${selectedCards.size}张）`);
    }
}

function playCards() {
    if (!gameState.isMyTurn) {
        showWarning('还没到您的回合');
        return;
    }
    
    if (gameState.selectedCards.size === 0) {
        showWarning('请选择要出的牌');
        return;
    }
    
    const declareValue = document.getElementById('declareValue').value;
    if (!declareValue) {
        showWarning('请声明点数');
        return;
    }
    
    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "PLAY",
        roomId: currentRoomId,
        playerId: playerIdStr,
        cards: Array.from(gameState.selectedCards),
        declaredValue: declareValue
    }));
    
    // 清除选择
    clearCardSelection();
}

function clearCardSelection() {
    selectedCards.clear();
    const cards = document.querySelectorAll('.card.selected');
    cards.forEach(card => {
        card.classList.remove('selected');
        card.style.transform = '';
    });
    
    // 清除声明值
    const declareInput = document.getElementById('declareValue');
    if (declareInput) {
        declareInput.value = '';
        declareInput.setAttribute('placeholder', '声明点数');
    }
}

function pass() {
    if (!gameState.isMyTurn) {
        showWarning('还没到您的回合');
        return;
    }
    
    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "PASS",
        roomId: currentRoomId,
        playerId: playerIdStr
    }));
    
    // 清除选择
    clearCardSelection();
}

function challenge() {
    if (!gameState.isMyTurn) {
        showWarning('还没到您的回合');
        return;
    }
    
    // 确保发送玩家ID为字符串
    const playerIdStr = typeof currentPlayer === 'object' ? currentPlayer.id : currentPlayer;
    
    stompClient.send("/app/game/action", {}, JSON.stringify({
        type: "CHALLENGE",
        roomId: currentRoomId,
        playerId: playerIdStr
    }));
    
    // 清除选择
    clearCardSelection();
}

// 更新手牌显示
function updateHandCards(cards) {
    const handContainer = document.getElementById('playerHand');
    if (!handContainer) return;
    
    handContainer.innerHTML = '';
    
    cards.forEach(card => {
        const cardElement = document.createElement('div');
        const suitSymbol = getSuitSymbol(card.suit);
        const valueDisplay = getValueDisplay(card.value);
        cardElement.className = 'card';
        cardElement.innerHTML = `
            <div class="card-inner">
                <div class="card-face">
                    <span class="card-value">${valueDisplay}</span>
                    <span class="card-suit ${card.suit.toLowerCase()}">${suitSymbol}</span>
                </div>
            </div>
        `;
        
        cardElement.onclick = () => toggleCardSelection(cardElement, card);
        handContainer.appendChild(cardElement);
    });
}

function getSuitSymbol(suit) {
    switch(suit) {
        case 'HEARTS': return '♥';
        case 'DIAMONDS': return '♦';
        case 'CLUBS': return '♣';
        case 'SPADES': return '♠';
        default: return suit;
    }
}

function getValueDisplay(value) {
    const displays = {
        1: 'A',
        11: 'J',
        12: 'Q',
        13: 'K'
    };
    return displays[value] || value;
}
// 更新当前出牌区域
function updateCurrentPlay(cards, declaredValue) {
    const playArea = document.getElementById('currentPlay');
    if (!playArea) return;
    
    playArea.innerHTML = '';
    
    if (cards && cards.length > 0) {
        const cardsHtml = cards.map(card => `
            <div class="card played">
                <div class="card-inner">
                    <div class="card-face">
                        <span class="card-value">${getValueDisplay(card.value)}</span>
                        <span class="card-suit ${card.suit.toLowerCase()}">${getSuitSymbol(card.suit)}</span>
                    </div>
                </div>
            </div>
        `).join('');
        
        playArea.innerHTML = `
            <div class="current-play-info">
                <span>当前出牌：${cards.length}张</span>
                <span>声明：${declaredValue}</span>
            </div>
            <div class="played-cards">
                ${cardsHtml}
            </div>
        `;
    } else {
        playArea.innerHTML = '<div class="no-cards">暂无出牌</div>';
    }
}

// 更新房间状态显示
function updateRoomStatus(gameState) {
    if (!gameState) return;
    
    const roomStatusContainer = document.getElementById('roomStatus');
    if (!roomStatusContainer) return;
    
    const playerData = JSON.parse(localStorage.getItem('player'));
    const isHost = playerData && gameState.hostId === playerData.id;
    
    // 获取准备玩家数量和总玩家数量
    const readyPlayersCount = gameState.readyPlayers ? gameState.readyPlayers.length : 0;
    const totalPlayers = gameState.players ? gameState.players.length : 0;
    
    // 创建房主控制面板
    let hostControlsHtml = '';
    if (isHost && gameState.status === 'WAITING') {
        hostControlsHtml = `
            <div id="hostControls" class="host-controls mt-3">
                <button class="btn btn-secondary me-2" onclick="addRobot()">添加机器人</button>
                <button class="btn btn-danger me-2" onclick="removeAllRobots()">移除所有机器人</button>
                ${readyPlayersCount === totalPlayers && totalPlayers >= 2 ? 
                    '<button class="btn btn-success" onclick="startGame()">开始游戏</button>' : 
                    '<button class="btn btn-success" disabled>等待玩家准备 (${readyPlayersCount}/${totalPlayers})</button>'}
            </div>
        `;
    }
    
    roomStatusContainer.innerHTML = `
        <div class="room-info">
            <h3>房间 ${gameState.roomId}</h3>
            <div class="status-badges">
                <span class="badge ${gameState.status === 'WAITING' ? 'bg-info' : 'bg-success'}">
                    ${gameState.status === 'WAITING' ? '等待中' : '游戏中'}
                </span>
                <span class="badge bg-primary">
                    ${readyPlayersCount}/${totalPlayers} 已准备
                </span>
            </div>
        </div>
        ${hostControlsHtml}
    `;
}

// 添加机器人
function addRobot() {
    if (!stompClient || !stompClient.connected) return;
    
    const roomId = getRoomId();
    if (!roomId) return;
    
    stompClient.send("/app/game/addRobot", {}, JSON.stringify({
        roomId: roomId,
        difficulty: "NORMAL" // 可以添加难度选择UI
    }));
}

// 移除所有机器人
function removeAllRobots() {
    if (!stompClient || !stompClient.connected) return;
    
    const roomId = getRoomId();
    if (!roomId) return;
    
    stompClient.send("/app/game/removeRobots", {}, JSON.stringify({
        roomId: roomId
    }));
}
/*
// 开始游戏
function startGame() {
    if (!stompClient || !stompClient.connected) return;
    
    const roomId = getRoomId();
    if (!roomId) return;
    
    stompClient.send("/app/game/start", {}, JSON.stringify({
        roomId: roomId
    }));
}
*/

// 获取房间ID
function getRoomId() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('roomId');
}
