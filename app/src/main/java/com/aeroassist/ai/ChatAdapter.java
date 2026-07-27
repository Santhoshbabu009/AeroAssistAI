package com.aeroassist.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages){
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        if(messages.get(position).isUser())
            return 1;
        else
            return 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{

        TextView messageText;

        public ViewHolder(View itemView){
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent,int viewType){

        View view;

        if(viewType == 1)
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_user,parent,false);
        else
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_ai,parent,false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder,int position){

        ChatMessage msg = messages.get(position);
        holder.messageText.setText(msg.getMessage());

    }

    @Override
    public int getItemCount(){
        return messages.size();
    }
}