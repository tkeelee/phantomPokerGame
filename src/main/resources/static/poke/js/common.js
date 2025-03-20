// 全局变量
let stompClient = null;
let currentPlayer = null;
let currentRoom = null;
let selectedCards = [];
let isReady = false;
let soundEnabled = true;

// 页面加载完成后执行
$(document).ready(function() {
    // 监听卡牌点击事件
    $(document).on('click', '.card', function() {
        selectCard(this);
    });
    
    // 监听聊天输入框回车事件
    $('#chatInput').keypress(function(e) {
        if (e.which == 13) {
            sendChat();
        }
    });
    
    // 折叠窗口效果
    $('.collapse_win dt').click(function() {
        collaseWin(this);
    });
    
    // 音效按钮初始化
    if($('#soundToggle').length === 0) {
        $('body').append('<button id="soundToggle" class="sound-toggle" onclick="toggleSound()">🔊</button>');
    }
});

// 折叠窗口
function collaseWin(el) {
    $(el).next().slideToggle();
    $(el).toggleClass('active');
}

// 连接WebSocket
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        
        // 订阅游戏状态更新
        stompClient.subscribe('/topic/game/state', function(gameState) {
            updateGameState(JSON.parse(gameState.body));
        });
        
        // 订阅聊天消息
        stompClient.subscribe('/topic/game/chat', function(chatMessage) {
            addChatMessage(JSON.parse(chatMessage.body));
        });
        
        // 订阅游戏结果
        stompClient.subscribe('/topic/game/result', function(gameResult) {
            showGameResult(JSON.parse(gameResult.body));
        });
    }, function(error) {
        console.error('连接错误:', error);
        // 尝试重新连接
        setTimeout(connect, 5000);
    });
}

// 登录
function login() {
    const playerName = $('#playerName').val().trim();
    if (!playerName) {
        layer.msg('请输入玩家名称');
        return;
    }
    currentPlayer = playerName;
    $('#loginForm').hide();
    $('#gameRoom').show();
    connect();
    joinExistingRoomOrCreate();
}

// 加入现有房间或创建新房间
function joinExistingRoomOrCreate() {
    // 先尝试获取现有房间列表
    $.ajax({
        url: '/api/game/rooms',
        type: 'GET',
        success: function(rooms) {
            if (rooms && rooms.length > 0) {
                // 有房间则加入第一个
                joinRoom(rooms[0].id);
            } else {
                // 没有房间则创建新房间
                createRoom();
            }
        },
        error: function(error) {
            console.error('获取房间列表失败:', error);
            createRoom(); // 出错也创建新房间
        }
    });
}

// 创建房间
function createRoom() {
    $.ajax({
        url: '/api/game/room',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            hostId: currentPlayer,
            maxPlayers: 4
        }),
        success: function(room) {
            currentRoom = room;
            updateRoomInfo(room);
            joinRoom(room.id);
        },
        error: function(error) {
            console.error('创建房间失败:', error);
            layer.msg('创建房间失败，请重试');
        }
    });
}

// 加入房间
function joinRoom(roomId) {
    $.ajax({
        url: `/api/game/room/${roomId}/join`,
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            playerId: currentPlayer
        }),
        success: function(room) {
            currentRoom = room;
            updateRoomInfo(room);
            playSound('cardSound');
        },
        error: function(error) {
            console.error('加入房间失败:', error);
            layer.msg('加入房间失败，请重试');
        }
    });
}

// 准备/取消准备
function toggleReady() {
    const readyButton = $('#readyButton');
    
    if (isReady) {
        // 已经准备了，取消准备
        $.ajax({
            url: `/api/game/room/${currentRoom.id}/cancel-ready`,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                playerId: currentPlayer
            }),
            success: function(response) {
                isReady = false;
                readyButton.removeClass('ready-active');
                readyButton.text('准备');
            }
        });
    } else {
        // 准备
        $.ajax({
            url: `/api/game/room/${currentRoom.id}/ready`,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                playerId: currentPlayer
            }),
            success: function(response) {
                isReady = true;
                readyButton.addClass('ready-active');
                readyButton.text('取消准备');
                playSound('cardSound');
            }
        });
    }
}

