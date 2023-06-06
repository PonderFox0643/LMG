package com.pf43;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class LMGPlugin extends JavaPlugin {

    private HikariDataSource dataSource;
    private Map<UUID, Long> lastMessageTimes = new HashMap<>();
    private Map<String, Boolean> playerMessagePermissions = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("插件已启用，作者：PonderFox0643");

        // 加载配置文件
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // 配置HikariCP数据源
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getString("database.host") + ":" + config.getInt("database.port") + "/" + config.getString("database.database"));
        hikariConfig.setUsername(config.getString("database.username"));
        hikariConfig.setPassword(config.getString("database.password"));
        hikariConfig.addDataSourceProperty("cachePrepStmts", true);
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", 250);
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
        hikariConfig.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(hikariConfig);

        // 创建留言表
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS messages (id INT PRIMARY KEY AUTO_INCREMENT, player VARCHAR(36), message TEXT, time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            getLogger().info("已创建留言表");
        } catch (SQLException e) {
            getLogger().severe("创建留言表失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 创建留言权限表
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS message_permissions (player VARCHAR(36) PRIMARY KEY, allowed BOOLEAN DEFAULT true)");
            getLogger().info("已创建留言权限表");
        } catch (SQLException e) {
            getLogger().severe("创建留言权限表失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化玩家留言权限
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT player, allowed FROM message_permissions");
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String player = result.getString("player");
                boolean allowed = result.getBoolean("allowed");
                playerMessagePermissions.put(player, allowed);
            }
        } catch (SQLException e) {
            getLogger().severe("查询玩家留言权限失败: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已卸载");
        dataSource.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lmsg")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("该命令只能由玩家执行");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (!player.hasPermission("lmgplugin.admin") && lastMessageTimes.containsKey(uuid)) {
                long lastMessageTime = lastMessageTimes.get(uuid);
                long currentTime = System.currentTimeMillis();
                long timeDiff = currentTime - lastMessageTime;
                long remainingTime = TimeUnit.HOURS.toMillis(24) - timeDiff;

                if (remainingTime > 0) {
                    player.sendMessage("你必须等待 " + TimeUnit.MILLISECONDS.toHours(remainingTime) + " 小时后才能再次留言");
                    return true;
                }
            }

            if (args.length == 0) {
                player.sendMessage("用法: /lmsg <留言内容>");
                return true;
            }

            String message = String.join(" ", args);

            if (!playerMessagePermissions.getOrDefault(player.getName(), true)) {
                player.sendMessage("你被禁止留言");
                return true;
            }

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO messages (player, message) VALUES (?, ?)");
                statement.setString(1, player.getName());
                statement.setString(2, message);
                statement.executeUpdate();
                player.sendMessage("留言已记录");
                lastMessageTimes.put(uuid, System.currentTimeMillis());
            } catch (SQLException e) {
                getLogger().severe("记录留言失败: " + e.getMessage());
                player.sendMessage("记录留言失败，请联系管理员");
            }

            return true;
        } else if (command.getName().equalsIgnoreCase("listmsg")) {
            int page = 1;
            if (args.length > 0) {
                try {
                    page = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("页码必须是数字");
                    return true;
                }
            }

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) FROM messages");
                ResultSet countResult = countStatement.executeQuery();
                countResult.next();
                int total = countResult.getInt(1);
                int totalPages = (total + 9) / 10;

                if (page < 1 || page > totalPages) {
                    sender.sendMessage("页码超出范围");
                    return true;
                }

                PreparedStatement statement = connection.prepareStatement("SELECT * FROM messages ORDER BY time DESC LIMIT ?, ?");
                statement.setInt(1, (page - 1) * 10);
                statement.setInt(2, 10);
                ResultSet result = statement.executeQuery();

                List<String> messages = new ArrayList<>();
                while (result.next()) {
                    int id = result.getInt("id");
                    String player = result.getString("player");
                    String message = result.getString("message");
                    Timestamp time = result.getTimestamp("time");
                    messages.add(id + ": <" + player + ">-" + message + "-" + time.toString());
                }

                sender.sendMessage("第 " + page + " 页 / 共 " + totalPages + " 页");
                for (String message : messages) {
                    sender.sendMessage(message);
                }
            } catch (SQLException e) {
                getLogger().severe("查询留言失败: " + e.getMessage());
                sender.sendMessage("查询留言失败，请联系管理员");
            }

            return true;
        } else if (command.getName().equalsIgnoreCase("adminmsg")) {
            if (!sender.hasPermission("lmgplugin.admin")) {
                sender.sendMessage("你没有权限执行该命令");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("用法: /adminmsg <list|deny|allow|remove> [参数]");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("页码必须是数字");
                        return true;
                    }
                }

                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) FROM messages");
                    ResultSet countResult = countStatement.executeQuery();
                    countResult.next();
                    int total = countResult.getInt(1);
                    int totalPages = (total + 9) / 10;

                    if (page < 1 || page > totalPages) {
                        sender.sendMessage("页码超出范围");
                        return true;
                    }

                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM messages ORDER BY time DESC LIMIT ?, ?");
                    statement.setInt(1, (page - 1) * 10);
                    statement.setInt(2, 10);
                    ResultSet result = statement.executeQuery();

                    List<String> messages = new ArrayList<>();
                    while (result.next()) {
                        int id = result.getInt("id");
                        String player = result.getString("player");
                        String message = result.getString("message");
                        Timestamp time = result.getTimestamp("time");
                        messages.add(id + ": <" + player + ">-" + message + "-" + time.toString());
                    }

                    sender.sendMessage("第 " + page + " 页 / 共 " + totalPages + " 页");
                    for (String message : messages) {
                        sender.sendMessage(message);
                    }
                } catch (SQLException e) {
                    getLogger().severe("查询留言失败: " + e.getMessage());
                    sender.sendMessage("查询留言失败，请联系管理员");
                }

                return true;
            } else if (args[0].equalsIgnoreCase("deny")) {
                if (args.length < 2) {
                    sender.sendMessage("用法: /adminmsg deny <玩家名>");
                    return true;
                }

                String playerName = args[1];
                playerMessagePermissions.put(playerName, false);

                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO message_permissions (player, allowed) VALUES (?, ?) ON DUPLICATE KEY UPDATE allowed = ?");
                    statement.setString(1, playerName);
                    statement.setBoolean(2, false);
                    statement.setBoolean(3, false);
                    statement.executeUpdate();
                    sender.sendMessage("已禁止玩家 " + playerName + " 发送留言");
                } catch (SQLException e) {
                    getLogger().severe("更新玩家留言权限失败: " + e.getMessage());
                    sender.sendMessage("更新玩家留言权限失败，请联系管理员");
                }

                return true;
            } else if (args[0].equalsIgnoreCase("allow")) {
                if (args.length < 2) {
                    sender.sendMessage("用法: /adminmsg allow <玩家名>");
                    return true;
                }

                String playerName = args[1];
                playerMessagePermissions.put(playerName, true);

                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO message_permissions (player, allowed) VALUES (?, ?) ON DUPLICATE KEY UPDATE allowed = ?");
                    statement.setString(1, playerName);
                    statement.setBoolean(2, true);
                    statement.setBoolean(3, true);
                    statement.executeUpdate();
                    sender.sendMessage("已允许玩家 " + playerName + " 发送留言");
                } catch (SQLException e) {
                    getLogger().severe("更新玩家留言权限失败: " + e.getMessage());
                    sender.sendMessage("更新玩家留言权限失败，请联系管理员");
                }

                return true;
            } else if (args[0].equalsIgnoreCase("remove")) {
                if (args.length < 2) {
                    sender.sendMessage("用法: /adminmsg remove <编号>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("编号必须是数字");
                    return true;
                }

                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM messages WHERE id = ?");
                    statement.setInt(1, id);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected == 0) {
                        sender.sendMessage("找不到编号为 " + id + " 的留言");
                    } else {
                        sender.sendMessage("已删除编号为 " + id + " 的留言");
                    }
                } catch (SQLException e) {
                    getLogger().severe("删除留言失败: " + e.getMessage());
                    sender.sendMessage("删除留言失败，请联系管理员");
                }

                return true;
            }
        }
        return false;
    }
}