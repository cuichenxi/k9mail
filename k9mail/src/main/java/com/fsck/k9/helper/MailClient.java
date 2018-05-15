package com.fsck.k9.helper;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.setup.AccountSetupCheckSettings.CheckDirection;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Transport;
import com.fsck.k9.mail.TransportProvider;
import com.fsck.k9.mail.store.RemoteStore;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalStore;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * Created by chenxi.cui on 2018/5/11.
 */

public class MailClient {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(1);
    private static final String TAG = "MailClient";
    private final Context mContext;
    private Account mAccount;
    private CheckDirection mDirection;
    private MailListener mListener;

    public MailClient(Context context, Account account) {
        this.mContext = context;
        this.mDirection = CheckDirection.INCOMING;
        this.mAccount = account;
    }

    public void sysLogin(MailListener listener) {
        synchronized (MailClient.class) {
            this.mListener = listener;
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    login(new MailListener() {
                        @Override
                        public void result(int code, String msg) {
                            if (code == 0) {
                                if (mDirection == CheckDirection.OUTGOING) {
                                    mAccount.setDescription(mAccount.getEmail());
                                    mAccount.save(Preferences.getPreferences(mContext));
                                    K9.setServicesEnabled(mContext);
                                    mListener.result(0, "成功");
                                    return;
                                }
                                mDirection = CheckDirection.OUTGOING;
                                login(new MailListener() {
                                    @Override
                                    public void result(int code, String msg) {
                                        if (code == 0) {
                                            mAccount.setDescription(mAccount.getEmail());
                                            mAccount.save(Preferences.getPreferences(mContext));
                                            K9.setServicesEnabled(mContext);
                                        }
                                        mListener.result(code, msg);
                                    }
                                });
                            } else {
                                mListener.result(code, msg);
                            }
                        }
                    });
                }
            });
        }
    }

    private void login(MailListener listener) {
        int code;
        String msg;
        try {
            clearCertificateErrorNotifications(mAccount, mDirection);
            checkServerSettings(mAccount, mDirection);
            createSpecialLocalFolders(mAccount, mDirection);
            code = 0;
            msg = "成功";
        } catch (AuthenticationFailedException afe) {
            Timber.e(afe, "Error while testing settings");
            msg = mContext.getResources().getString(R.string.account_setup_failed_dlg_auth_message_fmt,
                    afe.getMessage() == null ? "" : afe.getMessage());
            code = 1;
        } catch (CertificateValidationException cve) {
            Timber.e(cve, "Error while testing settings");
            X509Certificate[] chain = cve.getCertChain();
            // Avoid NullPointerException in acceptKeyDialog()
            if (chain != null) {
                try {
                    mAccount.addCertificate(mDirection, chain[0]);
                } catch (CertificateException e) {
                    msg = mContext.getResources().getString(
                            R.string.account_setup_failed_dlg_certificate_message_fmt,
                            e.getMessage() == null ? "" : e.getMessage());
                    code = 3;
                    listener.result(code, msg);
                    return;
                }
                sysLogin(mListener);
                return;
            } else {
                msg = mContext.getResources().getString(
                        R.string.account_setup_failed_dlg_server_message_fmt,
                        errorMessageForCertificateException(cve));
                code = 2;
            }
        } catch (Exception e) {
            Timber.e(e, "Error while testing settings");
            String message = e.getMessage() == null ? "" : e.getMessage();
            msg = mContext.getResources().getString(R.string.account_setup_failed_dlg_server_message_fmt, message);
            code = 4;
        }
        listener.result(code, msg);
    }

    private String errorMessageForCertificateException(CertificateValidationException e) {
        switch (e.getReason()) {
            case Expired:
                return mContext.getResources().getString(R.string.client_certificate_expired, e.getAlias(), e.getMessage());
            case MissingCapability:
                return mContext.getResources().getString(R.string.auth_external_error);
            case RetrievalFailure:
                return mContext.getResources().getString(R.string.client_certificate_retrieval_failure, e.getAlias());
            case UseMessage:
                return e.getMessage();
            case Unknown:
            default:
                return "";
        }
    }

    private void checkServerSettings(Account account, CheckDirection direction) throws MessagingException {
        switch (direction) {
            case INCOMING: {
                checkIncoming(account);
                break;
            }
            case OUTGOING: {
                checkOutgoing(account);
                break;
            }
        }
    }

    private void checkOutgoing(Account account) throws MessagingException {
        Transport transport = TransportProvider.getInstance().getTransport(K9.app, account);
        transport.close();
        try {
            transport.open();
        } finally {
            transport.close();
        }
    }

    private void checkIncoming(Account account) throws MessagingException {
        RemoteStore store = account.getRemoteStore();
        store.checkSettings();

        MessagingController.getInstance(mContext).listFoldersSynchronous(account, true, null);
        MessagingController.getInstance(mContext)
                .synchronizeMailbox(account, account.getInboxFolder(), null, null);
    }

    private void clearCertificateErrorNotifications(Account account, CheckDirection direction) {
        final MessagingController ctrl = MessagingController.getInstance(mContext);
        ctrl.clearCertificateErrorNotifications(account, direction);
    }

    private void createSpecialLocalFolders(Account account, CheckDirection direction) throws MessagingException {
        if (direction != CheckDirection.INCOMING) {
            return;
        }

        LocalStore localStore = account.getLocalStore();
        createLocalFolder(localStore, Account.OUTBOX, mContext.getResources().getString(R.string.special_mailbox_name_outbox));

        if (!account.getStoreUri().startsWith("pop3")) {
            return;
        }

        String draftsFolderInternalId = "Drafts";
        String sentFolderInternalId = "Sent";
        String trashFolderInternalId = "Trash";

        createLocalFolder(localStore, draftsFolderInternalId, mContext.getResources().getString(R.string.special_mailbox_name_drafts));
        createLocalFolder(localStore, sentFolderInternalId, mContext.getResources().getString(R.string.special_mailbox_name_sent));
        createLocalFolder(localStore, trashFolderInternalId, mContext.getResources().getString(R.string.special_mailbox_name_trash));

        account.setDraftsFolder(draftsFolderInternalId);
        account.setSentFolder(sentFolderInternalId);
        account.setTrashFolder(trashFolderInternalId);
    }

    private void createLocalFolder(LocalStore localStore, String internalId, String folderName)
            throws MessagingException {

        LocalFolder folder = localStore.getFolder(internalId);
        if (!folder.exists()) {
            folder.create(Folder.FolderType.HOLDS_MESSAGES);
        }
        folder.setName(folderName);
        folder.setInTopGroup(true);
        folder.setSyncClass(Folder.FolderClass.NONE);
    }

    public interface MailListener {
        void result(int code, String msg);
    }
}

