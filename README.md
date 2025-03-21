# 幻影扑克游戏 (Phantom Poker Game)

这是一个多人在线扑克牌游戏，玩家可以创建房间、加入游戏，通过声明和实际出牌的差异来进行博弈。

## 系统架构

该项目是基于Spring Boot构建的Web应用程序，使用以下技术：

- **后端**：Spring Boot、WebSocket、Spring JPA
- **前端**：HTML、CSS、JavaScript、jQuery、SockJS、STOMP
- **数据持久化**：H2数据库（内存模式）

### 关键组件

- **GameService**：处理游戏核心逻辑
- **WebSocket控制器**：处理实时游戏通信
- **游戏状态模型**：管理游戏状态和规则验证

## 环境要求

- JDK 11+
- Maven 3.6+

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

## 贡献指南

欢迎贡献代码、报告问题或提出改进建议。请遵循以下步骤：

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 许可证

本项目采用MIT许可证 - 详情请参阅LICENSE文件

## 联系方式

如有任何问题或建议，请通过以下方式联系我们：

- 电子邮件：[your.email@example.com](mailto:your.email@example.com)
- GitHub Issues：[project-issues-link](https://github.com/yourusername/phantomPokerGame/issues) 