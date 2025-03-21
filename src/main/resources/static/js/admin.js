// 管理员控制台脚本
let stompClient = null;
let adminPassword = null; // 管理员密码
let selectedRoomId = null; // 当前选中的房间ID
let pendingAction = null; // 待确认的动作

// 系统数据
const systemData = {
    rooms: {},      // 房间数据
    players: {},    // 玩家数据
    serverInfo: {}  // 服务器信息
};

document.addEventListener('DOMContentLoaded', function() {
    // 验证管理员身份
    checkAdminAuth();
    
    // 初始化事件监听
    setupEventListeners();
});

// 验证管理员身份
function checkAdminAuth() {
    // 检查是否已经有存储的管理员密码
    adminPassword = localStorage.getItem('adminPassword');
    
    if (!adminPassword) {
        // 如果没有密码，则提示输入
        promptForPassword();
    } else {
        // 有密码，尝试连接
        connect();
    }
}

// 提示输入密码
function promptForPassword() {
    // 简单提示输入密码
    adminPassword = prompt('请输入管理员密码:');
    if (adminPassword) {
        localStorage.setItem('adminPassword', adminPassword);
        connect();
    } else {
        // 如果取消输入，跳转回首页
        window.location.href = '/index.html';
    }
}

// 连接WebSocket
function connect() {
    if (stompClient !== null) {
        disconnect();
    }
    
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // 禁用调试输出
    
    stompClient.connect({}, function(frame) {
        // 连接成功
        document.getElementById('connectionStatus').textContent = '已连接';
        document.getElementById('connectionStatus').className = 'badge bg-success';
        
        addLog('WebSocket连接成功', 'success');
        
        // 加载数据
        loadAllData();
        
        // 订阅更新
        stompClient.subscribe('/topic/admin/rooms', function(message) {
            const rooms = JSON.parse(message.body);
            updateRoomData(rooms);
        });
        
        stompClient.subscribe('/topic/admin/players', function(message) {
            const players = JSON.parse(message.body);
            updatePlayerData(players);
        });
        
        stompClient.subscribe('/topic/admin/system', function(message) {
            const serverInfo = JSON.parse(message.body);
            updateSystemInfo(serverInfo);
        });
    }, function(error) {
        // 连接失败
        document.getElementById('connectionStatus').textContent = '连接失败';
        document.getElementById('connectionStatus').className = 'badge bg-danger';
        
        addLog('WebSocket连接失败: ' + error, 'error');
    });
}

// 加载所有数据
function loadAllData() {
    // 请求管理员数据
    stompClient.send("/app/admin/request-data-ws", {}, JSON.stringify({
        requestType: "initial"
    }));
    
    // 立即请求系统信息
    stompClient.send("/app/admin/system-info", {}, JSON.stringify({
        timestamp: new Date().getTime()
    }));
    
    addLog("请求系统数据更新", 'info');
}

// 更新房间数据
function updateRoomData(rooms) {
    systemData.rooms = {};
    
    // 将数组转换为对象便于查找
    rooms.forEach(room => {
        // 确保所有必要的字段都有值，避免未定义错误
        room.name = room.name || '未命名房间';
        room.hostId = room.hostId || '无房主';
        room.playerCount = room.playerCount || room.players ? room.players.length : 0;
        room.maxPlayers = room.maxPlayers || 4;
        room.status = room.status || 'WAITING';
        
        systemData.rooms[room.id] = room;
    });
    
    // 更新UI
    renderRoomList();
    document.getElementById('roomCount').textContent = `${Object.keys(systemData.rooms).length}个房间`;
    document.getElementById('totalRoomCount').textContent = Object.keys(systemData.rooms).length;
    
    addLog(`房间数据已更新，共 ${Object.keys(systemData.rooms).length} 个房间`, 'success');
}

