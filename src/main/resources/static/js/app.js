const socket = new SockJS('/ws-poker');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
    stompClient.subscribe('/topic/game-state', (message) => {
        const gameMessage = JSON.parse(message.body);
        handleGameMessage(gameMessage);
    });
});

function handleGameMessage(message) {
    switch(message.type) {
        case 'INIT':
            if(message.status === 'WAITING') {
                document.getElementById('statusArea').innerText = '正在寻找对手...';
            } else {
                document.getElementById('roomId').innerText = message.content;
                document.getElementById('roomInfo').classList.remove('hidden');
            }
            break;
        case 'PLAY':
            updateCardDisplay(message.playerId, message.cards);
            break;
        case 'CHALLENGE':
            showChallengeResult(message.isValid);
            break;
    }
}

// 初始化游戏
document.getElementById('findGameBtn').addEventListener('click', () => {
    const playerId = generatePlayerId();
    stompClient.send('/app/game/init', {}, JSON.stringify(playerId));
});

// 出牌操作
document.getElementById('playCardsBtn').addEventListener('click', () => {
    const selectedCards = Array.from(document.querySelectorAll('.card.selected')).map(card => card.dataset.value);
    stompClient.send('/app/game/action', {}, JSON.stringify({
        playerId: getCurrentPlayerId(),
        action: 'PLAY',
        cards: selectedCards
    }));
});

// 质疑操作
document.getElementById('challengeBtn').addEventListener('click', () => {
    stompClient.send('/app/game/action', {}, JSON.stringify({
        playerId: getCurrentPlayerId(),
        action: 'CHALLENGE',
        target: getLastPlayerId()
    }));
});

// 跟牌操作
document.getElementById('followBtn').addEventListener('click', () => {
    stompClient.send('/app/game/action', {}, JSON.stringify({
        playerId: getCurrentPlayerId(),
        action: 'FOLLOW'
    }));
});

function getCurrentPlayerId() {
    return localStorage.getItem('currentPlayerId');
}

function getLastPlayerId() {
    return document.getElementById('lastAction').dataset.playerId;
}

function generatePlayerId() {
    return 'player_' + Math.random().toString(36).substr(2, 9);
}