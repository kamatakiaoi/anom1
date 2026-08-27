package com.anonymous.chat.ui.patchnotes;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.anonymous.chat.adapters.PatchNotesAdapter;
import com.anonymous.chat.api.RestClient;
import com.anonymous.chat.databinding.ActivityPatchNotesBinding;
import com.anonymous.chat.models.PatchNote;
import com.anonymous.chat.utils.PreferenceManager;

import java.util.List;

public class PatchNotesActivity extends AppCompatActivity {

    private ActivityPatchNotesBinding binding;
    private PatchNotesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPatchNotesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnPatchBack.setOnClickListener(v -> finish());

        binding.rvPatchNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatchNotesAdapter();
        binding.rvPatchNotes.setAdapter(adapter);

        loadPatchNotes();
    }

    private void loadPatchNotes() {
        binding.pbPatchNotes.setVisibility(View.VISIBLE);
        String serverUrl = PreferenceManager.getInstance(this).getServerBaseUrl();

        RestClient.fetchPatchNotes(serverUrl, new RestClient.PatchNotesCallback() {
            @Override
            public void onSuccess(List<PatchNote> patchNotes) {
                binding.pbPatchNotes.setVisibility(View.GONE);
                adapter.setPatchNotes(patchNotes);
            }

            @Override
            public void onError(String error) {
                binding.pbPatchNotes.setVisibility(View.GONE);
                Toast.makeText(PatchNotesActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