// 更新玩家数据
function updatePlayerData(players) {
    systemData.players = {};
    
    // 将数组转换为对象便于查找
    players.forEach(player => {
        systemData.players[player.id] = player;
    });
    
    // 更新UI
    renderPlayerList();
    document.getElementById('playerCount').textContent = `${Object.keys(systemData.players).length}个玩家`;
    document.getElementById('totalPlayerCount').textContent = Object.keys(systemData.players).length;
    
    addLog(`玩家数据已更新，共 ${Object.keys(systemData.players).length} 个玩家`, 'success');
}

// 更新系统信息
function updateSystemInfo(serverInfo) {
    systemData.serverInfo = serverInfo;
    
    // 更新UI
    document.getElementById('serverStartTime').textContent = serverInfo.startTime ? new Date(serverInfo.startTime).toLocaleString() : '未知';
    document.getElementById('connectionCount').textContent = serverInfo.connectionCount;
    document.getElementById('totalRoomCount').textContent = Object.keys(systemData.rooms).length;
    document.getElementById('totalPlayerCount').textContent = Object.keys(systemData.players).length;
    
    addLog(`系统信息已更新`, 'success');
}

// 渲染房间列表
function renderRoomList() {
    const roomList = document.getElementById('roomList');
    roomList.innerHTML = '';
    
    // 按照房间ID排序
    const sortedRooms = Object.values(systemData.rooms).sort((a, b) => {
        return a.id.localeCompare(b.id);
    });
    
    if (sortedRooms.length === 0) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = `<td colspan="7" class="text-center">暂无房间</td>`;
        roomList.appendChild(emptyRow);
        return;
    }
    
    sortedRooms.forEach(room => {
        const tr = document.createElement('tr');
        
        // 创建时间（从房间ID中提取）
        const timestamp = room.id.split('_')[1];
        const createTime = timestamp ? new Date(parseInt(timestamp)).toLocaleString() : '未知';
        
        // 确保属性存在
        const roomName = room.name || '未命名房间';
        const hostId = room.hostId || '无房主';
        const playerCount = room.playerCount || 0;
        const maxPlayers = room.maxPlayers || 4;
        const status = room.status || 'WAITING';
        
        // 状态样式
        const statusClass = status === 'WAITING' ? 'status-waiting' : 
                            status === 'PLAYING' ? 'status-playing' : 'status-finished';
        
        // 状态文本
        const statusText = status === 'WAITING' ? '等待中' : 
                          status === 'PLAYING' ? '游戏中' : '已结束';
        
        tr.innerHTML = `
            <td>${room.id}</td>
            <td>${roomName}</td>
            <td>${hostId}</td>
            <td>${playerCount}/${maxPlayers}</td>
            <td><span class="${statusClass}">${statusText}</span></td>
            <td>${createTime}</td>
            <td>
                <button class="btn btn-sm btn-primary btn-action" onclick="showRoomDetail('${room.id}')">详情</button>
                <button class="btn btn-sm btn-danger btn-action" onclick="confirmDissolveRoom('${room.id}')">解散</button>
            </td>
        `;
        
        roomList.appendChild(tr);
    });
}

// 渲染玩家列表
function renderPlayerList() {
    const playerList = document.getElementById('playerList');
    playerList.innerHTML = '';
    
    // 按照玩家ID排序
    const sortedPlayers = Object.values(systemData.players).sort((a, b) => {
        return a.id.localeCompare(b.id);
    });
    
    if (sortedPlayers.length === 0) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = `<td colspan="5" class="text-center">暂无在线玩家</td>`;
        playerList.appendChild(emptyRow);
        return;
    }
    
    sortedPlayers.forEach(player => {
        const tr = document.createElement('tr');
        
        // 状态样式
        const statusClass = player.status === 'ONLINE' ? 'status-online' : 
                            player.status === 'PLAYING' ? 'status-playing' : 'status-offline';
        
        // 状态文本
        const statusText = player.status === 'ONLINE' ? '在线' : 
                          player.status === 'PLAYING' ? '游戏中' : '离线';
        
        // 获取玩家所在房间（如果有）
        let roomInfo = '无';
        if (player.roomId) {
            const room = systemData.rooms[player.roomId];
            if (room) {
                roomInfo = `${room.name} (${room.id})`;
            } else {
                roomInfo = player.roomId;
            }
        }
        
        // 活动时间
        const lastActiveTime = player.lastActiveTime ? new Date(player.lastActiveTime).toLocaleString() : '未知';
        
        tr.innerHTML = `
            <td>${player.id}</td>
            <td><span class="${statusClass}">${statusText}</span></td>
            <td>${roomInfo}</td>
            <td>${lastActiveTime}</td>
            <td>
                <button class="btn btn-sm btn-warning btn-action" onclick="confirmKickPlayer('${player.id}')">踢出游戏</button>
                ${player.roomId ? `<button class="btn btn-sm btn-danger btn-action" onclick="confirmKickFromRoom('${player.id}', '${player.roomId}')">踢出房间</button>` : ''}
            </td>
        `;
        
        playerList.appendChild(tr);
    });
}

