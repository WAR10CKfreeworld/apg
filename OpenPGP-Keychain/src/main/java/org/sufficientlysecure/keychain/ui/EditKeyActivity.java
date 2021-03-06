/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010-2014 Thialfihar <thi@thialfihar.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thialfihar.android.apg.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.devspark.appmsg.AppMsg;

import org.thialfihar.android.apg.Constants;
import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.helper.ExportHelper;
import org.thialfihar.android.apg.pgp.Key;
import org.thialfihar.android.apg.pgp.KeyRing;
import org.thialfihar.android.apg.pgp.PgpKeyHelper;
import org.thialfihar.android.apg.pgp.exception.PgpGeneralException;
import org.thialfihar.android.apg.provider.KeychainContract;
import org.thialfihar.android.apg.provider.ProviderHelper;
import org.thialfihar.android.apg.service.ApgIntentService;
import org.thialfihar.android.apg.service.ApgIntentServiceHandler;
import org.thialfihar.android.apg.service.PassphraseCacheService;
import org.thialfihar.android.apg.service.SaveKeyringParcel;
import org.thialfihar.android.apg.ui.dialog.DeleteKeyDialogFragment;
import org.thialfihar.android.apg.ui.dialog.PassphraseDialogFragment;
import org.thialfihar.android.apg.ui.dialog.SetPassphraseDialogFragment;
import org.thialfihar.android.apg.ui.widget.Editor;
import org.thialfihar.android.apg.ui.widget.Editor.EditorListener;
import org.thialfihar.android.apg.ui.widget.KeyEditor;
import org.thialfihar.android.apg.ui.widget.SectionView;
import org.thialfihar.android.apg.ui.widget.UserIdEditor;
import org.thialfihar.android.apg.util.Log;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Vector;

public class EditKeyActivity extends ActionBarActivity implements EditorListener {

    // Actions for internal use only:
    public static final String ACTION_CREATE_KEY = Constants.INTENT_PREFIX + "CREATE_KEY";
    public static final String ACTION_EDIT_KEY = Constants.INTENT_PREFIX + "EDIT_KEY";

    // possible extra keys
    public static final String EXTRA_USER_IDS = "user_ids";
    public static final String EXTRA_NO_PASSPHRASE = "no_passphrase";
    public static final String EXTRA_GENERATE_DEFAULT_KEYS = "generate_default_keys";

    // EDIT
    private Uri mDataUri;

    private ProviderHelper mProvider;

    private KeyRing mKeyRing = null;

    private SectionView mUserIdsView;
    private SectionView mKeysView;

    private String mCurrentPassphrase = null;
    private String mNewPassphrase = null;
    private String mSavedNewPassphrase = null;
    private boolean mIsPassphraseSet;
    private boolean mNeedsSaving;
    private boolean mIsBrandNewKeyring = false;

    private BootstrapButton mChangePassphrase;

    private CheckBox mNoPassphrase;

    private Vector<String> mUserIds;
    private Vector<Key> mKeys;
    private Vector<Integer> mKeysUsages;
    private boolean mMasterCanSign;

    private ExportHelper mExportHelper;

    public boolean needsSaving() {
        mNeedsSaving = (mUserIdsView == null) ? false : mUserIdsView.needsSaving();
        mNeedsSaving |= (mKeysView == null) ? false : mKeysView.needsSaving();
        mNeedsSaving |= hasPassphraseChanged();
        mNeedsSaving |= mIsBrandNewKeyring;
        return mNeedsSaving;
    }


    public void somethingChanged() {
        ActivityCompat.invalidateOptionsMenu(this);
    }

    public void onDeleted(Editor e, boolean wasNewItem) {
        somethingChanged();
    }

