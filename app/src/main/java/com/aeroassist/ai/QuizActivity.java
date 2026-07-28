package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class QuizActivity extends BaseActivity {

    private int currentQuestionIndex = 0;
    private TextView questionCount, questionText;
    private ProgressBar quizProgress;
    private Button option1, option2, option3;
    private List<Question> dailyQuestions;
    private int correctAnswersCount = 0;

    private static class Question {
        String text;
        String[] options;
        int correctIndex;

        Question(String text, String[] options, int correctIndex) {
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        questionCount = findViewById(R.id.questionCount);
        questionText = findViewById(R.id.questionText);
        quizProgress = findViewById(R.id.quizProgress);
        option1 = findViewById(R.id.option1);
        option2 = findViewById(R.id.option2);
        option3 = findViewById(R.id.option3);

        loadDailyQuestions();
        updateUI();

        View.OnClickListener listener = v -> {
            Button clickedButton = (Button) v;
            Question currentQuestion = dailyQuestions.get(currentQuestionIndex);
            String correctAnswerText = currentQuestion.options[currentQuestion.correctIndex];
            if (clickedButton.getText().toString().equals(correctAnswerText)) {
                correctAnswersCount++;
            }
            nextQuestion();
        };
        option1.setOnClickListener(listener);
        option2.setOnClickListener(listener);
        option3.setOnClickListener(listener);
    }

    private void loadDailyQuestions() {
        dailyQuestions = new ArrayList<>();
        dailyQuestions.add(new Question("Which airport code belongs to Chennai International Airport?", new String[]{"MAA", "BLR", "BOM"}, 0));
        dailyQuestions.add(new Question("Which airline is known as the national carrier of India?", new String[]{"IndiGo", "Air India", "SpiceJet"}, 1));
        dailyQuestions.add(new Question("What does ATC stand for in aviation?", new String[]{"Air Traffic Control", "Airport Terminal Center", "Aero Tracking Command"}, 0));
        dailyQuestions.add(new Question("What is the largest commercial passenger aircraft in the world?", new String[]{"Boeing 747", "Airbus A380", "Antonov An-225"}, 1));
        dailyQuestions.add(new Question("Which terminal at Chennai International Airport handles international flights?", new String[]{"Terminal 1", "Terminal 2", "Terminal 3"}, 1));
        dailyQuestions.add(new Question("What is the primary color of an airplane's 'Black Box'?", new String[]{"Black", "Orange", "Red"}, 1));
        dailyQuestions.add(new Question("What is the term for the area where airplanes park?", new String[]{"Runway", "Apron", "Hangar"}, 1));
        dailyQuestions.add(new Question("Which part of the plane provides aerodynamic lift?", new String[]{"Engine", "Wings", "Tail"}, 1));
        dailyQuestions.add(new Question("What is the standard transponder code for radio communication failure?", new String[]{"7500", "7600", "7700"}, 1));
        dailyQuestions.add(new Question("Which airport is famous for having the world's tallest indoor waterfall?", new String[]{"Changi (SIN)", "Haneda (HND)", "Dubai (DXB)"}, 0));
    }

    private void nextQuestion() {
        if (currentQuestionIndex < dailyQuestions.size() - 1) {
            currentQuestionIndex++;
            updateUI();
        } else {
            showSuccessDialog();
        }
    }

    private void updateUI() {
        Question q = dailyQuestions.get(currentQuestionIndex);
        questionCount.setText("Question " + (currentQuestionIndex + 1) + " of " + dailyQuestions.size());
        questionText.setText(q.text);
        option1.setText(q.options[0]);
        option2.setText(q.options[1]);
        option3.setText(q.options[2]);
        
        int progress = (int) (((float)(currentQuestionIndex + 1) / dailyQuestions.size()) * 100);
        quizProgress.setProgress(progress);
    }

    private void showSuccessDialog() {
        String email = getIntent().getStringExtra("email");
        String name = getIntent().getStringExtra("name");

        int finalScore = correctAnswersCount * 100;
        android.content.SharedPreferences globalPrefs = getSharedPreferences("GlobalStats", MODE_PRIVATE);
        String safeEmail = email != null ? email : "default";
        int currentHighScore = globalPrefs.getInt("score_" + safeEmail, 0);
        if (finalScore > currentHighScore) {
            globalPrefs.edit().putInt("score_" + safeEmail, finalScore).apply();
        }

        new AlertDialog.Builder(this)
                .setTitle("Quiz Completed! ðŸ†")
                .setMessage("You scored " + finalScore + " pts (" + correctAnswersCount + "/10 correct). See your ranking on the leaderboard!")
                .setPositiveButton("View Leaderboard", (dialog, which) -> {
                    Intent intent = new Intent(QuizActivity.this, LeaderboardActivity.class);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