// 显示房间详情
function showRoomDetail(roomId) {
    selectedRoomId = roomId;
    renderRoomDetail(roomId);
    
    // 显示模态框
    const modal = new bootstrap.Modal(document.getElementById('roomDetailModal'));
    modal.show();
}

// 渲染房间详情
function renderRoomDetail(roomId) {
    const room = systemData.rooms[roomId];
    if (!room) {
        addLog(`找不到房间: ${roomId}`, 'error');
        return;
    }
    
    // 确保属性存在
    const roomName = room.name || '未命名房间';
    const hostId = room.hostId || '无房主';
    const playerCount = room.playerCount || 0;
    const maxPlayers = room.maxPlayers || 4;
    const status = room.status || 'WAITING';
    
    // 基本信息
    document.getElementById('detail-roomId').textContent = room.id;
    document.getElementById('detail-roomName').textContent = roomName;
    document.getElementById('detail-hostId').textContent = hostId;
    document.getElementById('detail-playerCount').textContent = playerCount;
    document.getElementById('detail-maxPlayers').textContent = maxPlayers;
    
    // 状态
    const statusText = status === 'WAITING' ? '等待中' : 
                       status === 'PLAYING' ? '游戏中' : '已结束';
    document.getElementById('detail-status').textContent = statusText;
    
    // 玩家列表
    const playerListElement = document.getElementById('roomPlayerList');
    playerListElement.innerHTML = '';
    
    if (!room.players || room.players.length === 0) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = `<td colspan="4" class="text-center">房间内暂无玩家</td>`;
        playerListElement.appendChild(emptyRow);
    } else {
        room.players.forEach(playerId => {
            const tr = document.createElement('tr');
            
            // 是否准备
            const isReady = room.readyPlayers && room.readyPlayers.includes(playerId);
            const readyText = isReady ? '<span class="badge bg-success">已准备</span>' : '<span class="badge bg-secondary">未准备</span>';
            
            // 手牌数量
            const handSize = room.playerHands && room.playerHands[playerId] ? room.playerHands[playerId].length : '未知';
            
            tr.innerHTML = `
                <td>${playerId} ${playerId === room.hostId ? '<span class="badge bg-primary">房主</span>' : ''}</td>
                <td>${readyText}</td>
                <td>${handSize}</td>
                <td><button class="btn btn-sm btn-danger" onclick="confirmKickFromRoom('${playerId}', '${roomId}')">踢出</button></td>
            `;
            
            playerListElement.appendChild(tr);
        });
    }
    
    // 解散房间按钮
    document.getElementById('dissolveRoomBtn').onclick = () => confirmDissolveRoom(roomId);
}

// 确认解散房间
function confirmDissolveRoom(roomId) {
    showConfirmModal('确定要解散房间吗？玩家将回到大厅。', () => {
        dissolveRoom(roomId);
    });
}

