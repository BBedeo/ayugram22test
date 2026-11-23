package app.exteraless.updater;

import android.text.TextUtils;

import org.telegram.messenger.SharedConfig;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Sends the updater's HTTP traffic (release check and APK download) through the SOCKS5 proxy
 * the user already configured in the app, if one is enabled. Telegram's own MTProto proxies
 * (the ones with a secret) cannot carry plain HTTP, so they are ignored and the updater goes
 * direct. The proxy is read at request time, so changing it needs no restart.
 */
final class UpdaterProxy {

    private static volatile boolean authInstalled;

    private UpdaterProxy() {
    }

    /** The proxy to use right now, or null for a direct connection. */
    static SharedConfig.ProxyInfo active() {
        try {
            SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
            if (proxy != null && SharedConfig.isProxyEnabled()
                    && TextUtils.isEmpty(proxy.secret)
                    && !TextUtils.isEmpty(proxy.address) && proxy.port > 0) {
                return proxy;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static ProxySelector selector() {
        installAuthenticator();
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                SharedConfig.ProxyInfo proxy = active();
                if (proxy == null) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
                return Collections.singletonList(new Proxy(Proxy.Type.SOCKS,
                        InetSocketAddress.createUnresolved(proxy.address, proxy.port)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            }
        };
    }

    /**
     * SOCKS5 user/password login goes through java.net.Authenticator. It answers only for the
     * configured proxy host, so nothing else in the process is affected.
     */
    private static void installAuthenticator() {
        if (authInstalled) {
            return;
        }
        synchronized (UpdaterProxy.class) {
            if (authInstalled) {
                return;
            }
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    SharedConfig.ProxyInfo proxy = active();
                    if (proxy == null || TextUtils.isEmpty(proxy.username)
                            || getRequestingHost() == null
                            || !getRequestingHost().equalsIgnoreCase(proxy.address)) {
                        return null;
                    }
                    return new PasswordAuthentication(proxy.username,
                            (proxy.password == null ? "" : proxy.password).toCharArray());
                }
            });
            authInstalled = true;
        }
    }
}