    public void onEdited() {
        somethingChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportHelper = new ExportHelper(this);
        mProvider = new ProviderHelper(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setIcon(android.R.color.transparent);
        getSupportActionBar().setHomeButtonEnabled(true);

        mUserIds = new Vector<String>();
        mKeys = new Vector<Key>();
        mKeysUsages = new Vector<Integer>();

        mMasterCanSign = true;

        // Catch Intents opened from other apps
        Intent intent = getIntent();
        String action = intent.getAction();
        if (ACTION_CREATE_KEY.equals(action)) {
            handleActionCreateKey(intent);
        } else if (ACTION_EDIT_KEY.equals(action)) {
            handleActionEditKey(intent);
        }
    }

    /**
     * Handle intent action to create new key
     *
     * @param intent
     */
    private void handleActionCreateKey(Intent intent) {
        Bundle extras = intent.getExtras();

        mCurrentPassphrase = "";
        mIsBrandNewKeyring = true;

        if (extras != null) {
            // if userId is given, prefill the fields
            if (extras.containsKey(EXTRA_USER_IDS)) {
                Log.d(Constants.TAG, "UserIds are given!");
                mUserIds.add(extras.getString(EXTRA_USER_IDS));
            }

            // if no passphrase is given
            if (extras.containsKey(EXTRA_NO_PASSPHRASE)) {
                boolean noPassphrase = extras.getBoolean(EXTRA_NO_PASSPHRASE);
                if (noPassphrase) {
                    // check "no passphrase" checkbox and remove button
                    mNoPassphrase.setChecked(true);
                    mChangePassphrase.setVisibility(View.GONE);
                }
            }

            // generate key
            if (extras.containsKey(EXTRA_GENERATE_DEFAULT_KEYS)) {
                boolean generateDefaultKeys = extras.getBoolean(EXTRA_GENERATE_DEFAULT_KEYS);
                if (generateDefaultKeys) {

                    // Send all information needed to service generate keys in other thread
                    final Intent serviceIntent = new Intent(this, ApgIntentService.class);
                    serviceIntent.setAction(ApgIntentService.ACTION_GENERATE_DEFAULT_RSA_KEYS);

                    // fill values for this action
                    Bundle data = new Bundle();
                    data.putString(ApgIntentService.GENERATE_KEY_SYMMETRIC_PASSPHRASE,
                            mCurrentPassphrase);

                    serviceIntent.putExtra(ApgIntentService.EXTRA_DATA, data);

                    // Message is received after generating is done in ApgService
                    ApgIntentServiceHandler saveHandler = new ApgIntentServiceHandler(
                            this, getResources().getQuantityString(R.plurals.progress_generating, 1),
                            ProgressDialog.STYLE_HORIZONTAL, true,

                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    // Stop key generation on cancel
                                    stopService(serviceIntent);
                                    EditKeyActivity.this.setResult(Activity.RESULT_CANCELED);
                                    EditKeyActivity.this.finish();
                                }
                            }) {

                        @Override
                        public void handleMessage(Message message) {
                            // handle messages by standard ApgHandler first
                            super.handleMessage(message);

                            if (message.arg1 == ApgIntentServiceHandler.MESSAGE_OKAY) {
                                // get new key from data bundle returned from service
                                Bundle data = message.getData();

                                ArrayList<Key> newKeys = (ArrayList<Key>)
                                    data.getSerializable(ApgIntentService.RESULT_NEW_KEY);

                                ArrayList<Integer> keyUsageFlags = data.getIntegerArrayList(
                                        ApgIntentService.RESULT_KEY_USAGES);

                                if (newKeys.size() == keyUsageFlags.size()) {
                                    for (int i = 0; i < newKeys.size(); ++i) {
                                        mKeys.add(newKeys.get(i));
                                        mKeysUsages.add(keyUsageFlags.get(i));
                                    }
                                }

                                buildLayout(true);
                            }
                        }
                    };

                    // Create a new Messenger for the communication back
                    Messenger messenger = new Messenger(saveHandler);
                    serviceIntent.putExtra(ApgIntentService.EXTRA_MESSENGER, messenger);

                    saveHandler.showProgressDialog(this);

                    // start service with intent
                    startService(serviceIntent);
                }
            }
        } else {
            buildLayout(false);
        }
    }

    /**
     * Handle intent action to edit existing key
     *
     * @param intent
     */
    private void handleActionEditKey(Intent intent) {
        mDataUri = intent.getData();
        if (mDataUri == null) {
            Log.e(Constants.TAG, "Intent data missing. Should be Uri of key!");
            finish();
        } else {
            Log.d(Constants.TAG, "uri: " + mDataUri);

            // get master key id using row id
            KeyRing keyRing = mProvider.getKeyRing(mDataUri);
            Key masterKey = keyRing.getMasterKey();
            long masterKeyId = masterKey.getKeyId();

            mMasterCanSign = ProviderHelper.getMasterKeyCanCertify(this, mDataUri);
            finallyEdit(masterKeyId);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.key_edit, menu);
        //totally get rid of some actions for new keys
        if (mDataUri == null) {
            MenuItem mButton = menu.findItem(R.id.menu_key_edit_export_file);
            mButton.setVisible(false);
            mButton = menu.findItem(R.id.menu_key_edit_delete);
            mButton.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                cancelClicked();
                // TODO: why isn't this triggered on my tablet - one of many ui problems
                // I've had with this device. A code compatibility issue or a Samsung fail?
                return true;
            case R.id.menu_key_edit_cancel:
                cancelClicked();
                return true;
            case R.id.menu_key_edit_export_file:
                if (needsSaving()) {
                    Toast.makeText(this, R.string.error_save_first, Toast.LENGTH_LONG).show();
                } else {
                    long masterKeyId = ProviderHelper.getMasterKeyId(this, mDataUri);
                    long[] ids = new long[] {masterKeyId};
                    mExportHelper.showExportKeysDialog(
                        ids, Id.type.secret_key, Constants.Path.APP_DIR_FILE_SEC, null);
                    return true;
                }
                return true;
            case R.id.menu_key_edit_delete:
                long rowId = ProviderHelper.getRowId(this, mDataUri);
                Uri convertUri = KeychainContract.KeyRings.buildSecretKeyRingsUri(Long.toString(rowId));
                // Message is received after key is deleted
                Handler returnHandler = new Handler() {
                    @Override
                    public void handleMessage(Message message) {
                        if (message.what == DeleteKeyDialogFragment.MESSAGE_OKAY) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }
                };
                mExportHelper.deleteKey(convertUri, returnHandler);
                return true;

            case R.id.menu_key_edit_save:
                saveClicked();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("unchecked")
    private void finallyEdit(final long masterKeyId) {
        if (masterKeyId != 0) {
            Key masterKey = null;
            mKeyRing = mProvider.getSecretKeyRingByMasterKeyId(masterKeyId);
            if (mKeyRing != null) {
                masterKey = mKeyRing.getMasterKey();
                for (Key key : mKeyRing.getSecretKeys()) {
                    mKeys.add(key);
                    mKeysUsages.add(-1); // get usage when view is created
                }
            } else {
                Log.e(Constants.TAG, "Keyring not found with masterKeyId: " + masterKeyId);
                Toast.makeText(this, R.string.error_no_secret_key_found, Toast.LENGTH_LONG).show();
            }
            if (masterKey != null) {
                boolean isSet = false;
                for (String userId : masterKey.getUserIds()) {
                    Log.d(Constants.TAG, "Added userId " + userId);
                    if (!isSet) {
                        isSet = true;
                        String[] parts = PgpKeyHelper.splitUserId(userId);
                        if (parts[0] != null) {
                            setTitle(parts[0]);
                        }
                    }
                    mUserIds.add(userId);
                }
            }
        }

        mCurrentPassphrase = "";

        buildLayout(false);
        mIsPassphraseSet = PassphraseCacheService.hasPassphrase(this, masterKeyId);
        if (!mIsPassphraseSet) {
            // check "no passphrase" checkbox and remove button
            mNoPassphrase.setChecked(true);
            mChangePassphrase.setVisibility(View.GONE);
        }
    }

    /**
     * Shows the dialog to set a new passphrase
     */
    private void showSetPassphraseDialog() {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == SetPassphraseDialogFragment.MESSAGE_OKAY) {
                    Bundle data = message.getData();

                    // set new returned passphrase!
                    mNewPassphrase = data
                            .getString(SetPassphraseDialogFragment.MESSAGE_NEW_PASSPHRASE);

                    updatePassphraseButtonText();
                    somethingChanged();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        // set title based on isPassphraseSet()
        int title;
        if (isPassphraseSet()) {
            title = R.string.title_change_passphrase;
        } else {
            title = R.string.title_set_passphrase;
        }

        SetPassphraseDialogFragment setPassphraseDialog = SetPassphraseDialogFragment.newInstance(
                messenger, title);

        setPassphraseDialog.show(getSupportFragmentManager(), "setPassphraseDialog");
    }

    /**
     * Build layout based on mUserId, mKeys and mKeysUsages Vectors. It creates Views for every user
     * id and key.
     *
     * @param newKeys
     */
    private void buildLayout(boolean newKeys) {
        setContentView(R.layout.edit_key_activity);

        // find views
        mChangePassphrase = (BootstrapButton) findViewById(R.id.edit_key_btn_change_passphrase);
        mNoPassphrase = (CheckBox) findViewById(R.id.edit_key_no_passphrase);
        // Build layout based on given userIds and keys

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        LinearLayout container = (LinearLayout) findViewById(R.id.edit_key_container);
        if (mIsPassphraseSet) {
            mChangePassphrase.setText(getString(R.string.btn_change_passphrase));
        }
        mUserIdsView = (SectionView) inflater.inflate(R.layout.edit_key_section, container, false);
        mUserIdsView.setType(Id.type.user_id);
        mUserIdsView.setCanBeEdited(mMasterCanSign);
        mUserIdsView.setUserIds(mUserIds);
        mUserIdsView.setEditorListener(this);
        container.addView(mUserIdsView);
        mKeysView = (SectionView) inflater.inflate(R.layout.edit_key_section, container, false);
        mKeysView.setType(Id.type.key);
        mKeysView.setCanBeEdited(mMasterCanSign);
        mKeysView.setKeys(mKeys, mKeysUsages, newKeys);
        mKeysView.setEditorListener(this);
        container.addView(mKeysView);

        updatePassphraseButtonText();

        mChangePassphrase.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showSetPassphraseDialog();
            }
        });

        // disable passphrase when no passphrase checkbox is checked!
        mNoPassphrase.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // remove passphrase
                    mSavedNewPassphrase = mNewPassphrase;
                    mNewPassphrase = "";
                    mChangePassphrase.setVisibility(View.GONE);
                } else {
                    mNewPassphrase = mSavedNewPassphrase;
                    mChangePassphrase.setVisibility(View.VISIBLE);
                }
                somethingChanged();
            }
        });
    }

    private long getMasterKeyId() {
        if (mKeysView.getEditors().getChildCount() == 0) {
            return 0;
        }
        return ((KeyEditor) mKeysView.getEditors().getChildAt(0)).getValue().getKeyId();
    }

    public boolean isPassphraseSet() {
        if (mNoPassphrase.isChecked()) {
            return true;
        } else if (mIsPassphraseSet || (mNewPassphrase != null && !mNewPassphrase.equals(""))) {
            return true;
        } else {
            return false;
        }
    }

    public boolean hasPassphraseChanged() {
        if (mNoPassphrase != null) {
            if (mNoPassphrase.isChecked()) {
                return mIsPassphraseSet;
            } else {
                return mNewPassphrase != null && !mNewPassphrase.equals("");
            }
        } else {
            return false;
        }
    }

    private void saveClicked() {
        final long masterKeyId = getMasterKeyId();
        if (needsSaving()) { //make sure, as some versions don't support invalidateOptionsMenu
            try {
                if (!isPassphraseSet()) {
                    throw new PgpGeneralException(this.getString(R.string.set_a_passphrase));
                }

                String passphrase;
                if (mIsPassphraseSet) {
                    passphrase = PassphraseCacheService.getCachedPassphrase(this, masterKeyId);
                } else {
                    passphrase = "";
                }
                if (passphrase == null) {
                    PassphraseDialogFragment.show(this, masterKeyId,
                        new Handler() {
                            @Override
                            public void handleMessage(Message message) {
                                if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                                    mCurrentPassphrase = PassphraseCacheService.getCachedPassphrase(
                                            EditKeyActivity.this, masterKeyId);
                                    checkEmptyIDsWanted();
                                }
                            }
                        });
                } else {
                    mCurrentPassphrase = passphrase;
                    checkEmptyIDsWanted();
                }
            } catch (PgpGeneralException e) {
                Toast.makeText(this, getString(R.string.error_message, e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            AppMsg.makeText(this, R.string.error_change_something_first, AppMsg.STYLE_ALERT).show();
        }
    }

    private void checkEmptyIDsWanted() {
        try {
            ArrayList<String> userIDs = getUserIds(mUserIdsView);
            List<Boolean> newIDs = mUserIdsView.getNewIDFlags();
            ArrayList<String> originalIDs = mUserIdsView.getOriginalIDs();
            int curID = 0;
            for (String userID : userIDs) {
                if (userID.equals("") && (!userID.equals(originalIDs.get(curID)) || newIDs.get(curID))) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(
                            EditKeyActivity.this);

                    alert.setIcon(android.R.drawable.ic_dialog_alert);
                    alert.setTitle(R.string.warning);
                    alert.setMessage(EditKeyActivity.this.getString(R.string.ask_empty_id_ok));

                    alert.setPositiveButton(EditKeyActivity.this.getString(android.R.string.yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                    finallySaveClicked();
                                }
                            }
                    );
                    alert.setNegativeButton(this.getString(android.R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            }
                    );
                    alert.setCancelable(false);
                    alert.create().show();
                    return;
                }
                curID++;
            }
        } catch (PgpGeneralException e) {
            Log.e(Constants.TAG, getString(R.string.error_message, e.getMessage()));
            Toast.makeText(this, getString(R.string.error_message, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
        finallySaveClicked();
    }

    private boolean[] toPrimitiveArray(final List<Boolean> booleanList) {
        final boolean[] primitives = new boolean[booleanList.size()];
        int index = 0;
        for (Boolean object : booleanList) {
            primitives[index++] = object;
        }
        return primitives;
    }

    private void finallySaveClicked() {
        try {
            // Send all information needed to service to edit key in other thread
            Intent intent = new Intent(this, ApgIntentService.class);

            intent.setAction(ApgIntentService.ACTION_SAVE_KEYRING);

            SaveKeyringParcel saveParams = new SaveKeyringParcel();
            saveParams.userIDs = getUserIds(mUserIdsView);
            saveParams.originalIDs = mUserIdsView.getOriginalIDs();
            saveParams.deletedIDs = mUserIdsView.getDeletedIDs();
            saveParams.newIDs = toPrimitiveArray(mUserIdsView.getNewIDFlags());
            saveParams.primaryIDChanged = mUserIdsView.primaryChanged();
            saveParams.moddedKeys = toPrimitiveArray(mKeysView.getNeedsSavingArray());
            saveParams.deletedKeys = mKeysView.getDeletedKeys();
            saveParams.keysExpiryDates = getKeysExpiryDates(mKeysView);
            saveParams.keysUsages = getKeysUsages(mKeysView);
            saveParams.newPassphrase = mNewPassphrase;
            saveParams.oldPassphrase = mCurrentPassphrase;
            saveParams.newKeys = toPrimitiveArray(mKeysView.getNewKeysArray());
            saveParams.keys = getKeys(mKeysView);
            saveParams.originalPrimaryID = mUserIdsView.getOriginalPrimaryID();


            // fill values for this action
            Bundle data = new Bundle();
            data.putBoolean(ApgIntentService.SAVE_KEYRING_CAN_SIGN, mMasterCanSign);
            data.putParcelable(ApgIntentService.SAVE_KEYRING_PARCEL, saveParams);

            intent.putExtra(ApgIntentService.EXTRA_DATA, data);

            // Message is received after saving is done in ApgService
            ApgIntentServiceHandler saveHandler = new ApgIntentServiceHandler(this,
                    getString(R.string.progress_saving), ProgressDialog.STYLE_HORIZONTAL) {
                public void handleMessage(Message message) {
                    // handle messages by standard ApgHandler first
                    super.handleMessage(message);

                    if (message.arg1 == ApgIntentServiceHandler.MESSAGE_OKAY) {
                        Intent data = new Intent();

                        // return uri pointing to new created key
                        Uri uri = KeychainContract.KeyRings.buildPublicKeyRingsByKeyIdUri(
                                String.valueOf(getMasterKeyId()));
                        data.setData(uri);

                        setResult(RESULT_OK, data);
                        finish();
                    }
                }
            };

            // Create a new Messenger for the communication back
            Messenger messenger = new Messenger(saveHandler);
            intent.putExtra(ApgIntentService.EXTRA_MESSENGER, messenger);

            saveHandler.showProgressDialog(this);

            // start service with intent
            startService(intent);
        } catch (PgpGeneralException e) {
            Log.e(Constants.TAG, getString(R.string.error_message, e.getMessage()));
            Toast.makeText(this, getString(R.string.error_message, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelClicked() {
        if (needsSaving()) { //ask if we want to save
            AlertDialog.Builder alert = new AlertDialog.Builder(
                    EditKeyActivity.this);

            alert.setIcon(android.R.drawable.ic_dialog_alert);
            alert.setTitle(R.string.warning);
            alert.setMessage(EditKeyActivity.this.getString(R.string.ask_save_changed_key));

            alert.setPositiveButton(EditKeyActivity.this.getString(android.R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            saveClicked();
                        }
                    });
            alert.setNegativeButton(this.getString(android.R.string.no),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    });
            alert.setCancelable(false);
            alert.create().show();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /**
     * Returns user ids from the SectionView
     *
     * @param userIdsView
     * @return
     */
    private ArrayList<String> getUserIds(SectionView userIdsView) throws PgpGeneralException {
        ArrayList<String> userIds = new ArrayList<String>();

        ViewGroup userIdEditors = userIdsView.getEditors();

        for (int i = 0; i < userIdEditors.getChildCount(); ++i) {
            UserIdEditor editor = (UserIdEditor) userIdEditors.getChildAt(i);
            String userId = null;
            userId = editor.getValue();

            userIds.add(userId);
        }

        if (userIds.size() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_a_user_id));
        }

        return userIds;
    }

    /**
     * Returns keys from the SectionView
     *
     * @param keysView
     * @return
     */
    private ArrayList<Key> getKeys(SectionView keysView) throws PgpGeneralException {
        ArrayList<Key> keys = new ArrayList<Key>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keys.add(editor.getValue());
        }

        return keys;
    }

    /**
     * Returns usage selections of keys from the SectionView
     *
     * @param keysView
     * @return
     */
    private ArrayList<Integer> getKeysUsages(SectionView keysView) throws PgpGeneralException {
        ArrayList<Integer> keysUsages = new ArrayList<Integer>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keysUsages.add(editor.getUsage());
        }

        return keysUsages;
    }

    private ArrayList<GregorianCalendar> getKeysExpiryDates(SectionView keysView) throws PgpGeneralException {
        ArrayList<GregorianCalendar> keysExpiryDates = new ArrayList<GregorianCalendar>();

        ViewGroup keyEditors = keysView.getEditors();

        if (keyEditors.getChildCount() == 0) {
            throw new PgpGeneralException(getString(R.string.error_key_needs_master_key));
        }

        for (int i = 0; i < keyEditors.getChildCount(); ++i) {
            KeyEditor editor = (KeyEditor) keyEditors.getChildAt(i);
            keysExpiryDates.add(editor.getExpiryDate());
        }

        return keysExpiryDates;
    }

    private void updatePassphraseButtonText() {
        mChangePassphrase.setText(isPassphraseSet() ? getString(R.string.btn_change_passphrase)
                : getString(R.string.btn_set_passphrase));
    }

}
