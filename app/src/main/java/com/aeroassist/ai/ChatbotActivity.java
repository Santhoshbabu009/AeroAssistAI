package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.*;

public class ChatbotActivity extends AppCompatActivity {

    EditText userInput;
    Button sendBtn;
    ImageButton micBtn;
    TextView thinkingIndicator;

    RecyclerView chatRecycler;
    ChatAdapter adapter;
    List<ChatMessage> messages;

    ChatDatabase db;

    OkHttpClient client = new OkHttpClient();

    // Use centralized Constants for network communication
    private final String SERVER_URL = Constants.CHAT_ENDPOINT;

    TextToSpeech tts;
    String currentUserEmail, currentUserType;
    long currentSessionId;
    ImageButton historyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        userInput = findViewById(R.id.userInput);
        sendBtn = findViewById(R.id.sendBtn);
        micBtn = findViewById(R.id.micBtn);
        chatRecycler = findViewById(R.id.chatRecycler);
        thinkingIndicator = findViewById(R.id.thinkingIndicator);
        historyBtn = findViewById(R.id.historyBtn);

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);

        chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        chatRecycler.setAdapter(adapter);

        // Initialize TextToSpeech with current app language
        String currentLang = LocaleHelper.getLanguage(this);
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS){
                tts.setLanguage(new Locale(currentLang));
            }
        });

        // Receive user data
        currentUserEmail = getIntent().getStringExtra("email");
        currentUserType = getIntent().getStringExtra("user_type");
        if (currentUserEmail == null) currentUserEmail = "default_user";
        if (currentUserType == null) currentUserType = "Visitor";
        
        db = ChatDatabase.getInstance(this);

        // Load previous session or start new one if none exists
        long requestedSession = getIntent().getLongExtra("session_id", -1);
        new Thread(() -> {
            if (requestedSession != -1) {
                currentSessionId = requestedSession;
            } else {
                Long lastSession = db.chatDao().getLastSessionId(currentUserEmail, currentUserType);
                if (lastSession != null) {
                    currentSessionId = lastSession;
                } else {
                    currentSessionId = System.currentTimeMillis();
                }
            }

            // Load chats for the determined session
            List<ChatMessage> oldChats = db.chatDao().getChatsBySession(currentSessionId);
            runOnUiThread(() -> {
                messages.addAll(oldChats);
                adapter.notifyDataSetChanged();
                if(messages.size() > 0)
                    chatRecycler.scrollToPosition(messages.size()-1);
            });
        }).start();

        micBtn.setOnClickListener(v -> startVoiceInput());
        sendBtn.setOnClickListener(v -> sendMessage());
        historyBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatHistoryActivity.class);
            intent.putExtra("email", currentUserEmail);
            intent.putExtra("user_type", currentUserType);
            startActivity(intent);
        });

        // Interactive Suggestion Prompt Chips Click Handlers
        findViewById(R.id.promptGate).setOnClickListener(v -> {
            userInput.setText("Where is Gate A24?");
            sendMessage();
        });

        findViewById(R.id.promptPass).setOnClickListener(v -> {
            userInput.setText("Show boarding pass");
            sendMessage();
        });

        findViewById(R.id.promptLounge).setOnClickListener(v -> {
            userInput.setText("Nearest lounge?");
            sendMessage();
        });

        // Handle prefilled message (e.g. from Cab Booking card)
        String prefill = getIntent().getStringExtra("prefill");
        if (prefill != null && !prefill.isEmpty()) {
            userInput.setText(prefill);
            sendMessage();
        }
    }

    private void startVoiceInput(){

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak your question");

        startActivityForResult(intent,1);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){

        super.onActivityResult(requestCode,resultCode,data);

        if(requestCode == 1 && resultCode == RESULT_OK){

            ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            String spokenText = result.get(0);

            userInput.setText(spokenText);

            sendMessage(); // automatically send after speaking
        }
    }

    private void sendMessage() {

        String question = userInput.getText().toString().trim();

        if(question.isEmpty())
            return;

        ChatMessage userMessage = new ChatMessage(question, true, currentUserEmail, currentUserType, currentSessionId);

        new Thread(() -> {
            db.chatDao().insert(userMessage);
        }).start();

        messages.add(userMessage);
        adapter.notifyDataSetChanged();
        chatRecycler.scrollToPosition(messages.size()-1);

        userInput.setText("");

        thinkingIndicator.setVisibility(View.VISIBLE);
        getAiResponse(question);
    }

    private String getOwnerReply(String message) {
        String lower = message.toLowerCase();
        boolean askDetails = lower.contains("age") || lower.contains("old") || lower.contains("dob") || lower.contains("born") || lower.contains("birth") ||
                            lower.contains("వయస్సు") || lower.contains("పుట్టిన") || 
                            lower.contains("வயது") || lower.contains("பிறந்த") || 
                            lower.contains("उम्र") || lower.contains("जन्म");

        // Telugu / Telugu-English Mix
        if (lower.contains("యజమాని") || lower.contains("ni owner") || lower.contains("evaru") || lower.contains("meeku owner")) {
            String reply = "Na owner Santhosh Babu.";
            if (askDetails) reply += " Ayana vayasu 20 years, DOB 25-09-2005.";
            return reply;
        }

        // Tamil / Tamil-English Mix (Thanglish)
        if (lower.contains("உரிமையாளர்") || lower.contains("யாரு") || lower.contains("yaru") || lower.contains("unga owner") || lower.contains("unoda owner") || lower.contains("yaaru")) {
            if (message.matches(".*[\\u0B80-\\u0BFF].*")) { // Tamil characters
                String reply = "என் owner Santhosh Babu.";
                if (askDetails) reply += " அவர் 20 வயது, 25-09-2005 அன்று பிறந்தவர்.";
                return reply;
            }
            String reply = "En owner Santhosh Babu.";
            if (askDetails) reply += " avar 20 years old, DOB 25-09-2005.";
            return reply;
        }

        // Hindi / Hindi-English Mix (Hinglish)
        if (lower.contains("मालिक") || lower.contains("kaun hai") || lower.contains("tera owner") || lower.contains("apka owner") || lower.contains("kon hai")) {
            if (message.matches(".*[\\u0900-\\u097F].*")) { // Hindi characters
                String reply = "मेरा मालिक Santhosh Babu है।";
                if (askDetails) reply += " उनकी उम्र 20 साल है और जन्म तिथि 25-09-2005 है।";
                return reply;
            }
            String reply = "Mera malik Santhosh Babu hai.";
            if (askDetails) reply += " Unki age 20 years hai aur DOB 25-09-2005 hai.";
            return reply;
        }

        // Spanish
        if (lower.contains("dueño") || lower.contains("propietario")) {
            String reply = "Mi dueño es Santhosh Babu.";
            if (askDetails) reply += " Tiene 20 años y su fecha de nacimiento es 25-09-2005.";
            return reply;
        }

        // Malayalam
        if (lower.contains("ഉടമ")) {
            String reply = "എന്റെ ഉടമ Santhosh Babu ആണ്.";
            if (askDetails) reply += " അദ്ദേഹത്തിന് 20 വയസ്സുണ്ട്, ജനനത്തീയതി 25-09-2005 ആണ്.";
            return reply;
        }

        // Default to English
        String reply = "My owner is Santhosh Babu.";
        if (askDetails) reply += " He is 20 years old, born on 25-09-2005.";
        return reply;
    }

    private void getAiResponse(String message) {
        String lowerMessage = message.toLowerCase();
        
        // Owner Question Matching (Multi-lingual)
        boolean isOwnerQuestion = lowerMessage.contains("who is your owner") || lowerMessage.contains("who is owner") || lowerMessage.contains("who owns you") ||
                                 lowerMessage.contains("ni owner") || lowerMessage.contains("unga owner") || lowerMessage.contains("unoda owner") || lowerMessage.contains("tera owner") || lowerMessage.contains("apka owner") || lowerMessage.contains("meeku owner") ||
                                 lowerMessage.contains("மாलिक कौन है") || lowerMessage.contains("यजमान") || // Hindi
                                 lowerMessage.contains("quién es tu dueño") || // Spanish
                                 lowerMessage.contains("ഉടമ") || // Malayalam
                                 lowerMessage.contains("உரிமையாளர்") || lowerMessage.contains("முதலாளி") || lowerMessage.contains("ஓனர்") || lowerMessage.contains("சொந்தக்காரர்") || // Tamil
                                 lowerMessage.contains("యజమాని"); // Telugu

        if (isOwnerQuestion) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                runOnUiThread(() -> {
                    thinkingIndicator.setVisibility(View.GONE);
                    String reply = getOwnerReply(message);
                    ChatMessage aiMsg = new ChatMessage(reply, false, currentUserEmail, currentUserType, currentSessionId);
                    
                    // Sync both user message and AI response to Supabase since we bypassed the backend
                    saveChatToSupabase(new ChatMessage(message, true, currentUserEmail, currentUserType, currentSessionId));
                    saveChatToSupabase(aiMsg);

                    new Thread(() -> {
                        db.chatDao().insert(aiMsg);
                    }).start();
                    messages.add(aiMsg);
                    adapter.notifyDataSetChanged();
                    chatRecycler.scrollToPosition(messages.size() - 1);
                    if (tts != null) {
                        tts.speak(reply, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
            }, 2000);
            return;
        }

        // Designer Question Matching (Multi-lingual)
        boolean isDesignerQuestion = lowerMessage.contains("who designed") || lowerMessage.contains("who created") || lowerMessage.contains("who built") || lowerMessage.contains("who developed") ||
                                    lowerMessage.contains("ni design chesindi evaru") || lowerMessage.contains("ni create chesindi") || // Mixed
                                    lowerMessage.contains("किसने बनाया") || lowerMessage.contains("डेवलपर") || // Hindi
                                    lowerMessage.contains("quién diseñó") || lowerMessage.contains("creador") || // Spanish
                                    lowerMessage.contains("രൂപകൽപ്പന") || // Malayalam
                                    lowerMessage.contains("வடிவமைத்தவர்") || lowerMessage.contains("உருவாக்கியவர்") || lowerMessage.contains("தயாரித்தவர்") || // Tamil
                                    lowerMessage.contains("రూపొందించారు"); // Telugu

        if (isDesignerQuestion) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                runOnUiThread(() -> {
                    thinkingIndicator.setVisibility(View.GONE);
                    String reply = getString(R.string.ai_designer_reply);
                    ChatMessage aiMsg = new ChatMessage(reply, false, currentUserEmail, currentUserType, currentSessionId);

                    // Sync both user message and AI response to Supabase
                    saveChatToSupabase(new ChatMessage(message, true, currentUserEmail, currentUserType, currentSessionId));
                    saveChatToSupabase(aiMsg);

                    new Thread(() -> {
                        db.chatDao().insert(aiMsg);
                    }).start();
                    messages.add(aiMsg);
                    adapter.notifyDataSetChanged();
                    chatRecycler.scrollToPosition(messages.size() - 1);
                    if (tts != null) {
                        tts.speak(reply, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
            }, 2000);
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        JSONObject json = new JSONObject();
        try {
            json.put("message", message);
            json.put("email", currentUserEmail);
            json.put("user_type", currentUserType);
            json.put("session_id", currentSessionId);
            json.put("lang", LocaleHelper.getLanguage(this));

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(Constants.CHAT_ENDPOINT)
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    final String error = e.getMessage() != null ? e.getMessage() : "Unknown Error";
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        runOnUiThread(() -> {
                            thinkingIndicator.setVisibility(View.GONE);
                            ChatMessage errorMsg = new ChatMessage("Backend Error: " + error, false, currentUserEmail, currentUserType, currentSessionId);
                            new Thread(() -> {
                                db.chatDao().insert(errorMsg);
                            }).start();
                            messages.add(errorMsg);
                            adapter.notifyDataSetChanged();
                            chatRecycler.scrollToPosition(messages.size() - 1);
                        });
                    }, 2000);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    String result = response.body().string();

                    try {

                        JSONObject obj = new JSONObject(result);
                        String reply = obj.getString("reply");

                        runOnUiThread(() -> {

                            // open navigation if needed
                            String lowerReply = reply.toLowerCase();
                            String lowerMessage = message.toLowerCase();

                            if (lowerReply.contains("gate") || lowerMessage.contains("navigate") && lowerMessage.contains("gate")) {
                                Intent intent = new Intent(ChatbotActivity.this, NavigationActivity.class);
                                if (lowerMessage.contains("gate 5") || lowerReply.contains("gate 5")) {
                                    intent.putExtra("location", "gate5");
                                } else {
                                    intent.putExtra("location", "gateA12");
                                }
                                startActivity(intent);
                            }

                            ChatMessage aiMsg = new ChatMessage(reply, false, currentUserEmail, currentUserType, currentSessionId);

                            new Thread(() -> {
                                db.chatDao().insert(aiMsg);
                            }).start();

                            messages.add(aiMsg);
                            adapter.notifyDataSetChanged();
                            chatRecycler.scrollToPosition(messages.size()-1);

                            // Speak AI response
                            if(tts != null){
                                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);
                            }

                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            ChatMessage errorMsg = new ChatMessage("Error parsing response: " + e.getMessage(), false, currentUserEmail, currentUserType, currentSessionId);
                            new Thread(() -> {
                                db.chatDao().insert(errorMsg);
                            }).start();
                            messages.add(errorMsg);
                            adapter.notifyDataSetChanged();
                            chatRecycler.scrollToPosition(messages.size() - 1);
                        });
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveChatToSupabase(ChatMessage msg) {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            try {
                JSONObject json = new JSONObject();
                json.put("email", msg.getUserEmail());
                json.put("user_type", msg.getUserType());
                json.put("session_id", msg.getSessionId());
                json.put("message", msg.getMessage());
                json.put("is_user", msg.isUser());

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(Constants.SAVE_CHAT_ENDPOINT)
                        .post(body)
                        .build();
                client.newCall(request).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if(tts != null){
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}