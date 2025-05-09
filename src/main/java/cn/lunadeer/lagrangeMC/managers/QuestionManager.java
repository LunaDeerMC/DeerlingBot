package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.configuration.Questions;
import cn.lunadeer.lagrangeMC.protocols.PrivateOperation;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import cn.lunadeer.lagrangeMC.utils.configuration.ConfigurationManager;
import com.alibaba.fastjson2.JSONObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.ReplySegment;
import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;

public class QuestionManager {

    private static QuestionManager questionManager;
    private Map<Long, QuestionSession> sessions = new HashMap<>();

    public QuestionManager(JavaPlugin plugin) {
        if (Configuration.whiteList.getType() != Configuration.WhiteList.Type.question) return;
        questionManager = this;
        plugin.saveResource("questions.yml", false);
        try {
            ConfigurationManager.load(Questions.class, new File(plugin.getDataFolder(), "questions.yml"));
        } catch (Exception e) {
            XLogger.error(e);
        }
        if (Configuration.whiteList.question.total > Questions.questions.size()) {
            XLogger.error("题库中的题目数量 {0} 不足 {1}，在生成问卷时会采用所有题目", Questions.questions.size(), Configuration.whiteList.question.total);
        }
    }

    public static QuestionManager getInstance() {
        if (questionManager == null) {
            throw new IllegalStateException("QuestionManager is not initialized yet.");
        }
        return questionManager;
    }

    public QuestionSession createSession(long userID) {
        if (sessions.containsKey(userID)) return sessions.get(userID);
        QuestionSession session = new QuestionSession(userID);
        sessions.put(userID, session);
        return session;
    }

    public QuestionSession getSession(long userID) {
        if (!sessions.containsKey(userID)) return null;
        return sessions.get(userID);
    }

    public boolean handleAnswer(String commandText, JSONObject rawJsonObj) {
        JSONObject sender = rawJsonObj.getJSONObject("sender");
        if (sender == null) return false;
        if (!sender.containsKey("user_id")) return false;
        long userID = sender.getLong("user_id");
        if (!sessions.containsKey(userID)) return false;
        QuestionSession session = sessions.get(userID);
        try {
            session.answer(commandText);
        } catch (Exception e) {
            if (!rawJsonObj.containsKey("message_id")) return true;
            long messageID = rawJsonObj.getLong("message_id");
            PrivateOperation.SendPrivateMessage(userID, ReplySegment(messageID), TextSegment(e.getMessage()));
            return true;
        }
        PrivateOperation.SendPrivateMessage(userID, parseQuestion(session.next()));
        return true;
    }

    public static JSONObject[] parseQuestion(QuestionSession.Question question) {
        List<JSONObject> questions = new ArrayList<>();
        questions.add(TextSegment(question.getTitle() + "\n"));
        questions.add(TextSegment(question.getDescription() + "\n"));
        if (question.getImageUrl() != null) {
            questions.add(TextSegment(question.getImageUrl() + "\n"));
        }
        questions.add(TextSegment("---------------\n"));
        if (question.getCorrectOptions().size() > 1) {
            questions.add(TextSegment("选项（多选）：\n"));
        } else {
            questions.add(TextSegment("选项（单选）：\n"));
        }
        for (Map.Entry<String, String> entry : question.getOptions().entrySet()) {
            questions.add(TextSegment(entry.getKey() + "：" + entry.getValue() + "\n"));
        }
        return questions.toArray(new JSONObject[0]);
    }

}
