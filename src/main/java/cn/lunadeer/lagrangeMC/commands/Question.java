package cn.lunadeer.lagrangeMC.commands;

import com.alibaba.fastjson2.JSONObject;

public class Question extends BotCommand{
    /**
     * Constructs a new BotCommand.
     *
     */
    public Question() {
        super("question", "", false, true, false, false);
    }

    @Override
    public void handle(long userId, String commandText, JSONObject jsonObject) {

    }
}
