package cn.lunadeer.mc.deerlingbot.utils.databse;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/** 仅用于群事件的短查询，连接池等待仍由连接池自身的超时控制。 */
public final class EventQueryTimeout {
    public static final int SECONDS = 5;

    private EventQueryTimeout() { }

    public static void configure(Connection connection) throws SQLException {
        try {
            // 网络截止时间留出取消语句与事务回滚的余量，避免二者同时超时污染连接池。
            connection.setNetworkTimeout(Runnable::run, SECONDS * 2000);
        } catch (SQLFeatureNotSupportedException ignored) {
            // SQLite 没有网络连接，仍由各 Statement 的 queryTimeout 控制锁等待。
        }
    }
}
