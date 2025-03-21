/**
 * 通用工具函数
 */

// 全局变量管理
const GameState = {
    currentPlayer: null,
    currentRoomId: null,
    selectedCards: [],
    isReady: false,
    soundEnabled: true,
    isHost: false,
    gameStarted: false,
    stompClient: null,
    player: null,
    room: null,
    isConnected: false,
    playerId: '',
    roomId: '',
    roomName: '',
    // 连接WebSocket服务器
    connect: function(username, successCallback, errorCallback) {
        try {
            console.log('正在连接WebSocket服务器...');
            
            // 配置WebSocket连接（使用SockJS作为回退选项）
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            
            // 设置STOMP客户端连接头
            const headers = {
                login: username  // 设置用户名
            };
            
            console.log('连接头信息:', JSON.stringify(headers));
            
            // 连接到STOMP broker
            this.stompClient.connect(headers, 
                // 连接成功回调
                (frame) => {
                    console.log('WebSocket连接成功:', frame);
                    this.isConnected = true;
                    this.playerId = username;
                    
                    // 如果提供了成功回调，则调用它
                    if (successCallback) {
                        successCallback(frame);
                    }
                },
                // 连接错误回调
                (error) => {
                    console.error('WebSocket连接失败:', error);
                    
                    // 如果提供了错误回调，则调用它
                    if (errorCallback) {
                        errorCallback(error);
                    }
                }
            );
        } catch (e) {
            console.error('建立WebSocket连接时发生错误:', e);
            if (errorCallback) {
                errorCallback(e);
            }
        }
    },
    
    // 断开WebSocket连接
    disconnect: function() {
        if (this.stompClient && this.stompClient.connected) {
            console.log('正在断开WebSocket连接...');
            this.stompClient.disconnect(() => {
                console.log('WebSocket连接已断开');
                this.isConnected = false;
            });
        }
    },
    
    // 发送游戏动作
    sendGameAction: function(actionType, actionData) {
        if (this.stompClient && this.stompClient.connected) {
            const payload = {
                type: actionType,
                ...actionData
            };
            GameState.stompClient.send("/app/game/action", {}, JSON.stringify({
                playerId: this.playerId,
                roomId: this.roomId,
                action: payload
            }));
        } else {
            console.error('无法发送游戏动作：WebSocket未连接');
        }
    }
};

// WebSocket连接管理
const WebSocketManager = {
    connect() {
        const socket = new SockJS('/ws');
        GameState.stompClient = Stomp.over(socket);
        
        GameState.stompClient.connect({}, function(frame) {
            console.log('Connected: ' + frame);
            
            // 订阅游戏状态更新
            GameState.stompClient.subscribe('/topic/game/state', function(gameState) {
                GameManager.updateGameState(JSON.parse(gameState.body));
            });
            
            // 订阅聊天消息
            GameState.stompClient.subscribe('/topic/game/chat', function(chatMessage) {
                ChatManager.addMessage(JSON.parse(chatMessage.body));
            });
            
            // 订阅游戏结果
            GameState.stompClient.subscribe('/topic/game/result', function(gameResult) {
                GameManager.showGameResult(JSON.parse(gameResult.body));
            });
        }, function(error) {
            console.error('连接错误:', error);
            setTimeout(() => WebSocketManager.connect(), 5000);
        });
    },

    sendMessage(type, data) {
        if (GameState.stompClient && GameState.stompClient.connected) {
            GameState.stompClient.send("/app/game/action", {}, JSON.stringify({
                type: type,
                roomId: GameState.currentRoomId,
                ...data
            }));
        }
    }
};

