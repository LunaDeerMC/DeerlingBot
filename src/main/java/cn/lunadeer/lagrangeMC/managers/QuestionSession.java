package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.configuration.Questions;
import cn.lunadeer.lagrangeMC.utils.scheduler.CancellableTask;

import java.util.*;

public class QuestionSession {

    public static class Question extends Questions.Question {
        public Question(Questions.Question raw, int idx, int total){
            this.title = "【" + idx + "/" + total + "】 " + raw.title;
            this.description = raw.description;
            this.imageUrl = raw.imageUrl;
            this.correctAnswers = raw.correctAnswers;
            this.wrongAnswers = raw.wrongAnswers;
            List<String> allAnswers = new ArrayList<>(correctAnswers);
            allAnswers.addAll(wrongAnswers);
            allAnswers.sort((a, b) -> (int) (Math.random() * 2 - 1));
            for (int i = 0; i < allAnswers.size(); i++) {
                String option = String.valueOf((char) ('A' + i));
                options.put(option, allAnswers.get(i));
                if (correctAnswers.contains(allAnswers.get(i))) {
                    this.correctoptions.add(option);
                }
            }
        }
        private final Map<String, String> options = new HashMap<>();
        private final List<String> correctoptions = new ArrayList<>();

        public Map<String, String> getOptions() {
            return options;
        }

        public List<String> getCorrectOptions() {
            return correctoptions;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getImageUrl() {
            return imageUrl;
        }
    }

    private final long userID;
    private final List<Question> questions;
    private int currentQuestion = 0;
    private int score = 0;
    private CancellableTask timer;

    public QuestionSession(long userID) {
        this.userID = userID;
        questions = new ArrayList<>();
        if (Configuration.whiteList.question.total > Questions.questions.size()) {
            int idx = 1;
            for (Questions.Question question : Questions.questions) {
                questions.add(new Question(question, idx, Questions.questions.size()));
                idx++;
            }
        } else {
            List<Questions.Question> allQuestions = new ArrayList<>(Questions.questions);
            for (int i = 0; i < Configuration.whiteList.question.total; i++) {
                int randomIndex = (int) (Math.random() * allQuestions.size());
                questions.add(new Question(allQuestions.get(randomIndex), i + 1, Configuration.whiteList.question.total));
                allQuestions.remove(randomIndex);
            }
        }
        // todo show question info
        // todo start a timer
    }

    public Question next() {
        if (currentQuestion >= questions.size()) {
            return null;
        }
        Question question = questions.get(currentQuestion);
        currentQuestion++;
        return question;
    }

    public void answer(String option) throws IllegalArgumentException{
        Question current = questions.get(currentQuestion);
        List<String> options = new ArrayList<>();
        // extract abcd... from option
        for (int i = 0; i < option.length(); i++) {
            char c = option.toUpperCase().charAt(i);
            if (c >= 'A' && c <= 'Z') {
                options.add(String.valueOf(c));
            }
        }
        // validate option is in options
        int correctCount = 0;
        for (String opt : options) {
            if (!current.getOptions().containsKey(opt)) {
                throw new IllegalArgumentException("选项不存在，经检查输入内容。");
            }
            if (current.getCorrectOptions().contains(opt)) {
                correctCount++;
            }
        }
        if (correctCount == current.getCorrectOptions().size()) {
            score++;
        }
    }

    public int getScore() {
        return score;
    }
}
