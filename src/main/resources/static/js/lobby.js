// 大厅页面逻辑
let stompClient = null;
let currentPlayer = null;
let connectionAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 3;
let currentRoom = null;

// 页面加载完成后初始化
$(document).ready(function() {
    // 检查登录状态
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        window.location.href = 'login.html';
        return;
    }

    // 设置玩家名称
    currentPlayer = playerData.id;
    $('#playerName').text(playerData.name || playerData.id);

    // 连接WebSocket
    connectWebSocket();

    // 绑定聊天输入框事件
    $('#chatInput').keypress(function(e) {
        if (e.which == 13) {
            sendMessage();
        }
    });
});

// 连接WebSocket
function connectWebSocket() {
    try {
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null; // 禁用debug日志

        const connectHeaders = {
            login: currentPlayer
        };

        stompClient.connect(connectHeaders, 
            function(frame) {
                console.debug('WebSocket连接成功');
                connectionAttempts = 0;
                setupSubscriptions();
                loadRoomList();
            },
            function(error) {
                console.error('WebSocket连接失败:', error);
                handleConnectionError();
            }
        );
    } catch (error) {
        console.error('WebSocket连接错误:', error);
        handleConnectionError();
    }
}

// 处理连接错误
function handleConnectionError() {
    connectionAttempts++;
    if (connectionAttempts < MAX_RECONNECT_ATTEMPTS) {
        console.debug(`尝试重新连接 (${connectionAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
        setTimeout(connectWebSocket, 2000);
    } else {
        showError('无法连接到服务器，请刷新页面重试');
    }
}

// 设置WebSocket订阅
function setupSubscriptions() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket未连接，无法设置订阅');
        return;
    }

    // 订阅房间列表更新
    stompClient.subscribe('/topic/rooms', function(message) {
        updateRoomList(JSON.parse(message.body));
    });

    // 订阅玩家列表更新
    stompClient.subscribe('/topic/players', function(message) {
        updatePlayerList(JSON.parse(message.body));
    });

    // 订阅系统消息
    stompClient.subscribe('/topic/system', function(message) {
        handleSystemMessage(JSON.parse(message.body));
    });

    // 订阅聊天消息
    stompClient.subscribe('/topic/chat', function(message) {
        displayChatMessage(JSON.parse(message.body));
    });

    // 订阅个人消息
    stompClient.subscribe('/user/queue/private', function(message) {
        handlePrivateMessage(JSON.parse(message.body));
    });
}

// 加载房间列表
function loadRoomList() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket未连接');
        return;
    }
    stompClient.send("/app/rooms/list", {}, {});
}

// 更新房间列表
function updateRoomList(rooms) {
    const roomList = $('#roomList');
    roomList.empty();

    if (!rooms || rooms.length === 0) {
        roomList.html('<div class="text-center text-light p-3">暂无房间</div>');
        return;
    }

    rooms.forEach(room => {
        if (room.status !== 'PLAYING') {
            const roomElement = $(`
                <div class="room-item">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h5 class="text-light mb-1">房间 ${room.id}</h5>
                            <small class="text-light">
                                玩家数: ${room.players.length}/${room.maxPlayers}
                                ${room.hasRobots ? ' (包含机器人)' : ''}
                            </small>
                        </div>
                        <button class="btn btn-primary btn-sm" onclick="joinRoom('${room.id}')">
                            加入房间
                        </button>
                    </div>
                </div>
            `);
            roomList.append(roomElement);
        }
    });
}

// 更新玩家列表
function updatePlayerList(players) {
    const playerList = $('#playerList');
    playerList.empty();

    if (!players || players.length === 0) {
        playerList.html('<div class="text-center text-light p-3">暂无玩家</div>');
        return;
    }

    players.forEach(player => {
        const isCurrentPlayer = player.id === currentPlayer;
        const playerElement = $(`
            <div class="player-item">
                <div class="d-flex justify-content-between align-items-center">
                    <span class="text-light">${player.name || player.id}</span>
                    ${isCurrentPlayer ? '<span class="badge bg-info ms-2">我</span>' : ''}
                </div>
            </div>
        `);
        playerList.append(playerElement);
    });
}

// 创建房间
function createRoom() {
    if (!stompClient || !stompClient.connected) {
        showError('未连接到服务器');
        return;
    }

    stompClient.send("/app/rooms/create", {}, JSON.stringify({
        hostId: currentPlayer,
        maxPlayers: 6 // 默认6人房
    }));
}

// 加入房间
function joinRoom(roomId) {
    if (!stompClient || !stompClient.connected) {
        showError('未连接到服务器');
        return;
    }

    stompClient.send("/app/rooms/join", {}, JSON.stringify({
        roomId: roomId,
        playerId: currentPlayer
    }));
}

// 发送聊天消息
function sendMessage() {
    const input = $('#chatInput');
    const message = input.val().trim();
    
    if (!message) return;
    
    if (!stompClient || !stompClient.connected) {
        showError('未连接到服务器');
        return;
    }

    stompClient.send("/app/chat", {}, JSON.stringify({
        sender: currentPlayer,
        content: message,
        timestamp: new Date().getTime()
    }));

    input.val('');
}

// 显示聊天消息
function displayChatMessage(message) {
    const chatMessages = $('#chatMessages');
    const messageElement = $(`
        <div class="chat-message">
            <span class="chat-sender">${message.sender}:</span>
            <span class="chat-content">${message.content}</span>
        </div>
    `);
    chatMessages.append(messageElement);
    chatMessages.scrollTop(chatMessages[0].scrollHeight);
}

// 处理系统消息
function handleSystemMessage(message) {
    switch (message.type) {
        case 'ROOM_CREATED':
            window.location.href = `game.html?roomId=${message.roomId}`;
            break;
        case 'ROOM_JOINED':
            window.location.href = `game.html?roomId=${message.roomId}`;
            break;
        case 'ERROR':
            showError(message.content);
            break;
    }
}

// 处理私人消息
function handlePrivateMessage(message) {
    switch (message.type) {
        case 'KICK':
            handleKick(message);
            break;
        case 'BAN':
            handleBan(message);
            break;
    }
}

// 处理踢出
function handleKick(message) {
    localStorage.setItem('kicked_out', 'true');
    localStorage.setItem('kicked_time', Date.now().toString());
    localStorage.setItem('kicked_reason', message.reason || 'KICK');
    window.location.href = 'login.html?reason=' + encodeURIComponent('您已被踢出游戏');
}

// 处理封禁
function handleBan(message) {
    const banInfo = {
        bannedUntil: new Date().getTime() + (message.duration || 30) * 1000
    };
    sessionStorage.setItem('banInfo', JSON.stringify(banInfo));
    window.location.href = 'login.html?reason=' + encodeURIComponent('您已被封禁');
}

// 退出登录
function logout() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
    localStorage.clear();
    sessionStorage.clear();
    window.location.href = 'login.html';
}

// 显示错误消息
function showError(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message, {icon: 2});
    } else {
        alert(message);
    }
}

// 显示成功消息
function showSuccess(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message, {icon: 1});
    } else {
        alert(message);
    }
}

// 显示警告消息
function showWarning(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message, {icon: 3});
    } else {
        alert(message);
    }
}

// 显示信息消息
function showInfo(message) {
    if (typeof layer !== 'undefined') {
        layer.msg(message, {icon: 4});
    } else {
        alert(message);
    }
}

// 显示加载提示
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

// 隐藏加载提示
function hideLoading() {
    if (typeof layer !== 'undefined' && window.loadingIndex) {
        layer.close(window.loadingIndex);
        window.loadingIndex = null;
    }
}

// 检查登录状态
function checkLoginStatus() {
    const playerData = JSON.parse(localStorage.getItem('player'));
    if (!playerData || !playerData.id) {
        // 未登录，跳转到登录页
        window.location.href = '/login.html';
    }
}

// 处理玩家离线
function handlePlayerOffline(playerId) {
    console.debug('处理玩家离线:', playerId);
    
    // 从本地玩家列表中移除
    const playerList = $('#playerList');
    if (playerList) {
        const playerElement = playerList.find(`[data-player-id="${playerId}"]`);
        if (playerElement) {
            playerElement.remove();
        }
    }
    
    // 从房间列表中更新该玩家的状态
    const roomList = $('#roomList');
    if (roomList) {
        const roomElements = roomList.find('.room-item');
        roomElements.each(function() {
            const playerElements = $(this).find('.player-item');
            playerElements.each(function() {
                if ($(this).data('player-id') === playerId) {
                    $(this).find('.status-text').addClass('status-offline');
                    $(this).find('.status-text').text('离线');
                }
            });
        });
    }
}

// 更新在线玩家列表
function updateOnlinePlayers(players) {
    console.debug('更新在线玩家列表:', players);
    
    const playerList = $('#playerList');
    if (!playerList) return;
    
    // 获取当前玩家ID
    const currentPlayerData = JSON.parse(localStorage.getItem('player'));
    const currentPlayerId = currentPlayerData ? currentPlayerData.id : '';
    
    // 清空现有列表
    playerList.empty();
    
    if (!players || players.length === 0) {
        playerList.html('<div class="text-center text-light p-3">暂无在线玩家</div>');
        return;
    }
    
    // 添加在线玩家
    players.forEach(playerId => {
        const playerElement = $(`
            <div class="player-item">
                <div class="d-flex justify-content-between align-items-center">
                    <span class="text-light">${playerId}</span>
                    ${playerId === currentPlayerId ? '<span class="badge bg-info ms-2">我</span>' : ''}
                </div>
            </div>
        `);
        playerList.append(playerElement);
    });
}

// 添加聊天功能
function setupChat() {
    try {
        // 检查是否已存在聊天容器
        let chatContainer = $('.chat-container');
        if (chatContainer.length > 0) {
            console.debug('聊天容器已存在');
            return;
        }
        
        // 检查主容器是否存在，如果不存在则创建
        let mainContainer = $('.container');
        if (!mainContainer) {
            mainContainer = $('<div>');
            mainContainer.addClass('container');
            $('body').append(mainContainer);
        }
        
        // 创建聊天容器
        chatContainer = $('<div>');
        chatContainer.addClass('chat-container');
        chatContainer.html(`
            <div class="chat-messages" id="chatMessages"></div>
            <div class="chat-input-container">
                <input type="text" class="chat-input" id="chatInput" placeholder="输入消息...">
                <button class="chat-send-btn" onclick="sendMessage()">发送</button>
            </div>
        `);
        
        // 将聊天容器添加到主容器
        mainContainer.append(chatContainer);
        
        // 添加回车发送功能
        const chatInput = $('#chatInput');
        if (chatInput) {
            chatInput.keypress(function(e) {
                if (e.key === 'Enter') {
                    sendMessage();
                }
            });
        }
        
        console.debug('聊天容器设置完成');
    } catch (error) {
        console.error('设置聊天容器时出错:', error);
    }
}

// 添加聊天消息订阅
function setupChatSubscription() {
    if (!stompClient || !stompClient.connected) return;
    
    stompClient.subscribe('/topic/chat', function(message) {
        displayChatMessage(JSON.parse(message.body));
    });
}

// 初始化大厅
function initializeLobby() {
    // 移除重复的按钮和元素
    const duplicateButtons = $('.room-actions');
    if (duplicateButtons.length > 1) {
        duplicateButtons.each(function() {
            if ($(this).index() > 0) {
                $(this).remove();
            }
        });
    }
    
    // 加载房间列表
    loadRoomList();
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

// 更新UI
function updateUI() {
    const playerData = JSON.parse(localStorage.getItem('player'));
    const playerName = playerData ? playerData.name : '游客';
    $('#playerName').text(playerName);
    
    // 修复房间操作区的显示逻辑
    const roomInfo = $('#roomInfo');
    const createRoomBtn = $('#createRoomBtn');
    const refreshRoomsBtn = $('#refreshRoomsBtn');
    const hostActions = $('#hostActions');
    
    if (playerData && playerData.roomId) {
        // 已在房间中
        if (createRoomBtn) createRoomBtn.hide();
        if (refreshRoomsBtn) refreshRoomsBtn.hide();
        
        // 房主特殊权限
        if (hostActions && currentRoom && playerData.id === currentRoom.hostId) {
            hostActions.show();
        } else if (hostActions) {
            hostActions.hide();
        }
    } else {
        // 未在房间中
        if (createRoomBtn) createRoomBtn.show();
        if (refreshRoomsBtn) refreshRoomsBtn.show();
        if (hostActions) hostActions.hide();
    }
} 