package cn.lunadeer.lagrangeMC.configuration;

import cn.lunadeer.lagrangeMC.utils.configuration.*;

import java.util.List;

public class Questions extends ConfigurationFile {

    @Comments({
            "配置题库",
            "当只有一个正确答案时，题目为单选",
            "当有多个正确答案时，题目为多选",
            "支持插入图片直链，请将图片上传到图床后获取链接",
    })
    @HandleManually
    public static List<Question> questions;

    public static class Question extends ConfigurationPart {
        public String title = "问题标题";
        public String description = "问题描述";
        public String imageUrl = "https://example.com/image.png";
        public List<String> correctAnswers = List.of("正确答案1", "正确答案2");
        public List<String> wrongAnswers = List.of("错误答案1", "错误答案2");
    }

    @PostProcess
    public static void loadQuestions() {

    }
}