// 更新游戏状态
function updateGameState(state) {
    // 更新游戏状态文本
    $('#gameStatusText').text(state.gameStatus || '等待中');
    
    // 更新当前玩家指示
    $('#currentPlayerName').text(state.currentPlayer || '-');
    
    // 高亮显示当前玩家
    updatePlayersList(state.players, state.readyPlayers, state.currentPlayer);
    
    // 更新玩家数量
    $('#playerCount').text(state.players ? state.players.length : 0);
    
    // 更新手牌
    if (state.playerHands && state.playerHands[currentPlayer]) {
        updatePlayerHand(state.playerHands[currentPlayer]);
    }
    
    // 更新当前牌堆
    updateCurrentPile(state.currentPile || []);
    
    // 更新最后声明
    if (state.lastClaim) {
        $('#lastClaim span').text(state.lastClaim);
        $('#lastClaim').show();
    } else {
        $('#lastClaim').hide();
    }
    
    // 禁用/启用游戏操作按钮
    const isCurrentPlayer = state.currentPlayer === currentPlayer;
    const gameStarted = state.gameStatus === 'PLAYING';
    
    if (!isCurrentPlayer || !gameStarted) {
        $('.btn.turn, .btn.pass').addClass('disabled').attr('disabled', true);
    } else {
        $('.btn.turn, .btn.pass').removeClass('disabled').attr('disabled', false);
    }
    
    if (!isCurrentPlayer || !gameStarted || !state.lastClaim) {
        $('.btn.challenge').addClass('disabled').attr('disabled', true);
    } else {
        $('.btn.challenge').removeClass('disabled').attr('disabled', false);
    }
    
    // 如果是当前玩家的回合，播放提示音效
    if (isCurrentPlayer && gameStarted) {
        playSound('cardSound');
        
        // 添加提示动画
        layer.msg('轮到你出牌了', {
            icon: 1,
            time: 2000
        });
    }
}

// 更新房间信息
function updateRoomInfo(room) {
    $('#gameStatusText').text(room.status || '等待中');
    $('#playerCount').text(room.players ? room.players.length : 0);
}

// 更新玩家列表
function updatePlayersList(players, readyPlayers, currentPlayer) {
    const playerListElement = $('#playerList');
    playerListElement.empty();
    
    if (players && players.length > 0) {
        players.forEach(player => {
            const isReady = readyPlayers && readyPlayers.includes(player);
            const isCurrent = player === currentPlayer;
            
            const playerElement = $('<div>').addClass('player-item');
            if (isCurrent) playerElement.addClass('current-player');
            if (player === window.currentPlayer) playerElement.addClass('self');
            
            playerElement.html(`
                ${player} 
                ${isReady ? '<span class="ready-status">已准备</span>' : ''}
                ${isCurrent ? '<span class="current-marker">●</span>' : ''}
            `);
            
            playerListElement.append(playerElement);
        });
    }
}

// 更新玩家手牌
function updatePlayerHand(cards) {
    const handContainer = $('#playerHand');
    handContainer.empty();
    
    if (cards && cards.length > 0) {
        // 先对卡牌进行排序
        cards.sort((a, b) => a.value - b.value);
        
        cards.forEach((card, index) => {
            const cardElement = $('<div>').addClass('card');
            cardElement.attr('data-card', JSON.stringify(card));
            cardElement.click(function() { selectCard(this); });
            
            // 设置卡牌左边距，实现扇形排列效果
            cardElement.css({
                'margin-left': `${index * 30}px`,
                'z-index': index + 1
            });
            
            // 设置卡牌颜色和花色
            const isRed = card.suit === 'HEARTS' || card.suit === 'DIAMONDS';
            const cardColor = isRed ? 'red' : 'black';
            
            let suitSymbol = '♠';
            switch (card.suit) {
                case 'HEARTS': suitSymbol = '♥'; break;
                case 'DIAMONDS': suitSymbol = '♦'; break;
                case 'CLUBS': suitSymbol = '♣'; break;
                case 'JOKER': suitSymbol = '🃏'; break;
            }
            
            // 设置卡牌点数显示
            let valueDisplay = card.value;
            switch (card.value) {
                case 1: valueDisplay = 'A'; break;
                case 11: valueDisplay = 'J'; break;
                case 12: valueDisplay = 'Q'; break;
                case 13: valueDisplay = 'K'; break;
            }
            
            cardElement.html(`
                <span class="lt pv" style="color:${cardColor}">${valueDisplay}</span>
                <span class="cm" style="color:${cardColor}">${suitSymbol}</span>
                <span class="rb pv" style="color:${cardColor}">${valueDisplay}</span>
            `);
            
            handContainer.append(cardElement);
        });
    }
    
    $('#handCount').text(cards ? cards.length : 0);
}

