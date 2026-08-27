package com.anonymous.chat.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.models.PatchNote;

import java.util.ArrayList;
import java.util.List;

public class PatchNotesAdapter extends RecyclerView.Adapter<PatchNotesAdapter.PatchViewHolder> {

    private final List<PatchNote> patchNotes = new ArrayList<>();

    public void setPatchNotes(List<PatchNote> list) {
        patchNotes.clear();
        if (list != null) patchNotes.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patch_card, parent, false);
        return new PatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatchViewHolder holder, int position) {
        PatchNote note = patchNotes.get(position);
        holder.bind(note);
    }

    @Override
    public int getItemCount() {
        return patchNotes.size();
    }

    public static class PatchViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvMeta;
        private final LinearLayout itemsContainer;

        public PatchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvPatchTitle);
            tvMeta = itemView.findViewById(R.id.tvPatchMeta);
            itemsContainer = itemView.findViewById(R.id.patchItemsContainer);
        }

        public void bind(PatchNote note) {
            Context context = itemView.getContext();
            tvTitle.setText(note.getTitle() != null && !note.getTitle().isEmpty() ? note.getTitle() : note.getVersion());
            String meta = note.getVersion() + (note.getDate() != null && !note.getDate().isEmpty() ? " · " + note.getDate() : "");
            tvMeta.setText(meta);

            itemsContainer.removeAllViews();
            if (note.getItems() != null) {
                for (String item : note.getItems()) {
                    TextView tv = new TextView(context);
                    tv.setText("• " + item);
                    tv.setTextColor(Color.parseColor("#CCCCCC"));
                    tv.setTextSize(13.5f);
                    tv.setLineSpacing(0, 1.25f);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMargins(0, 2, 0, 4);
                    tv.setLayoutParams(lp);
                    itemsContainer.addView(tv);
                }
            }
        }
    }
}
