package com.example.mymoney.chatbot;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<ChatMessage> chatMessages;
    private static final int USER_MESSAGE = 1;
    private static final int BOT_MESSAGE = 2;
    private static final int LOADING_MESSAGE = 3;

    public ChatAdapter() {
        this.chatMessages = new ArrayList<>();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = chatMessages.get(position);
        if (message.isLoading()) {
            return LOADING_MESSAGE;
        }
        return message.isUser() ? USER_MESSAGE : BOT_MESSAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == USER_MESSAGE) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else if (viewType == LOADING_MESSAGE) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_loading_message, parent, false);
            return new LoadingViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bot_message, parent, false);
            return new BotMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = chatMessages.get(position);

        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof BotMessageViewHolder) {
            ((BotMessageViewHolder) holder).bind(message);
        }
        // LoadingViewHolder doesn't need binding
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    public void addMessage(ChatMessage message) {
        chatMessages.add(message);
        notifyItemInserted(chatMessages.size() - 1);
    }

    public void removeLastMessage() {
        if (!chatMessages.isEmpty()) {
            chatMessages.remove(chatMessages.size() - 1);
            notifyItemRemoved(chatMessages.size());
        }
    }

    public void clearMessages() {
        chatMessages.clear();
        notifyDataSetChanged();
    }

    public List<ChatMessage> getAllMessages() {
        return new ArrayList<>(chatMessages);
    }

    public void setMessages(List<ChatMessage> messages) {
        chatMessages.clear();
        chatMessages.addAll(messages);
        notifyDataSetChanged();
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        public void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }

    static class BotMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public BotMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        public void bind(ChatMessage message) {
            CharSequence formattedText = parseMarkdownBold(message.getMessage());
            messageText.setText(formattedText);
        }

        /**
         * Parse markdown bold syntax (**text**) and convert to SpannableString with bold style
         */
        private CharSequence parseMarkdownBold(String text) {
            if (text == null || !text.contains("**")) {
                return text;
            }

            SpannableStringBuilder builder = new SpannableStringBuilder();
            Pattern pattern = Pattern.compile("\\*\\*(.+?)\\*\\*");
            Matcher matcher = pattern.matcher(text);

            int lastEnd = 0;
            while (matcher.find()) {
                // Add text before the match
                builder.append(text.substring(lastEnd, matcher.start()));

                // Add bold text (without the **)
                String boldText = matcher.group(1);
                int start = builder.length();
                builder.append(boldText);
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                lastEnd = matcher.end();
            }

            // Add remaining text after last match
            if (lastEnd < text.length()) {
                builder.append(text.substring(lastEnd));
            }

            return builder;
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}