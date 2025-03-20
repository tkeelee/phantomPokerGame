# 扑克牌游戏

这是一个基于Spring Boot和WebSocket的多人扑克牌游戏。

## 功能特点

- 支持多人同时在线游戏
- 实时游戏状态同步
- 支持房间创建和加入
- 支持玩家准备和开始游戏
- 支持出牌、质疑和过牌等游戏操作
- 支持游戏胜利判定和排名

## 技术栈

- Spring Boot 2.7.0
- Spring WebSocket
- Lombok
- Maven

## 环境要求

- JDK 11+
- Maven 3.6+

## 快速开始

1. 克隆项目
```bash
git clone https://github.com/yourusername/poker.git
```

2. 进入项目目录
```bash
cd poker
```

3. 编译项目
```bash
mvn clean package
```

4. 运行项目
```bash
java -jar target/poker-0.0.1-SNAPSHOT.jar
```

5. 访问游戏
打开浏览器访问 http://localhost:8080

## 游戏规则

1. 游戏开始时，每个玩家获得13张牌
2. 玩家轮流出牌，可以出单张、对子、顺子等
3. 玩家可以质疑上一个玩家的出牌
4. 如果质疑成功，被质疑者获得所有牌
5. 如果质疑失败，质疑者获得所有牌
6. 第一个出完牌的玩家获胜

## API文档

### WebSocket接口

- `/ws` - WebSocket连接端点
- `/app/game/action` - 发送游戏动作
- `/topic/game/state` - 接收游戏状态更新

### HTTP接口

- `POST /api/game/room` - 创建房间
- `POST /api/game/room/{roomId}/join` - 加入房间
- `POST /api/game/room/{roomId}/leave` - 离开房间
- `POST /api/game/room/{roomId}/ready` - 准备游戏
- `POST /api/game/room/{roomId}/play` - 出牌
- `POST /api/game/room/{roomId}/challenge` - 质疑
- `POST /api/game/room/{roomId}/pass` - 过牌
- `GET /api/game/room/{roomId}` - 获取房间信息
- `GET /api/game/room/{roomId}/state` - 获取游戏状态
- `GET /api/game/room/{roomId}/players` - 获取玩家列表

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

MIT License 