// 选择卡牌
function selectCard(element) {
    $(element).toggleClass('selected');
    const card = JSON.parse($(element).attr('data-card'));
    
    // 检查卡片是否已经被选中
    const cardIndex = selectedCards.findIndex(c => 
        c.suit === card.suit && c.value === card.value
    );
    
    if (cardIndex === -1) {
        // 如果没有被选中，添加到选择列表
        selectedCards.push(card);
        playSound('cardSound');
    } else {
        // 如果已经被选中，从选择列表移除
        selectedCards.splice(cardIndex, 1);
    }
    
    // 更新声明值下拉框
    if (selectedCards.length > 0) {
        $('#declaredValue').val(selectedCards[0].value);
    }
}

// 选择所有相同点数的牌
function selectAllSameValue() {
    // 先获取当前选择的第一张牌的值
    if (selectedCards.length === 0) {
        layer.msg('请先选择一张牌');
        return;
    }
    
    const selectedValue = selectedCards[0].value;
    
    // 查找所有具有相同值的卡片并选中它们
    $('.card').each(function() {
        const card = JSON.parse($(this).attr('data-card'));
        if (card.value === selectedValue) {
            if (!$(this).hasClass('selected')) {
                $(this).addClass('selected');
                selectedCards.push(card);
            }
        }
    });
    
    playSound('cardSound');
}

// 清除选择
function clearSelection() {
    $('.card.selected').removeClass('selected');
    selectedCards = [];
}

// 更新当前牌堆
function updateCurrentPile(cards) {
    const pileContainer = $('#currentPile');
    pileContainer.empty();
    
    if (cards && cards.length > 0) {
        // 创建牌堆元素
        cards.forEach((card, index) => {
            const cardElement = $('<div>').addClass('card pile-card');
            
            // 设置卡牌位置，使其略微重叠
            cardElement.css({
                'top': `${index * 5}px`,
                'left': `${index * 10}px`,
                'z-index': index + 1
            });
            
            // 设置卡牌颜色和花色
            const isRed = card.suit === 'HEARTS' || card.suit === 'DIAMONDS';
            const cardColor = isRed ? 'red' : 'black';
            
            let suitSymbol = '♠';
            switch (card.suit) {
                case 'HEARTS': suitSymbol = '♥'; break;
                case 'DIAMONDS': suitSymbol = '♦'; break;
                case 'CLUBS': suitSymbol = '♣'; break;
                case 'JOKER': suitSymbol = '🃏'; break;
            }
            
            // 设置卡牌点数显示
            let valueDisplay = card.value;
            switch (card.value) {
                case 1: valueDisplay = 'A'; break;
                case 11: valueDisplay = 'J'; break;
                case 12: valueDisplay = 'Q'; break;
                case 13: valueDisplay = 'K'; break;
            }
            
            cardElement.html(`
                <span class="lt pv" style="color:${cardColor}">${valueDisplay}</span>
                <span class="cm" style="color:${cardColor}">${suitSymbol}</span>
                <span class="rb pv" style="color:${cardColor}">${valueDisplay}</span>
            `);
            
            pileContainer.append(cardElement);
        });
    }
}

// 添加历史记录
function addHistoryItem(message) {
    const historyList = $('#historyList');
    const historyItem = $('<li>').text(message);
    historyList.append(historyItem);
    
    // 自动滚动到底部
    historyList.scrollTop(historyList[0].scrollHeight);
}

// 添加聊天消息
function addChatMessage(chatMessage) {
    const chatList = $('#chatList');
    const chatItem = $('<li>');
    chatItem.html(`<strong>${chatMessage.sender}:</strong> ${chatMessage.content}`);
    
    // 如果是当前玩家的消息，添加样式
    if (chatMessage.sender === currentPlayer) {
        chatItem.addClass('self-message');
    }
    
    chatList.append(chatItem);
    
    // 自动滚动到底部
    chatList.scrollTop(chatList[0].scrollHeight);
}