// 游戏核心逻辑管理
const GameManager = {
    // 游戏操作
    playCards() {
        if (GameState.selectedCards.length === 0) {
            UI.showError('请选择要出的牌');
            return;
        }
        
        const declaredValue = document.getElementById('declaredValue').value;
        WebSocketManager.sendMessage('PLAY', {
            playerId: GameState.currentPlayer,
            cards: GameState.selectedCards,
            declaredCount: GameState.selectedCards.length,
            declaredValue: declaredValue
        });
        
        HistoryManager.addItem(`${GameState.currentPlayer} 打出了 ${GameState.selectedCards.length} 张 ${declaredValue}`);
        UI.clearSelection();
        SoundManager.play('cardSound');
    },

    pass() {
        WebSocketManager.sendMessage('PASS', {
            playerId: GameState.currentPlayer
        });
        
        HistoryManager.addItem(`${GameState.currentPlayer} 选择了过牌`);
        SoundManager.play('cardSound');
    },

    challenge() {
        if (!GameState.currentRoom?.lastPlayer) {
            UI.showError('没有可以质疑的玩家');
            return;
        }
        
        WebSocketManager.sendMessage('CHALLENGE', {
            playerId: GameState.currentPlayer,
            targetPlayerId: GameState.currentRoom.lastPlayer
        });
        
        HistoryManager.addItem(`${GameState.currentPlayer} 质疑了 ${GameState.currentRoom.lastPlayer}`);
        SoundManager.play('challengeSound');
    },

    // 游戏状态更新
    updateGameState(state) {
        UI.updateGameStatus(state.gameStatus);
        UI.updateCurrentPlayer(state.currentPlayer);
        UI.updatePlayersList(state.players, state.readyPlayers, state.currentPlayer);
        UI.updatePlayerCount(state.players?.length || 0);
        
        if (state.playerHands?.[GameState.currentPlayer]) {
            UI.updatePlayerHand(state.playerHands[GameState.currentPlayer]);
        }
        
        UI.updateCurrentPile(state.currentPile || []);
        UI.updateLastClaim(state.lastClaim);
        
        const isCurrentPlayer = state.currentPlayer === GameState.currentPlayer;
        const gameStarted = state.gameStatus === 'PLAYING';
        
        UI.updateGameControls(isCurrentPlayer, gameStarted);
        
        if (isCurrentPlayer && gameStarted) {
            SoundManager.play('cardSound');
        }
    },

    // 游戏结果处理
    showGameResult(result) {
        UI.showGameResultModal(result);
        
        if (result.winner === GameState.currentPlayer) {
            SoundManager.play('winSound');
        } else {
            SoundManager.play('loseSound');
        }
    },

    // 机器人管理
    addRobots(count, difficulty) {
        WebSocketManager.sendMessage('ADD_ROBOTS', {
            count: count,
            difficulty: difficulty
        });
    },

    removeRobots() {
        WebSocketManager.sendMessage('REMOVE_ROBOTS', {});
    }
};

