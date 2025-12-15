package com.example.mymoney;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.chatbot.ChatAdapter;
import com.example.mymoney.chatbot.ChatMessage;
import com.example.mymoney.chatbot.ChatbotService;
import com.example.mymoney.utils.TestDataGenerator;

import java.util.HashMap;
import java.util.Map;

public class AIChatFragment extends Fragment {

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private EditText messageInput;
    private ImageView sendButton;
    private ChatbotService chatbotService;
    private LinearLayout suggestedQuestion1, suggestedQuestion2;
    private TextView suggestedText1, suggestedText2;
    private HorizontalScrollView quickActionsScroll;

    // 🔹 Static cache to preserve chat history per wallet
    private static Map<String, ChatAdapter> chatHistoryCache = new HashMap<>();
    private int currentUserId = -1;
    private int currentWalletId = -1;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        currentUserId = MainActivity.getCurrentUserId();
        currentWalletId = MainActivity.getSelectedWalletId();
        
        // Initialize views
        chatRecyclerView = view.findViewById(R.id.chat_recycler_view);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        
        // Setup RecyclerView with cached or new adapter
        setupChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatRecyclerView.setAdapter(chatAdapter);
        
        // Initialize chatbot service
        chatbotService = new ChatbotService(requireContext());
        
        // Setup suggested questions
        setupSuggestedQuestions(view);
        
        // Setup quick action chips
        setupQuickActions(view);
        
        // Setup send button
        sendButton.setOnClickListener(v -> sendMessage());
        
        // Setup keyboard visibility listener
        setupKeyboardListener(view);
        