// 出牌
function playCards() {
    if (selectedCards.length === 0) {
        layer.msg('请选择要出的牌', {icon: 0});
        return;
    }
    
    const declaredValue = $('#declaredValue').val();
    
    const message = {
        type: 'PLAY',
        roomId: currentRoom.id,
        playerId: currentPlayer,
        cards: selectedCards,
        declaredCount: selectedCards.length,
        declaredValue: declaredValue
    };
    
    stompClient.send("/app/game/action", {}, JSON.stringify(message));
    
    // 添加到历史记录
    addHistoryItem(`${currentPlayer} 打出了 ${selectedCards.length} 张 ${declaredValue}`);
    
    // 清除选择
    clearSelection();
    
    // 播放音效
    playSound('cardSound');
}

// 过牌
function pass() {
    const message = {
        type: 'PASS',
        roomId: currentRoom.id,
        playerId: currentPlayer
    };
    
    stompClient.send("/app/game/action", {}, JSON.stringify(message));
    
    // 添加到历史记录
    addHistoryItem(`${currentPlayer} 选择了过牌`);
    
    // 播放音效
    playSound('cardSound');
}

// 质疑
function challenge() {
    if (!currentRoom.lastPlayer) {
        layer.msg('没有可以质疑的玩家', {icon: 0});
        return;
    }
    
    // 确认弹窗
    layer.confirm(`确定要质疑 ${currentRoom.lastPlayer} 吗？`, {
        btn: ['确定','取消'],
        title: '质疑确认'
    }, function(index){
        layer.close(index);
        
        const message = {
            type: 'CHALLENGE',
            roomId: currentRoom.id,
            playerId: currentPlayer,
            targetPlayerId: currentRoom.lastPlayer
        };
        
        stompClient.send("/app/game/action", {}, JSON.stringify(message));
        
        // 添加到历史记录
        addHistoryItem(`${currentPlayer} 质疑了 ${currentRoom.lastPlayer}`);
        
        // 播放音效
        playSound('challengeSound');
    });
}

// 发送聊天消息
function sendChat() {
    const content = $('#chatInput').val().trim();
    if (!content) return;
    
    const message = {
        type: 'CHAT',
        roomId: currentRoom.id,
        playerId: currentPlayer,
        content: content
    };
    
    stompClient.send("/app/game/chat", {}, JSON.stringify(message));
    $('#chatInput').val('');
}

// 切换表情选择器
function toggleEmojiPicker() {
    $('#emojiContainer').toggle();
}

// 添加表情
function addEmoji(emoji) {
    const chatInput = $('#chatInput');
    chatInput.val(chatInput.val() + emoji);
    $('#emojiContainer').hide();
    chatInput.focus();
}

// 显示游戏结果
function showGameResult(result) {
    const rankingHtml = result.ranking && result.ranking.length > 0
        ? `<ol>${result.ranking.map(player => `<li>${player.name} - ${player.cards}张牌</li>`).join('')}</ol>`
        : '';
    
    layer.open({
        type: 1,
        title: '游戏结束',
        content: `
            <div class="result-modal">
                <p>${result.message || '游戏结束'}</p>
                <div class="ranking">
                    ${rankingHtml}
                </div>
                <div class="restart-btn-container">
                    <button onclick="restartGame()" class="restart-btn">再来一局</button>
                </div>
            </div>
        `,
        area: ['400px', '300px'],
        shadeClose: false,
        closeBtn: 1
    });
    
    // 播放音效
    if (result.winner === currentPlayer) {
        playSound('winSound');
    } else {
        playSound('loseSound');
    }
}

// 重新开始游戏
function restartGame() {
    // 关闭结果弹窗
    layer.closeAll();
    
    // 重新开始游戏
    $.ajax({
        url: `/api/game/room/${currentRoom.id}/restart`,
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            playerId: currentPlayer
        })
    });
    
    // 重置准备状态
    isReady = false;
    $('#readyButton').removeClass('ready-active').text('准备');
    
    // 清除历史记录
    $('#historyList').empty();
    
    // 清除选择
    clearSelection();
}

// 播放音效
function playSound(soundId) {
    if (soundEnabled) {
        try {
            const sound = document.getElementById(soundId);
            sound.currentTime = 0;
            sound.play();
        } catch (e) {
            console.error('播放音效失败:', e);
        }
    }
}

// 切换音效
function toggleSound() {
    soundEnabled = !soundEnabled;
    $('#soundToggle').text(soundEnabled ? '🔊' : '🔇');
}