// UI管理
const UI = {
    showError(message) {
        alert(message);
    },

    updateGameStatus(status) {
        document.getElementById('gameStatusText').textContent = status || '等待中';
    },

    updateCurrentPlayer(player) {
        document.getElementById('currentPlayerName').textContent = player || '-';
    },

    updatePlayersList(players, readyPlayers, currentPlayer) {
        const playerList = document.getElementById('playerList');
        playerList.innerHTML = '';
        
        if (players?.length > 0) {
            players.forEach(player => {
                const isReady = readyPlayers?.includes(player);
                const isCurrent = player === currentPlayer;
                const isRobot = player.id?.startsWith('robot_');
                
                const playerElement = document.createElement('div');
                playerElement.className = `player-info ${isCurrent ? 'current-player' : ''} ${isRobot ? 'robot-player' : ''}`;
                
                let playerName = player.name;
                if (isRobot) {
                    playerName += `<span class="robot-badge">机器人</span>`;
                    playerName += `<span class="difficulty-badge difficulty-${GameState.currentRoom.robotDifficulty.toLowerCase()}">${GameState.currentRoom.robotDifficulty}</span>`;
                }
                
                playerElement.innerHTML = `
                    <div class="d-flex justify-content-between align-items-center">
                        <div><strong>${playerName}</strong></div>
                        <span class="badge ${isReady ? 'bg-success' : 'bg-warning'}">
                            ${isReady ? '已准备' : '未准备'}
                        </span>
                    </div>
                `;
                playerList.appendChild(playerElement);
            });
        }
    },

    updatePlayerCount(count) {
        document.getElementById('playerCount').textContent = count;
    },

    updatePlayerHand(cards) {
        const handContainer = document.getElementById('playerHand');
        handContainer.innerHTML = '';
        
        if (cards?.length > 0) {
            cards.sort((a, b) => a.value - b.value);
            
            cards.forEach((card, index) => {
                const cardElement = UI.createCardElement(card, index);
                handContainer.appendChild(cardElement);
            });
        }
        
        document.getElementById('handCount').textContent = cards?.length || 0;
    },

    createCardElement(card, index) {
        const cardElement = document.createElement('div');
        cardElement.className = 'card';
        cardElement.setAttribute('data-card', JSON.stringify(card));
        cardElement.onclick = () => UI.selectCard(cardElement);
        cardElement.style.marginLeft = `${index * 30}px`;
        
        const isRed = card.suit === 'HEARTS' || card.suit === 'DIAMONDS';
        const cardColor = isRed ? 'red' : 'black';
        const suitSymbol = UI.getSuitSymbol(card.suit);
        const valueDisplay = UI.getValueDisplay(card.value);
        
        cardElement.innerHTML = `
            <span class="lt pv" style="color:${cardColor}">${valueDisplay}</span>
            <span class="cm" style="color:${cardColor}">${suitSymbol}</span>
            <span class="rb pv" style="color:${cardColor}">${valueDisplay}</span>
        `;
        
        return cardElement;
    },

    getSuitSymbol(suit) {
        const symbols = {
            'HEARTS': '♥',
            'DIAMONDS': '♦',
            'CLUBS': '♣',
            'JOKER': '🃏'
        };
        return symbols[suit] || '♠';
    },

    getValueDisplay(value) {
        const displays = {
            1: 'A',
            11: 'J',
            12: 'Q',
            13: 'K'
        };
        return displays[value] || value;
    },

    selectCard(element) {
        element.classList.toggle('selected');
        const card = JSON.parse(element.getAttribute('data-card'));
        
        const cardIndex = GameState.selectedCards.findIndex(c => 
            c.suit === card.suit && c.value === card.value
        );
        
        if (cardIndex === -1) {
            GameState.selectedCards.push(card);
            SoundManager.play('cardSound');
        } else {
            GameState.selectedCards.splice(cardIndex, 1);
        }
        
        if (GameState.selectedCards.length > 0) {
            document.getElementById('declaredValue').value = GameState.selectedCards[0].value;
        }
    },

    clearSelection() {
        const cards = document.querySelectorAll('.card.selected');
        cards.forEach(card => card.classList.remove('selected'));
        GameState.selectedCards = [];
    },

    updateCurrentPile(cards) {
        const pileContainer = document.getElementById('currentPile');
        pileContainer.innerHTML = '';
        
        if (cards?.length > 0) {
            cards.forEach((card, index) => {
                const cardElement = UI.createCardElement(card, index);
                cardElement.classList.add('pile-card');
                cardElement.style.top = `${index * 5}px`;
                cardElement.style.left = `${index * 10}px`;
                cardElement.style.zIndex = index + 1;
                pileContainer.appendChild(cardElement);
            });
        }
    },

    updateLastClaim(claim) {
        const lastClaimElement = document.getElementById('lastClaim');
        if (claim) {
            lastClaimElement.querySelector('span').textContent = claim;
            lastClaimElement.style.display = 'block';
        } else {
            lastClaimElement.style.display = 'none';
        }
    },

    updateGameControls(isCurrentPlayer, gameStarted) {
        const gameControls = document.querySelector('.game-controls');
        
        if (!gameStarted) {
            gameControls.innerHTML = `
                <div class="d-flex justify-content-between align-items-center">
                    <div>
                        ${GameState.isHost ? `
                            <button onclick="GameManager.startGame()" class="btn btn-success">开始游戏</button>
                        ` : ''}
                        <button onclick="GameManager.toggleReady()" class="btn ${GameState.isReady ? 'btn-warning' : 'btn-primary'}">
                            ${GameState.isReady ? '取消准备' : '准备'}
                        </button>
                    </div>
                    <div>
                        <button onclick="GameManager.leaveRoom()" class="btn btn-danger">离开房间</button>
                    </div>
                </div>
            `;
        } else {
            gameControls.innerHTML = `
                <div class="row">
                    <div class="col">
                        <input type="text" id="claimInput" class="form-control claim-input" placeholder="声明（例如：2 7）">
                    </div>
                    <div class="col">
                        <button onclick="GameManager.playCards()" class="btn btn-success" ${!isCurrentPlayer ? 'disabled' : ''}>出牌</button>
                        <button onclick="GameManager.pass()" class="btn btn-warning" ${!isCurrentPlayer ? 'disabled' : ''}>过牌</button>
                        <button onclick="GameManager.challenge()" class="btn btn-danger" ${!isCurrentPlayer ? 'disabled' : ''}>质疑</button>
                    </div>
                </div>
            `;
        }
    },

    showGameResultModal(result) {
        const modal = document.getElementById('gameResultModal');
        const titleElement = document.getElementById('gameResultTitle');
        const messageElement = document.getElementById('gameResultMessage');
        const rankingElement = document.getElementById('playerRankingFinal');
        
        titleElement.textContent = '游戏结束';
        messageElement.textContent = result.message || '游戏结束';
        
        rankingElement.innerHTML = '';
        if (result.ranking?.length > 0) {
            const rankingList = document.createElement('ol');
            result.ranking.forEach(player => {
                const item = document.createElement('li');
                item.textContent = `${player.name} - ${player.cards}张牌`;
                rankingList.appendChild(item);
            });
            rankingElement.appendChild(rankingList);
        }
        
        modal.style.display = 'block';
    }
};