        // Setup input focus listener to scroll when keyboard appears
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Post with delay to ensure keyboard is shown
                view.postDelayed(() -> scrollToBottom(), 200);
            }
        });
        
        // Add welcome message if this is a new chat
        if (chatAdapter.getItemCount() == 0) {
            addWelcomeMessage();
        } else {
            // Scroll to bottom if restoring chat
            scrollToBottom();
        }
    }
    
    /**
     * Setup chat adapter - either restore from cache or create new one
     */
    private void setupChatAdapter() {
        String cacheKey = getCacheKey();
        
        if (chatHistoryCache.containsKey(cacheKey)) {
            // Restore existing chat history
            chatAdapter = chatHistoryCache.get(cacheKey);
        } else {
            // Create new chat adapter
            chatAdapter = new ChatAdapter();
            chatHistoryCache.put(cacheKey, chatAdapter);
        }
    }
    
    /**
     * Generate unique cache key for user+wallet combination
     */
    private String getCacheKey() {
        return "user_" + currentUserId + "_wallet_" + currentWalletId;
    }
    
    /**
     * Public method to clear chat history for current wallet
     */
    public void clearChatHistory() {
        String cacheKey = getCacheKey();
        chatHistoryCache.remove(cacheKey);
        if (chatAdapter != null) {
            chatAdapter.clearMessages();
            addWelcomeMessage();
        }
    }
    
    /**
     * Static method to clear all chat history (for logout, etc.)
     */
    public static void clearAllChatHistory() {
        chatHistoryCache.clear();
    }

    
    private void setupSuggestedQuestions(View view) {
        suggestedQuestion1 = view.findViewById(R.id.suggested_question_1);
        suggestedQuestion2 = view.findViewById(R.id.suggested_question_2);
        
        if (suggestedQuestion1 != null) {
            suggestedQuestion1.setOnClickListener(v -> {
                messageInput.setText("Tôi nên chi tiêu như thế nào?");
                sendMessage();
            });
        }
        
        if (suggestedQuestion2 != null) {
            suggestedQuestion2.setOnClickListener(v -> {
                messageInput.setText("Nhận xét chi tiêu tháng qua của tôi");
                sendMessage();
            });
        }
    }
    
    /**
     * Setup quick action chips for budget recommendations
     */
    private void setupQuickActions(View view) {
        quickActionsScroll = view.findViewById(R.id.quick_actions_scroll);
        
        // Budget status chip
        TextView chipBudgetStatus = view.findViewById(R.id.chip_budget_status);
        if (chipBudgetStatus != null) {
            chipBudgetStatus.setOnClickListener(v -> {
                sendQuickQuery("Tình trạng ngân sách của tôi thế nào? Tôi có đang đúng tiến độ không?");
            });
        }
        
        // Spending tips chip
        TextView chipSpendingTips = view.findViewById(R.id.chip_spending_tips);
        if (chipSpendingTips != null) {
            chipSpendingTips.setOnClickListener(v -> {
                sendQuickQuery("Dựa vào ngân sách của tôi, hãy đưa ra mẹo giảm chi tiêu cụ thể.");
            });
        }
        
        // Daily limit chip
        TextView chipDailyLimit = view.findViewById(R.id.chip_daily_limit);
        if (chipDailyLimit != null) {
            chipDailyLimit.setOnClickListener(v -> {
                sendQuickQuery("Hôm nay tôi có thể chi bao nhiêu tiền để không vượt ngân sách?");
            });
        }
        
        // Save more chip
        TextView chipSaveMore = view.findViewById(R.id.chip_save_more);
        if (chipSaveMore != null) {
            chipSaveMore.setOnClickListener(v -> {
                sendQuickQuery("Dựa vào mô hình chi tiêu của tôi, làm sao tôi có thể tiết kiệm nhiều hơn?");
            });
        }
        
        // Spending habits chip - NEW
        TextView chipSpendingHabits = view.findViewById(R.id.chip_spending_habits);
        if (chipSpendingHabits != null) {
            chipSpendingHabits.setOnClickListener(v -> {
                sendPatternAnalysisQuery();
            });
        }
        
        // Generate test data chip - FOR TESTING
        TextView chipGenerateTestData = view.findViewById(R.id.chip_generate_test_data);
        if (chipGenerateTestData != null) {
            chipGenerateTestData.setOnClickListener(v -> {
                showTestDataDialog();
            });
        }
    }
    
    /**
     * Show dialog to generate or clear test data
     */
    private void showTestDataDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("🧪 Dữ liệu Test")
            .setMessage("Tạo dữ liệu test để kiểm tra tính năng phân tích thói quen chi tiêu?\n\n" +
                "Sẽ tạo:\n" +
                "• 6 tháng giao dịch\n" +
                "• Thu nhập hàng tháng\n" +
                "• Chi tiêu đa dạng\n" +
                "• Ngân sách mẫu")
            .setPositiveButton("Tạo dữ liệu", (dialog, which) -> {
                generateTestData();
            })
            .setNegativeButton("Xóa dữ liệu", (dialog, which) -> {
                clearTestData();
            })
            .setNeutralButton("Hủy", null)
            .show();
    }
    
    /**
     * Generate test data
     */
    private void generateTestData() {
        // Show loading message
        ChatMessage loadingMessage = new ChatMessage("🔄 Đang tạo dữ liệu test...", false);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();
        
        TestDataGenerator generator = new TestDataGenerator(requireContext());
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();
        
        generator.generateTestData(userId, walletId, new TestDataGenerator.GeneratorCallback() {
            @Override
            public void onComplete(String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading message
                        chatAdapter.removeLastMessage();
                        
                        // Add success message
                        ChatMessage successMessage = new ChatMessage(message, false);
                        chatAdapter.addMessage(successMessage);
                        scrollToBottom();
                        
                        Toast.makeText(requireContext(), "✅ Đã tạo dữ liệu test!", Toast.LENGTH_SHORT).show();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading message
                        chatAdapter.removeLastMessage();
                        
                        // Add error message
                        ChatMessage errorMessage = new ChatMessage("❌ " + error, false);
                        chatAdapter.addMessage(errorMessage);
                        scrollToBottom();
                        
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    /**
     * Clear test data
     */
    private void clearTestData() {
        ChatMessage loadingMessage = new ChatMessage("🔄 Đang xóa dữ liệu...", false);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();
        
        TestDataGenerator generator = new TestDataGenerator(requireContext());
        int walletId = MainActivity.getSelectedWalletId();
        
        generator.clearTestData(walletId, new TestDataGenerator.GeneratorCallback() {
            @Override
            public void onComplete(String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        chatAdapter.removeLastMessage();
                        ChatMessage successMessage = new ChatMessage(message, false);
                        chatAdapter.addMessage(successMessage);
                        scrollToBottom();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        chatAdapter.removeLastMessage();
                        ChatMessage errorMessage = new ChatMessage("❌ " + error, false);
                        chatAdapter.addMessage(errorMessage);
                        scrollToBottom();
                    });
                }
            }
        });
    }
    
    /**
     * Send a quick query with instant rule-based response + LLM enhancement
     */
    private void sendQuickQuery(String query) {
        // Add user message
        ChatMessage userMessage = new ChatMessage(query, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();
        
        // Get quick budget recommendation first (rule-based)
        int walletId = MainActivity.getSelectedWalletId();
        chatbotService.getQuickBudgetRecommendation(walletId, new ChatbotService.ChatbotCallback() {
            @Override
            public void onSuccess(String quickResponse) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Show quick response
                        ChatMessage quickMessage = new ChatMessage(quickResponse, false);
                        chatAdapter.addMessage(quickMessage);
                        scrollToBottom();
                        
                        // Then get LLM response for more detailed advice
                        sendMessageToLLM(query);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Fallback to LLM only
                        sendMessageToLLM(query);
                    });
                }
            }
        });
    }
    
    /**
     * Send pattern analysis query - shows spending habits
     */
    private void sendPatternAnalysisQuery() {
        // Add user message
        String query = "Phân tích thói quen chi tiêu của tôi và đề xuất những gì tôi nên mua tháng này.";
        ChatMessage userMessage = new ChatMessage(query, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();
        
        // Add loading indicator
        ChatMessage loadingMessage = new ChatMessage(true);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();
        
        // Get pattern analysis
        int walletId = MainActivity.getSelectedWalletId();
        chatbotService.getSpendingPatternAnalysis(walletId, new ChatbotService.ChatbotCallback() {
            @Override
            public void onSuccess(String patternResponse) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Show pattern analysis response
                        ChatMessage patternMessage = new ChatMessage(patternResponse, false);
                        chatAdapter.addMessage(patternMessage);
                        scrollToBottom();
                        
                        // Then get LLM response for personalized advice
                        sendMessageToLLM(query);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Show error and fallback to LLM
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                        sendMessageToLLM(query);
                    });
                }
            }
        });
    }
    
    /**
     * Send message to LLM only (used after quick response)
     */
    private void sendMessageToLLM(String message) {
        // Add loading indicator
        ChatMessage loadingMessage = new ChatMessage(true);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();
        
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();
        
        chatbotService.generateFinancialAdvice(userId, walletId, message, new ChatbotService.ChatbotCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Add bot response
                        ChatMessage botMessage = new ChatMessage("🤖 " + response, false);
                        chatAdapter.addMessage(botMessage);
                        scrollToBottom();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Show error
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void addWelcomeMessage() {
        ChatMessage welcomeMessage = new ChatMessage(
            "Xin chào! 👋 Tôi là trợ lý tài chính của bạn.\n\n" +
            "Tôi có thể giúp bạn:\n" +
            "• Phân tích chi tiêu\n" +
            "• Lời khuyên tiết kiệm\n" +
            "• Đánh giá tình hình tài chính\n\n" +
            "Hãy hỏi tôi bất cứ điều gì về tài chính của bạn!",
            false
        );
        chatAdapter.addMessage(welcomeMessage);
        scrollToBottom();
    }
    
    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        
        if (message.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add user message
        ChatMessage userMessage = new ChatMessage(message, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();
        
        // Clear input
        messageInput.setText("");
        
        // Add loading indicator
        ChatMessage loadingMessage = new ChatMessage(true);
        chatAdapter.addMessage(loadingMessage);
        scrollToBottom();
        
        // Get AI response (wallet-specific)
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId(); // 🔹 Pass wallet ID
        
        chatbotService.generateFinancialAdvice(userId, walletId, message, new ChatbotService.ChatbotCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Add bot response
                        ChatMessage botMessage = new ChatMessage(response, false);
                        chatAdapter.addMessage(botMessage);
                        scrollToBottom();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Remove loading indicator
                        chatAdapter.removeLastMessage();
                        
                        // Add error message
                        ChatMessage errorMessage = new ChatMessage(
                            "Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau.",
                            false
                        );
                        chatAdapter.addMessage(errorMessage);
                        scrollToBottom();
                        
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }
    
    /**
     * Setup keyboard visibility listener to auto-scroll when keyboard appears
     */
    private void setupKeyboardListener(View rootView) {
        // Use WindowInsetsCompat for proper keyboard handling with EdgeToEdge
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Apply bottom padding when keyboard is visible
            int bottomPadding = Math.max(imeInsets.bottom, systemBarInsets.bottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomPadding);
            
            // Scroll to bottom when keyboard appears
            if (imeInsets.bottom > 0) {
                v.post(() -> scrollToBottom());
            }
            
            return WindowInsetsCompat.CONSUMED;
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove insets listener
        if (getView() != null) {
            ViewCompat.setOnApplyWindowInsetsListener(getView(), null);
        }
    }
}

