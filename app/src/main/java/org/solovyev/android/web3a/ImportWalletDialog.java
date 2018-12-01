package org.solovyev.android.web3a;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.web3j.crypto.Bip39Wallet;
import org.web3j.crypto.MnemonicUtils;

import java.util.Locale;

class ImportWalletDialog {
    @NonNull
    private final MainActivity mActivity;
    @NonNull
    private final AlertDialog mDialog;
    @NonNull
    private final EditText mMnemonic;

    ImportWalletDialog(@NonNull MainActivity activity) {
        mActivity = activity;
        final FrameLayout frame = new FrameLayout(activity);
        final Resources res = activity.getResources();
        final int padding = res.getDimensionPixelSize(R.dimen.side_padding);
        frame.setPadding(padding, 0, padding, 0);
        mMnemonic = new EditText(activity);
        frame.addView(mMnemonic);

        final AlertDialog.Builder builder =
                new AlertDialog.Builder(mActivity).setView(frame).setTitle("Enter mnemonic");
        builder.setPositiveButton("Import", null);

        mDialog = builder.create();
        mDialog.setOnShowListener(d -> {
            final Button ok = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String mnemonic = mMnemonic.getText().toString().toLowerCase(Locale.US);
                    try {
                        MnemonicUtils.generateEntropy(mnemonic);
                        // The keys are not saved on disk but the mnemonic is saved in SharedPreferences.
                        final Bip39Wallet wallet = new Bip39Wallet("empty", mnemonic);
                        mDialog.dismiss();
                        mActivity.onWalletImported(wallet);
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(mActivity, e.getMessage(), Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            });
        });
    }

    void show() {
        mDialog.show();
    }
}
