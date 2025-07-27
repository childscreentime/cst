package io.github.childscreentime.ui.fragments;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_POSITIVE;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.core.internal.view.SupportMenu;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.fragment.NavHostFragment;

import io.github.childscreentime.R;

import okhttp3.HttpUrl;

/**
 * Dialog fragment for admin access verification
 */
public class NoticeDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        EditText input = new EditText(getActivity());
        input.setInputType(129);
        builder.setView(input);
        builder.setMessage("Exit?").setPositiveButton("Go!", (dialog, which) -> {
            if (input.getText().toString().equals("253")) {
                input.setText(HttpUrl.FRAGMENT_ENCODE_SET);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        }).setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        return builder.create();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onStart() {
        super.onStart();
        ((AlertDialog) getDialog()).getButton(BUTTON_POSITIVE).setTextColor(SupportMenu.CATEGORY_MASK);
        ((AlertDialog) getDialog()).getButton(BUTTON_NEGATIVE).setTextColor(SupportMenu.CATEGORY_MASK);
    }
}