// 聊天管理
const ChatManager = {
    addMessage(chatMessage) {
        const chatList = document.getElementById('chatList');
        const chatItem = document.createElement('li');
        chatItem.innerHTML = `<strong>${chatMessage.sender}:</strong> ${chatMessage.content}`;
        
        if (chatMessage.sender === GameState.currentPlayer) {
            chatItem.classList.add('self-message');
        }
        
        chatList.appendChild(chatItem);
        chatList.scrollTop = chatList.scrollHeight;
    },

    sendMessage() {
        const content = document.getElementById('chatInput').value.trim();
        if (!content) return;
        
        WebSocketManager.sendMessage('CHAT', {
            playerId: GameState.currentPlayer,
            content: content
        });
        
        document.getElementById('chatInput').value = '';
    }
};

// 历史记录管理
const HistoryManager = {
    addItem(message) {
        const historyList = document.getElementById('historyList');
        const historyItem = document.createElement('li');
        historyItem.textContent = message;
        historyList.appendChild(historyItem);
        historyList.scrollTop = historyList.scrollHeight;
    }
};

// 音效管理
const SoundManager = {
    play(soundId) {
        if (GameState.soundEnabled) {
            try {
                const sound = document.getElementById(soundId);
                sound.currentTime = 0;
                sound.play();
            } catch (e) {
                console.error('播放音效失败:', e);
            }
        }
    },

    toggle() {
        GameState.soundEnabled = !GameState.soundEnabled;
        const soundButton = document.getElementById('soundToggle');
        soundButton.textContent = GameState.soundEnabled ? '🔊' : '🔇';
    }
};

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('loginForm').style.display = 'block';
    
    document.getElementById('playerName').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            GameManager.login();
        }
    });
});
