# LMGPlugin

LMGPlugin是一个Minecraft服务器的留言插件，它允许玩家在游戏中留言，并且管理员可以查看、删除和管理留言。

## 功能

- 玩家可以在游戏中留言。
- 管理员可以查看所有留言。
- 管理员可以禁止某个玩家留言。
- 管理员可以允许某个玩家留言。
- 管理员可以删除某个留言。

## 使用方法

1. 安装插件并启用。

2. 在游戏中输入“/lmsg 留言内容”来留言。如果玩家被禁止留言，将无法发送留言。如果玩家没有管理员权限，他们必须等待24小时才能再次留言。

3. 管理员可以使用“/listmsg”命令查看所有留言，并可以指定页码来查看更多留言。

4. 管理员可以使用“/adminmsg”命令来管理留言。使用“/adminmsg list”命令查看所有留言，使用“/adminmsg deny 玩家名”命令禁止某个玩家留言，使用“/adminmsg allow 玩家名”命令允许某个玩家留言，使用“/adminmsg remove 编号”命令删除某个留言。

## 技术细节

- 使用HikariCP作为连接池。
- 使用MySQL作为数据库。
- 使用JavaPlugin作为插件框架。

## 作者

- [PonderFox0643](https://github.com/PonderFox0643)

## 许可证

这个项目使用[GPLv3许可证](https://github.com/PonderFox0643/LMG/blob/main/LICENSE)。
