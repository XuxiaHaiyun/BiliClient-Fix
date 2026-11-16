package com.RobinNotBad.BiliClient.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.SettingSection;

import java.util.List;

//操作教程列表
public class TutorialAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTutorialClickListener {
        void onTutorialClick(SettingSection section);
    }

    private final Context context;
    private final List<SettingSection> list;
    private final OnTutorialClickListener listener;

    public TutorialAdapter(Context context, List<SettingSection> list, OnTutorialClickListener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return "title".equals(list.get(position).type) ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0)
            return new TitleHolder(LayoutInflater.from(context).inflate(R.layout.cell_setting_title, parent, false));
        return new ItemHolder(LayoutInflater.from(context).inflate(R.layout.cell_goto, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingSection section = list.get(position);
        if (holder instanceof TitleHolder) ((TitleHolder) holder).bind(section);
        else ((ItemHolder) holder).bind(section);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class TitleHolder extends RecyclerView.ViewHolder {
        final TextView title;

        TitleHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.setting_title_text);
        }

        void bind(SettingSection section) {
            title.setText(section.name);
        }
    }

    class ItemHolder extends RecyclerView.ViewHolder {
        final TextView text;

        ItemHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }

        void bind(SettingSection section) {
            text.setText(section.name);
            itemView.setOnClickListener(view -> {
                if (listener != null) listener.onTutorialClick(section);
            });
        }
    }
}
