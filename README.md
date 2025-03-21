# 幻影扑克游戏 (Phantom Poker Game)

这是一个多人在线扑克牌游戏，玩家可以创建房间、加入游戏，通过声明和实际出牌的差异来进行博弈。游戏支持多人同时在线，实时互动，具有聊天功能和音效反馈。

## 功能特点

- **多人实时对战**：支持多个玩家同时在线游戏
- **房间系统**：可以创建和加入游戏房间
- **实时通信**：使用 WebSocket 实现实时游戏状态更新
- **聊天系统**：支持玩家间实时聊天，包含表情功能
- **音效反馈**：游戏操作配有音效，提升游戏体验
- **响应式界面**：适配不同屏幕尺寸
- **游戏规则**：
  - 玩家轮流出牌
  - 可以虚张声势
  - 支持质疑机制
  - 实时显示游戏状态和玩家排名

## 系统架构

该项目是基于 Spring Boot 构建的 Web 应用程序，使用以下技术：

### 后端技术栈
- **Spring Boot**：Web 应用框架
- **WebSocket**：实现实时通信
- **Spring JPA**：数据持久化
- **H2 数据库**：内存数据库，用于存储游戏状态

### 前端技术栈
- **HTML5**：页面结构
- **CSS3**：样式和动画
- **JavaScript**：客户端逻辑
- **jQuery**：DOM 操作和事件处理
- **SockJS**：WebSocket 客户端
- **STOMP**：消息协议
- **Layer**：弹窗组件

### 关键组件
- **GameService**：处理游戏核心逻辑
- **WebSocket 控制器**：处理实时游戏通信
- **游戏状态模型**：管理游戏状态和规则验证
- **前端控制器**：处理页面路由和资源访问

## 环境要求

- JDK 11+
- Maven 3.6+
- 现代浏览器（支持 WebSocket）

## 安装指南

1. 克隆仓库：

```bash
git clone https://github.com/yourusername/phantomPokerGame.git
cd phantomPokerGame
```

2. 编译项目：

```bash
mvn clean package
```

3. 运行应用：

```bash
java -jar target/poker-0.0.1-SNAPSHOT.jar
```

4. 访问游戏：

```
http://localhost:8080
```

## 项目结构

```
phantomPokerGame/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/poker/
│   │   │       ├── controller/
│   │   │       ├── model/
│   │   │       ├── service/
│   │   │       └── config/
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   ├── images/
│   │       │   ├── sounds/
│   │       │   └── plugins/
│   │       └── application.properties
│   └── test/
├── pom.xml
└── README.md
```

## 开发指南

### 本地开发
1. 确保已安装所需环境
2. 克隆项目并导入 IDE
3. 运行 `mvn clean install` 安装依赖
4. 运行 `PokerApplication` 主类启动项目

### 代码规范
- 遵循 Java 代码规范
- 使用统一的代码格式化工具
- 保持代码注释的完整性
- 编写单元测试确保代码质量

## 贡献指南

欢迎贡献代码、报告问题或提出改进建议。请遵循以下步骤：

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详情请参阅 LICENSE 文件

## 联系方式

如有任何问题或建议，请通过以下方式联系我们：

- 电子邮件：[your.email@example.com](mailto:your.email@example.com)
- GitHub Issues：[project-issues-link](https://github.com/yourusername/phantomPokerGame/issues) 