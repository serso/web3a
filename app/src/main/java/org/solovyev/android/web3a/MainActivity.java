package org.solovyev.android.web3a;

import static org.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT;
import static org.web3j.tx.gas.DefaultGasProvider.GAS_PRICE;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Bip39Wallet;
import org.web3j.crypto.Bip44WalletUtils;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Nullable
    private Credentials mCredentials;

    private TextView mMnemonic;
    private TextView mAddress;
    private TextView mBalance;
    private TextView mSignature;
    private TextView mEnsNameToAddress;
    private View mSendEth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMnemonic = findViewById(R.id.mnemonic);
        mMnemonic.setOnClickListener(this);

        mAddress = findViewById(R.id.address);
        mAddress.setOnClickListener(this);

        mBalance = findViewById(R.id.balance);
        mBalance.setOnClickListener(this);

        mSignature = findViewById(R.id.signature);
        mSignature.setOnClickListener(this);

        mSendEth = findViewById(R.id.send_eth);
        mSendEth.setOnClickListener(this);

        mEnsNameToAddress = findViewById(R.id.ens_name_to_address);
        mEnsNameToAddress.setOnClickListener(this);

        new LoadWalletTask(this).execute();
        new EnsResolveNameToAddressTask(this).execute();
    }

    private void onWalletLoaded(@Nullable Bip39Wallet wallet) {
        if (wallet == null) {
            mMnemonic.setError("No wallet");
            return;
        }
        mMnemonic.setText(wallet.getMnemonic());
        new LoadCredentialsTask(this, wallet).execute();
    }

    private void onEnsNameResolved(@NonNull String name, @NonNull String address) {
        mEnsNameToAddress.setText(
                String.format("%s => %s", name, TextUtils.isEmpty(address) ? "N/A" : address));
    }

    private void onCredentialsLoaded(@NonNull Credentials credentials) {
        mCredentials = credentials;
        mSendEth.setEnabled(true);
        mAddress.setText(credentials.getAddress());
        new GetBalanceTask(this, credentials).execute();
        new SignMessageTask(this, credentials).execute();
    }

    private void onBalanceReceived(@Nullable BigInteger balance) {
        mBalance.setText(balance == null ? "N/A" : Convert.fromWei(new BigDecimal(balance), Convert.Unit.ETHER).toPlainString() + "ETH");
    }

    private void onMessageSigned(@NonNull String message, @NonNull Sign.SignatureData sig) {
        final byte[] result = new byte[65];
        System.arraycopy(sig.getR(), 0, result, 0, 32);
        System.arraycopy(sig.getS(), 0, result, 32, 32);
        result[64] = sig.getV();
        mSignature
                .setText(String.format("Msg = %s\nSig = %s", message, Numeric.toHexString(result)));
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.send_eth) {
            if (mCredentials == null) return;
            Toast.makeText(this, "Sending 0.01ETH", Toast.LENGTH_SHORT).show();
            new SendEthTask(mCredentials, this).execute();
            return;
        }
        if (v instanceof TextView) {
            final ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            final CharSequence text = ((TextView) v).getText();
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private abstract static class BaseTask<R> extends AsyncTask<Void, Void, R> {
        @SuppressLint("StaticFieldLeak")
        @NonNull
        final App mApp;
        @NonNull
        final WeakReference<MainActivity> mActivity;

        private BaseTask(@NonNull MainActivity activity) {
            mApp = App.get(activity);
            mActivity = new WeakReference<>(activity);
        }

        @Override
        protected final void onPostExecute(@Nullable R result) {
            super.onPostExecute(result);
            final MainActivity activity = mActivity.get();
            if (activity != null) {
                handleResult(activity, result);
            }
        }

        protected abstract void handleResult(@NonNull MainActivity activity, R result);

        final void handleError(@NonNull Exception e) {
            Log.e(App.TAG, e.getMessage(), e);
            final MainActivity activity = mActivity.get();
            if (activity == null) return;
            activity.runOnUiThread(
                    () -> Toast.makeText(mApp, e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private static class LoadCredentialsTask extends BaseTask<Credentials> {
        @NonNull
        private final Bip39Wallet mWallet;

        private LoadCredentialsTask(@NonNull MainActivity activity,
                                    @NonNull Bip39Wallet wallet) {
            super(activity);
            mWallet = wallet;
        }

        @NonNull
        @Override
        protected Credentials doInBackground(Void... voids) {
            final Credentials credentials =
                    Bip44WalletUtils.loadBip44Credentials(App.PASSWORD, mWallet.getMnemonic());
            // m/44'/60'/0'/0
            final Bip32ECKeyPair keyPair = (Bip32ECKeyPair) credentials.getEcKeyPair();
            // m/44'/60'/0'/0/0
            return Credentials.create(Bip32ECKeyPair.deriveKeyPair(keyPair, new int[]{0}));
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity,
                                    @NonNull Credentials credentials) {
            activity.onCredentialsLoaded(credentials);
        }
    }

    private static class SignMessageTask extends BaseTask<Sign.SignatureData> {
        @NonNull
        private final Credentials mCredentials;
        @NonNull
        private final String mMessage = "test";

        private SignMessageTask(@NonNull MainActivity activity,
                                @NonNull Credentials credentials) {
            super(activity);
            mCredentials = credentials;
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity,
                                    @NonNull Sign.SignatureData signature) {
            activity.onMessageSigned(mMessage, signature);
        }

        @NonNull
        @Override
        protected Sign.SignatureData doInBackground(Void... voids) {
            final String hash = Hash.sha3(Numeric.toHexString(mMessage.getBytes()));
            return Sign.signPrefixedMessage(Numeric.hexStringToByteArray(hash),
                    mCredentials.getEcKeyPair());
        }
    }

    private static class GetBalanceTask extends BaseTask<BigInteger> {
        @NonNull
        private final Credentials mCredentials;

        private GetBalanceTask(@NonNull MainActivity activity,
                               @NonNull Credentials credentials) {
            super(activity);
            mCredentials = credentials;
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @Nullable BigInteger balance) {
            activity.onBalanceReceived(balance);
        }

        @Override
        protected BigInteger doInBackground(Void... voids) {
            final Web3j web3j = mApp.getWeb3j();
            try {
                return web3j
                        .ethGetBalance(mCredentials.getAddress(), DefaultBlockParameterName.LATEST)
                        .send().getBalance();
            } catch (IOException | RuntimeException e) {
                handleError(e);
            }
            return null;
        }
    }

    private static class SendEthTask extends BaseTask<String> {
        @NonNull
        private final Credentials mCredentials;

        private SendEthTask(@NonNull Credentials credentials, @NonNull MainActivity activity) {
            super(activity);
            mCredentials = credentials;
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @NonNull String result) {

        }

        @NonNull
        @Override
        protected String doInBackground(Void... voids) {
            final Web3j web3j = mApp.getWeb3j();
            final String address = mCredentials.getAddress();
            try {
                final BigInteger nonce =
                        web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST)
                                .send().getTransactionCount();
                final BigInteger value = Convert.toWei("0.01", Convert.Unit.ETHER).toBigInteger();

                final RawTransaction tx = RawTransaction
                        .createEtherTransaction(nonce, GAS_PRICE, GAS_LIMIT, address, value);
                final byte[] signed = TransactionEncoder.signMessage(tx, App.CHAIN_ID, mCredentials);
                final String hexed = Numeric.toHexString(signed);

                return web3j.ethSendRawTransaction(hexed).send().getTransactionHash();
            } catch (IOException | RuntimeException e) {
                handleError(e);
            }
            return "";
        }
    }

    private static class EnsResolveNameToAddressTask extends BaseTask<String> {
        @NonNull
        private final String mName = "michalzalecki.test";

        private EnsResolveNameToAddressTask(@NonNull MainActivity activity) {
            super(activity);
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @NonNull String address) {
            activity.onEnsNameResolved(mName, address);
        }

        @NonNull
        @Override
        protected String doInBackground(Void... voids) {
            try {
                final String address = mApp.getEnsResolver().resolve(mName);
                return address == null ? "" : address;
            } catch (RuntimeException e) {
                handleError(e);
            }
            return "";
        }
    }

    private static class LoadWalletTask extends BaseTask<Bip39Wallet> {

        private LoadWalletTask(@NonNull MainActivity activity) {
            super(activity);
        }

        @Override
        protected void handleResult(@NonNull MainActivity activity, @Nullable Bip39Wallet wallet) {
            activity.onWalletLoaded(wallet);
        }

        @Nullable
        @Override
        protected Bip39Wallet doInBackground(Void... voids) {
            final File dir = mApp.getFilesDir();
            try {
                final SharedPreferences prefs = mApp.getPrefs();
                final String filename = prefs.getString("filename", "");
                final String mnemonic = prefs.getString("mnemonic", "");
                if (TextUtils.isEmpty(filename) || TextUtils.isEmpty(mnemonic)) {
                    return generate(dir);
                }
                // validate
                Bip44WalletUtils.loadBip44Credentials(App.PASSWORD, mnemonic);
                return new Bip39Wallet(filename, mnemonic);
            } catch (CipherException | IOException e) {
                handleError(e);
            }
            return null;
        }

        @NonNull
        private Bip39Wallet generate(@NonNull File dir) throws CipherException, IOException {
            final Bip39Wallet wallet = Bip44WalletUtils.generateBip44Wallet(App.PASSWORD, dir);
            final File file = new File(dir, wallet.getFilename());
            if (!file.exists()) throw new IOException("No file created");

            final SharedPreferences prefs = mApp.getPrefs();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("filename", wallet.getFilename());
            editor.putString("mnemonic", wallet.getMnemonic());
            editor.apply();

            return wallet;
        }
    }
}