// 解散房间
function dissolveRoom(roomId) {
    if (!stompClient || !stompClient.connected) {
        addLog('WebSocket未连接，无法执行操作', 'error');
        return;
    }
    
    // 显示操作中状态
    addLog(`正在解散房间: ${roomId}`, 'info');
    
    // 发送解散房间请求
    stompClient.send("/app/admin/rooms/dissolve", {}, JSON.stringify({
        roomId: roomId
    }));
    
    // 手动更新UI状态，防止等待广播更新的延迟
    if (systemData.rooms[roomId]) {
        delete systemData.rooms[roomId];
        renderRoomList();
        document.getElementById('roomCount').textContent = `${Object.keys(systemData.rooms).length}个房间`;
        document.getElementById('totalRoomCount').textContent = Object.keys(systemData.rooms).length;
    }
}

// 确认踢出玩家
function confirmKickPlayer(playerId) {
    showConfirmModal(`确定要将玩家 ${playerId} 踢出游戏吗？`, () => {
        kickPlayer(playerId);
    });
}

// 踢出玩家
function kickPlayer(playerId) {
    if (!stompClient || !stompClient.connected) {
        addLog('WebSocket未连接，无法执行操作', 'error');
        return;
    }
    
    stompClient.send("/app/players/kick", {}, JSON.stringify({
        playerId: playerId
    }));
    
    addLog(`正在踢出玩家: ${playerId}`, 'info');
}

// 确认将玩家踢出房间
function confirmKickFromRoom(playerId, roomId) {
    showConfirmModal(`确定要将玩家 ${playerId} 踢出房间吗？`, () => {
        kickFromRoom(playerId, roomId);
    });
}

// 将玩家踢出房间
function kickFromRoom(playerId, roomId) {
    if (!stompClient || !stompClient.connected) {
        addLog('WebSocket未连接，无法执行操作', 'error');
        return;
    }
    
    stompClient.send("/app/rooms/kickPlayer", {}, JSON.stringify({
        playerId: playerId,
        roomId: roomId
    }));
    
    addLog(`正在将玩家 ${playerId} 踢出房间 ${roomId}`, 'info');
}

// 显示确认模态框
function showConfirmModal(message, callback) {
    document.getElementById('confirmMessage').textContent = message;
    
    pendingAction = callback;
    
    const modal = new bootstrap.Modal(document.getElementById('confirmModal'));
    modal.show();
}

// 设置事件监听
function setupEventListeners() {
    // 刷新按钮
    document.getElementById('refreshBtn').addEventListener('click', loadAllData);
    
    // 确认操作按钮
    document.getElementById('confirmActionBtn').addEventListener('click', function() {
        if (pendingAction) {
            pendingAction();
            pendingAction = null;
            
            // 关闭确认框
            bootstrap.Modal.getInstance(document.getElementById('confirmModal')).hide();
        }
    });
}

// 添加操作日志
function addLog(message, type = 'info') {
    const logContainer = document.getElementById('adminLog');
    const now = new Date();
    const timeString = now.toLocaleTimeString();
    
    const logEntry = document.createElement('div');
    logEntry.className = 'log-entry';
    logEntry.innerHTML = `
        <span class="log-time" style="color: #000;">[${timeString}]</span>
        <span class="log-type-${type}" style="color: #000;">${message}</span>
    `;
    
    logContainer.appendChild(logEntry);
    logContainer.scrollTop = logContainer.scrollHeight;
}

// 断开连接
function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
        stompClient = null;
        
        document.getElementById('connectionStatus').textContent = '未连接';
        document.getElementById('connectionStatus').className = 'badge bg-danger';
        
        addLog('已断开连接', 'warning');
    }
}

// 格式化运行时间
function formatUptime(milliseconds) {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);
    
    if (days > 0) {
        return `${days}天 ${hours % 24}小时`;
    } else if (hours > 0) {
        return `${hours}小时 ${minutes % 60}分钟`;
    } else if (minutes > 0) {
        return `${minutes}分钟 ${seconds % 60}秒`;
    } else {
        return `${seconds}秒`;
    }
} 