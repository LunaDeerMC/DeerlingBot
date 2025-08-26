package cn.lunadeer.mc.deerlingbot.tables;

import cn.lunadeer.mc.deerlingbot.utils.databse.FIelds.*;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Delete;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Insert;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Select;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Table.Column;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Table.Create;
import cn.lunadeer.mc.deerlingbot.utils.databse.syntax.Update;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WhitelistTable {

    private static WhitelistTable instance;

    public WhitelistTable() throws SQLException {
        Column user_record_id = Column.of(new FieldInteger("id")).primary().serial().notNull().unique();
        Column user_user_id = Column.of(new FieldLong("user_id")).notNull().defaultSqlVal("'0'");
        Column user_record_uuid = Column.of(new FieldString("uuid")).notNull().unique();
        Column user_record_last_known_name = Column.of(new FieldString("last_known_name")).notNull().defaultSqlVal("'unknown'");
        Column user_record_code = Column.of(new FieldString("code")).notNull();
        Column user_record_bind = Column.of(new FieldBoolean("bind")).notNull().defaultSqlVal("false");
        Column user_record_last_join_at = Column.of(new FieldTimestamp("last_join_at")).notNull().defaultSqlVal("'1970-01-01 00:00:00'");
        Create.create().table("user_record")
                .column(user_record_id)
                .column(user_user_id)
                .column(user_record_uuid)
                .column(user_record_last_known_name)
                .column(user_record_last_join_at)
                .column(user_record_bind)
                .column(user_record_code)
                .execute();
        instance = this;
    }

    public static WhitelistTable getInstance() {
        if (instance == null) {
            try {
                instance = new WhitelistTable();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public void createRecord(UUID uuid, String name, String code) {
        try {
            Insert.insert().into("user_record")
                    .values(new FieldString("uuid", uuid.toString()),
                            new FieldString("last_known_name", name),
                            new FieldString("code", code.toUpperCase()),
                            new FieldTimestamp("last_join_at", Timestamp.valueOf(LocalDateTime.now())))
                    .onConflict("uuid").doNothing().execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isRecorded(UUID uuid) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldBoolean("bind", false))
                .from("user_record")
                .where("uuid = ?", uuid.toString())
                .execute();
        return !res.isEmpty();
    }

    public boolean isRecorded(long userId) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldBoolean("bind", false))
                .from("user_record")
                .where("user_id = ?", userId)
                .execute();
        return !res.isEmpty();
    }

    public boolean isBind(UUID uuid) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldBoolean("bind", false))
                .from("user_record")
                .where("uuid = ?", uuid.toString())
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for UUID: " + uuid);
        return (boolean) res.get(0).get("bind").getValue();
    }

    public boolean isBind(long userId) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldBoolean("bind", false))
                .from("user_record")
                .where("user_id = ?", userId)
                .execute();
        if (res.isEmpty()) return false;
        return (boolean) res.get(0).get("bind").getValue();
    }

    public String getCode(UUID uuid) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldString("code", ""))
                .from("user_record")
                .where("uuid = ?", uuid.toString())
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for UUID: " + uuid);
        return (String) res.get(0).get("code").getValue();
    }

    public boolean isCodeAvailable(String code) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldBoolean("bind", false))
                .from("user_record")
                .where("code = ?", code.toUpperCase())
                .execute();
        if (res.isEmpty()) return false;
        return !(boolean) res.get(0).get("bind").getValue();
    }

    public void setBind(String code, long userId) throws Exception {
        Update.update("user_record")
                .set(new FieldBoolean("bind", true), new FieldLong("user_id", userId))
                .where("code = ?", code.toUpperCase())
                .execute();
    }

    public void setName(UUID uuid, String name) throws Exception {
        Update.update("user_record")
                .set(
                        new FieldString("last_known_name", name),
                        new FieldTimestamp("last_join_at", Timestamp.valueOf(LocalDateTime.now()))
                )
                .where("uuid = ?", uuid.toString())
                .execute();
    }

    public UUID getUserUUID(long userId) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldString("uuid", ""))
                .from("user_record")
                .where("user_id = ?", userId)
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for userId: " + userId);
        return UUID.fromString((String) res.get(0).get("uuid").getValue());
    }

    public String getLastKnownName(UUID uuid) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldString("last_known_name", ""))
                .from("user_record")
                .where("uuid = ?", uuid.toString())
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for UUID: " + uuid);
        return (String) res.get(0).get("last_known_name").getValue();
    }

    public String getLastKnownName(long userId) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldString("last_known_name", ""))
                .from("user_record")
                .where("user_id = ?", userId)
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for userId: " + userId);
        return (String) res.get(0).get("last_known_name").getValue();
    }

    public String getLastJoinAt(UUID uuid) throws Exception {
        List<Map<String, Field<?>>> res = Select
                .select(new FieldTimestamp("last_join_at", Timestamp.valueOf(LocalDateTime.now())))
                .from("user_record")
                .where("uuid = ?", uuid.toString())
                .execute();
        if (res.isEmpty()) throw new RuntimeException("No record found for UUID: " + uuid);
        return res.get(0).get("last_join_at").getValue().toString();
    }

    public void deletePlayer(long userId) throws Exception {
        Delete.delete().from("user_record")
                .where("user_id = ?", userId)
                .execute();
    }
}
