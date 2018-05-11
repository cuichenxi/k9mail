package org.openintents.openpgp;


import android.app.PendingIntent;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.Lifecycle.Event;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpServiceConnection.OnBound;
import timber.log.Timber;


public class OpenPgpApiManager implements LifecycleObserver {
    private final Context context;

    @Nullable
    private String openPgpProvider;
    @Nullable
    private OpenPgpApiManagerCallback callback;

    private OpenPgpServiceConnection openPgpServiceConnection;
    private OpenPgpApi openPgpApi;
    private PendingIntent userInteractionPendingIntent;
    private OpenPgpProviderState openPgpProviderState = OpenPgpProviderState.UNCONFIGURED;

    public OpenPgpApiManager(Context context, Lifecycle lifecycle) {
        this.context = context;

        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Event.ON_CREATE)
    void onLifecycleCreate() {
        setupCryptoProvider();
    }

    @OnLifecycleEvent(Event.ON_START)
    void onLifecycleStart() {
        refreshConnection();
    }

    @OnLifecycleEvent(Event.ON_DESTROY)
    public void onLifecycleDestroy() {
        disconnect();
    }

    public void setOpenPgpProvider(@Nullable String openPgpProvider, OpenPgpApiManagerCallback callback) {
        if (openPgpProvider == null || !openPgpProvider.equals(this.openPgpProvider)) {
            disconnect();
        }

        this.openPgpProvider = openPgpProvider;
        this.callback = callback;

        setupCryptoProvider();
    }

    private void setupCryptoProvider() {
        if (TextUtils.isEmpty(openPgpProvider)) {
            setOpenPgpProviderState(OpenPgpProviderState.UNCONFIGURED);
            return;
        }

        boolean providerIsBound = openPgpServiceConnection != null && openPgpServiceConnection.isBound();
        if (providerIsBound) {
            refreshConnection();
            return;
        }

        setOpenPgpProviderState(OpenPgpProviderState.UNINITIALIZED);
        openPgpServiceConnection = new OpenPgpServiceConnection(context, openPgpProvider, new OnBound() {
            @Override
            public void onBound(IOpenPgpService2 service) {
                openPgpApi = new OpenPgpApi(context, service);
                refreshConnection();
            }

            @Override
            public void onError(Exception e) {
                Timber.e(e, "error connecting to crypto provider!");
                setOpenPgpProviderState(OpenPgpProviderState.ERROR);
                callbackOpenPgpProviderError(OpenPgpProviderError.ConnectionFailed);
            }
        });
        refreshConnection();
    }

    public void refreshConnection() {
        boolean isOkStateButLostConnection = openPgpProviderState == OpenPgpProviderState.OK &&
                (openPgpServiceConnection == null || !openPgpServiceConnection.isBound());
        if (isOkStateButLostConnection) {
            userInteractionPendingIntent = null;
            setOpenPgpProviderState(OpenPgpProviderState.ERROR);
            callbackOpenPgpProviderError(OpenPgpProviderError.ConnectionLost);
            return;
        }

        if (openPgpServiceConnection == null) {
            userInteractionPendingIntent = null;
            setupCryptoProvider();
            return;
        }

        if (!openPgpServiceConnection.isBound()) {
            userInteractionPendingIntent = null;
            Timber.d("attempting to bind to openpgp provider: %s (%s)", openPgpProvider, openPgpServiceConnection);
            openPgpServiceConnection.bindToService();
            return;
        }

        if (userInteractionPendingIntent != null) {
            setOpenPgpProviderState(OpenPgpProviderState.UI_REQUIRED);
            return;
        }

        Intent intent = new Intent(OpenPgpApi.ACTION_CHECK_PERMISSION);
        getOpenPgpApi().executeApiAsync(intent, null, null, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                onPgpPermissionCheckResult(result);
            }
        });
    }

    public void onUserInteractionResult() {
        userInteractionPendingIntent = null;
        refreshConnection();
    }

    public PendingIntent getUserInteractionPendingIntent() {
        return userInteractionPendingIntent;
    }

    private void onPgpPermissionCheckResult(Intent result) {
        int resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR);
        switch (resultCode) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                setOpenPgpProviderState(OpenPgpProviderState.OK);
                break;

            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                userInteractionPendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                setOpenPgpProviderState(OpenPgpProviderState.UI_REQUIRED);
                break;

            case OpenPgpApi.RESULT_CODE_ERROR:
            default:
                if (result.hasExtra(OpenPgpApi.RESULT_ERROR)) {
                    OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                    handleOpenPgpError(error);
                } else {
                    setOpenPgpProviderState(OpenPgpProviderState.ERROR);
                    callbackOpenPgpProviderError(OpenPgpProviderError.ConnectionFailed);
                }
                break;
        }
    }

    private void setOpenPgpProviderState(OpenPgpProviderState state) {
        boolean statusChanged = openPgpProviderState != state;
        if (statusChanged) {
            Timber.d("callback provider status changed from %s to %s", openPgpProviderState, state);
            openPgpProviderState = state;
            if (callback != null) {
                callback.onOpenPgpProviderStatusChanged();
            }
        }
    }

    private void handleOpenPgpError(@Nullable OpenPgpError error) {
        Timber.e("OpenPGP Api error: %s", error);

        if (error != null && error.getErrorId() == OpenPgpError.INCOMPATIBLE_API_VERSIONS) {
            callbackOpenPgpProviderError(OpenPgpProviderError.VersionIncompatible);
            setOpenPgpProviderState(OpenPgpProviderState.UNCONFIGURED);
        } else {
            callbackOpenPgpProviderError(OpenPgpProviderError.ConnectionFailed);
            setOpenPgpProviderState(OpenPgpProviderState.ERROR);
        }
    }

    private void callbackOpenPgpProviderError(OpenPgpProviderError providerError) {
        Timber.d("callback provider connection error %s", providerError);
        if (callback != null) {
            callback.onOpenPgpProviderError(providerError);
        }
    }

    private void disconnect() {
        openPgpApi = null;
        if (openPgpServiceConnection != null) {
            openPgpServiceConnection.unbindFromService();
        }
        openPgpServiceConnection = null;
    }

    public OpenPgpApi getOpenPgpApi() {
        if (openPgpServiceConnection == null || !openPgpServiceConnection.isBound()) {
            Timber.e("Obtained OpenPgpApi object, but service is not bound! Inconsistent state?");
        }
        return openPgpApi;
    }

    public OpenPgpProviderState getOpenPgpProviderState() {
        return openPgpProviderState;
    }

    public enum OpenPgpProviderState {
        UNCONFIGURED,
        UNINITIALIZED,
        UI_REQUIRED,
        ERROR,
        OK
    }

    public enum OpenPgpProviderError {
        ConnectionFailed, ConnectionLost, VersionIncompatible
    }

    public interface OpenPgpApiManagerCallback {
        void onOpenPgpProviderStatusChanged();
        void onOpenPgpProviderError(OpenPgpProviderError error);
    }
}
