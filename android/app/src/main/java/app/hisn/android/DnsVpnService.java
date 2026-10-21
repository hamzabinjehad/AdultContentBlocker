package app.hisn.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.LinkProperties;
import android.os.ParcelFileDescriptor;
import android.os.Parcel;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import app.hisn.core.DnsExchange;
import app.hisn.core.DnsPacketKind;
import app.hisn.core.DnsPolicy;
import app.hisn.core.DnsTunnelProbe;
import app.hisn.core.DnsTcpServer;
import app.hisn.core.DomainSeed;
import app.hisn.core.DomainPolicy;
import app.hisn.core.Ipv4DnsPacket;
import app.hisn.core.Ipv4TcpPacket;
import app.hisn.core.TlsDnsTransport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Experimental DNS-only VPN. It intentionally does not advertise always-on support. */
public final class DnsVpnService extends VpnService {
    public enum State { OFF, STARTING, RUNNING, DEGRADED, ERROR }
    public enum CheckState { IDLE, CHECKING, PASSED, FAILED, STALE }
    /** A completed check verifies only this DNS tunnel path, not every app's DNS or traffic. */
    public static final class ConnectionCheck {
        public final CheckState state;
        public final boolean localBlockPassed;
        public final long elapsedMillis;
        public final DnsTunnelProbe.Failure failure;
        public final boolean tcpPassed;

        private ConnectionCheck(CheckState state, boolean localBlockPassed, long elapsedMillis,
                DnsTunnelProbe.Failure failure) {
            this(state, localBlockPassed, elapsedMillis, failure, false);
        }

        private ConnectionCheck(CheckState state, boolean localBlockPassed, long elapsedMillis,
                DnsTunnelProbe.Failure failure, boolean tcpPassed) {
            this.state = state;
            this.localBlockPassed = localBlockPassed;
            this.elapsedMillis = elapsedMillis;
            this.failure = failure;
            this.tcpPassed = tcpPassed;
        }
    }
    private static final String CHANNEL = "dns_filter";
    private static final byte[] SERVER = {10, 111, 0, 2};
    private static final Object CHECK_LOCK = new Object();
    private static DnsVpnService activeService;
    public static volatile ConnectionCheck connectionCheck = idleCheck();
    public static volatile State state = State.OFF;
    public static volatile int domains;
    public static final AtomicLong blocked = new AtomicLong();
    public static final AtomicLong forwarded = new AtomicLong();
    public static final AtomicLong errors = new AtomicLong();
    public static final AtomicLong unsupportedPackets = new AtomicLong();
    public static final AtomicLong malformedPackets = new AtomicLong();
    public static final AtomicLong unsupportedTcpPackets = new AtomicLong();
    public static final AtomicLong networkChanges = new AtomicLong();
    public static final AtomicLong cancelledNetworkRequests = new AtomicLong();
    public static final AtomicLong tcpQueries = new AtomicLong();
    public static final AtomicLong tcpReplies = new AtomicLong();
    public static final AtomicLong udpTruncated = new AtomicLong();
    public static volatile boolean networkMonitoring;
    // Diagnostic category only: never retain a question, answer, hostname or exception message.
    public static volatile String lastDnsFailure = "NONE";
    private volatile Session session;

    private static ConnectionCheck idleCheck() {
        return new ConnectionCheck(CheckState.IDLE, false, 0, DnsTunnelProbe.Failure.NONE);
    }

    /** Debug fixture only: simulate a callback without changing Android network settings. */
    static boolean simulateNetworkChange() {
        if (!BuildConfig.DEBUG) return false;
        synchronized (CHECK_LOCK) {
            Session current = activeService == null ? null : activeService.session;
            if (current == null || current.closed || current.transport == null) return false;
            current.networkChanged();
            return true;
        }
    }

    static boolean simulateOldNetworkFailure() {
        if (!BuildConfig.DEBUG) return false;
        synchronized (CHECK_LOCK) {
            Session current = activeService == null ? null : activeService.session;
            return current != null && current.networkEpoch > 0
                    && current.recordRequestFailure("IO", current.networkEpoch - 1);
        }
    }

    /** Shell-only debug fixture arms an additive, fixed-host lease, never a saved rule. */
    static boolean setBrowserCanary(boolean enabled) {
        if (!BuildConfig.DEBUG) return false;
        synchronized (CHECK_LOCK) {
            Session current = activeService == null ? null : activeService.session;
            if (current == null || current.closed || current.transport == null) return false;
            if (enabled) current.browserTest.arm(); else current.browserTest.clear();
            return true;
        }
    }

    static String browserCanaryStatus() {
        if (!BuildConfig.DEBUG) return "BROWSER_CANARY_UNAVAILABLE";
        synchronized (CHECK_LOCK) {
            Session current = activeService == null ? null : activeService.session;
            return current == null || current.closed ? "BROWSER_CANARY_NO_SESSION" : current.browserTest.status();
        }
    }

    /** Call only after the owner confirms the public example.com test in the UI.
     * No automatic, periodic, protected-socket or fallback check is performed. */
    public static boolean requestConnectionCheck() {
        synchronized (CHECK_LOCK) {
            DnsVpnService service = activeService;
            Session current = service == null ? null : service.session;
            if (current == null || current.closed || (state != State.RUNNING && state != State.DEGRADED)
                    || connectionCheck.state == CheckState.CHECKING) return false;
            try {
                DnsTunnelProbe probe = new DnsTunnelProbe(
                        new InetSocketAddress(InetAddress.getByAddress(SERVER), 53), 3500, DnsTunnelProbe.Protocol.BOTH);
                long selfCheckBefore = current.selfCheckSeen.get();
                long tcpCheckBefore = current.selfCheckTcpSeen.get();
                long networkBefore = current.networkEpoch;
                long failuresBefore = current.failureEpoch;
                current.checkProbe = probe;
                connectionCheck = new ConnectionCheck(CheckState.CHECKING, false, 0, DnsTunnelProbe.Failure.NONE);
                current.checkWorker.execute(() -> {
                    DnsTunnelProbe.Result result;
                    try {
                        result = probe.run();
                    } catch (RuntimeException unavailable) {
                        probe.close();
                        synchronized (CHECK_LOCK) {
                            if (activeService == service && service.session == current && !current.closed
                                    && current.checkProbe == probe) {
                                current.checkProbe = null;
                                connectionCheck = new ConnectionCheck(CheckState.FAILED, false, 0,
                                        DnsTunnelProbe.Failure.BLOCK_CHECK);
                            }
                        }
                        return;
                    }
                    synchronized (CHECK_LOCK) {
                        // An old session/check may not publish after stop, revoke or restart.
                        if (activeService == service && service.session == current && !current.closed
                                && current.checkProbe == probe) {
                            current.checkProbe = null;
                            boolean tcpObserved = current.selfCheckTcpSeen.get() > tcpCheckBefore;
                            boolean observedHere = current.selfCheckSeen.get() > selfCheckBefore && tcpObserved;
                            boolean unchanged = current.networkEpoch == networkBefore && current.failureEpoch == failuresBefore;
                            CheckState checked = result.passed && observedHere
                                    ? (unchanged ? CheckState.PASSED : CheckState.STALE) : CheckState.FAILED;
                            connectionCheck = new ConnectionCheck(checked,
                                    result.localBlockPassed && observedHere, result.elapsedMillis,
                                    result.failure != DnsTunnelProbe.Failure.NONE ? result.failure
                                            : observedHere ? result.failure : DnsTunnelProbe.Failure.BLOCK_CHECK,
                                    result.tcpPassed && tcpObserved);
                        }
                    }
                });
                return true;
            } catch (IOException | RuntimeException unavailable) {
                DnsTunnelProbe pending = current.checkProbe;
                if (pending != null) pending.close();
                current.checkProbe = null;
                connectionCheck = new ConnectionCheck(CheckState.FAILED, false, 0, DnsTunnelProbe.Failure.BLOCK_CHECK);
                return false;
            }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (session != null) return START_NOT_STICKY;
        try {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, getString(R.string.network), NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_hisn).setContentTitle(getString(R.string.dns_notification_title))
                    .setContentText(getString(R.string.dns_notification_body)).setContentIntent(open)
                    .setOngoing(true).build();
            startForeground(1, notification);
            domains = 0; blocked.set(0); forwarded.set(0); errors.set(0); lastDnsFailure = "NONE";
            unsupportedPackets.set(0); malformedPackets.set(0); unsupportedTcpPackets.set(0);
            networkChanges.set(0); networkMonitoring = false;
            cancelledNetworkRequests.set(0);
            tcpQueries.set(0); tcpReplies.set(0); udpTruncated.set(0);
            Session next = new Session();
            synchronized (CHECK_LOCK) {
                state = State.STARTING;
                connectionCheck = idleCheck();
                session = next;
                activeService = this;
            }
            new Thread(next::run, "hisn-dns-reader").start();
        } catch (RuntimeException unavailable) {
            state = State.ERROR;
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onRevoke() {
        state = State.OFF;
        closeSession();
        stopSelf();
    }

    @Override public void onDestroy() {
        closeSession();
        if (state != State.ERROR) state = State.OFF;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private void closeSession() {
        Session current;
        synchronized (CHECK_LOCK) {
            current = session;
            session = null;
            if (activeService == this) {
                activeService = null;
                connectionCheck = idleCheck();
                networkMonitoring = false;
            }
        }
        if (current != null) current.close();
    }

    private final class Session {
        private final AtomicLong selfCheckSeen = new AtomicLong();
        private final AtomicLong selfCheckTcpSeen = new AtomicLong();
        private final DnsTcpServer tcp = new DnsTcpServer();
        private final BrowserTestHooks browserTest = new BrowserTestHooks();
        private volatile boolean closed;
        private volatile ParcelFileDescriptor tunnel;
        private volatile TlsDnsTransport transport;
        private DnsTunnelProbe checkProbe;
        // Accessed under CHECK_LOCK. Old requests must not change current-network health.
        private long networkEpoch;
        private long failureEpoch;
        private final Object monitorLock = new Object();
        private final Map<Network, LinkProperties> linkMarks = new HashMap<>();
        private final Map<Network, Integer> capabilityMarks = new HashMap<>();
        private ConnectivityManager connectivity;
        private ConnectivityManager.NetworkCallback callback;
        // Never wait for a response on a packet-processing worker: this query enters the TUN itself.
        private final ThreadPoolExecutor checkWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), job -> new Thread(job, "hisn-dns-self-check"));
        private final Object outputLock = new Object();
        private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());

        void run() {
            try {
                final DomainSeed seed;
                try (InputStream input = getAssets().open("domains_core.txt")) {
                    seed = DomainSeed.load(input, BuildConfig.DOMAIN_SEED_SHA256, BuildConfig.DOMAIN_SEED_COUNT);
                }
                if (closed) return;
                domains = seed.domainCount;
                ParcelFileDescriptor established = new Builder().setSession(getString(R.string.dns_notification_title))
                        .setMtu(1500).addAddress("10.111.0.1", 32).addDnsServer("10.111.0.2")
                        .addRoute("10.111.0.2", 32).allowFamily(OsConstants.AF_INET6).establish();
                if (established == null) throw new IOException("VPN permission unavailable");
                tunnel = established;
                if (closed) { established.close(); return; }
                PolicyStore settings = PolicyStore.get(DnsVpnService.this);
                DnsPolicy.Rules savedRules = hostname -> {
                    DomainPolicy.Decision custom = settings.snapshot().domains.evaluate(hostname);
                    return custom == DomainPolicy.Decision.ALLOW ? seed.policy.evaluate(hostname) : custom;
                };
                DnsPolicy.Rules rules = hostname -> browserTest.evaluate(hostname, savedRules);
                DnsPolicy policy = new DnsPolicy(hostname -> {
                    // This reserved test name must always be handled locally, regardless of user rules.
                    if (DnsTunnelProbe.BLOCKED_HOST.equalsIgnoreCase(hostname)) {
                        selfCheckSeen.incrementAndGet();
                        return DomainPolicy.Decision.BLOCK;
                    }
                    return rules.evaluate(hostname);
                });
                DnsPolicy tcpPolicy = new DnsPolicy(hostname -> {
                    if (DnsTunnelProbe.BLOCKED_HOST.equalsIgnoreCase(hostname)) {
                        selfCheckTcpSeen.incrementAndGet(); return DomainPolicy.Decision.BLOCK;
                    }
                    return rules.evaluate(hostname);
                });
                transport = new TlsDnsTransport(
                        new InetSocketAddress(InetAddress.getByAddress(new byte[]{1, 1, 1, 3}), 853),
                        "family.cloudflare-dns.com", 2000, DnsVpnService.this::protect);
                DnsExchange exchange = new DnsExchange(policy, transport);
                DnsExchange tcpExchange = new DnsExchange(tcpPolicy, transport);
                try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(established.getFileDescriptor()));
                     OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(established.getFileDescriptor()))) {
                    synchronized (CHECK_LOCK) {
                        if (closed || session != this) return;
                        state = State.RUNNING;
                    }
                    startMonitoring();
                    StructPollfd poll = new StructPollfd();
                    poll.fd = established.getFileDescriptor();
                    poll.events = (short) OsConstants.POLLIN;
                    byte[] buffer = new byte[1501];
                    long checkpointAt = android.os.SystemClock.elapsedRealtime();
                    while (!closed) {
                        emitTcp(tcp.poll(android.os.SystemClock.elapsedRealtime()), tcpExchange, tcpPolicy, output);
                        if (android.os.SystemClock.elapsedRealtime() - checkpointAt >= 60000) {
                            settings.checkpoint();
                            checkpointAt = android.os.SystemClock.elapsedRealtime();
                        }
                        if (Os.poll(new StructPollfd[]{poll}, 250) == 0) continue;
                        if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) {
                            throw new IOException("Tunnel disconnected");
                        }
                        if ((poll.revents & OsConstants.POLLIN) == 0) continue;
                        int size = input.read(buffer);
                        if (size <= 0) continue;
                        Ipv4DnsPacket packet = Ipv4DnsPacket.parse(Arrays.copyOf(buffer, size), SERVER);
                        if (packet == null) {
                            Ipv4TcpPacket tcpPacket = Ipv4TcpPacket.parse(Arrays.copyOf(buffer, size), SERVER);
                            if (tcpPacket != null) {
                                emitTcp(tcp.receive(tcpPacket, android.os.SystemClock.elapsedRealtime()), tcpExchange, tcpPolicy, output);
                                continue;
                            }
                            DnsPacketKind kind = DnsPacketKind.classify(Arrays.copyOf(buffer, size), SERVER);
                            synchronized (CHECK_LOCK) {
                                if (closed || session != this) continue;
                                if (kind == DnsPacketKind.MALFORMED) malformedPackets.incrementAndGet();
                                else {
                                    unsupportedPackets.incrementAndGet();
                                    if (kind == DnsPacketKind.TCP_DNS_UNSUPPORTED) unsupportedTcpPackets.incrementAndGet();
                                }
                                errors.incrementAndGet(); state = State.DEGRADED;
                            }
                            continue;
                        }
                        long queuedAt = android.os.SystemClock.elapsedRealtime();
                        try {
                            workers.execute(() -> {
                                if (closed) return;
                                if (android.os.SystemClock.elapsedRealtime() - queuedAt > 4000) {
                                    recordRequestFailure("QUEUE_EXPIRED");
                                    sendUdpFailure(packet, policy, output);
                                } else handle(packet, exchange, policy, output);
                            });
                        } catch (RejectedExecutionException full) {
                            recordRequestFailure("QUEUE_FULL"); sendUdpFailure(packet, policy, output);
                        }
                    }
                }
            } catch (Exception failure) {
                synchronized (CHECK_LOCK) {
                    if (!closed && session == this) {
                        state = State.ERROR; errors.incrementAndGet(); stopSelf();
                    }
                }
            } finally {
                close();
            }
        }

        private void emitTcp(DnsTcpServer.Result result, DnsExchange exchange, DnsPolicy policy, OutputStream output) throws IOException {
            synchronized (outputLock) {
                if (closed) return;
                for (byte[] packet : result.packets) output.write(packet);
            }
            for (DnsTcpServer.Request request : result.requests) {
                synchronized (CHECK_LOCK) {
                    if (closed || session != this || !tcp.isPending(request)) continue;
                    tcpQueries.incrementAndGet();
                }
                long queuedAt = android.os.SystemClock.elapsedRealtime();
                try {
                    workers.execute(() -> {
                        if (closed || !tcp.isPending(request)) return;
                        if (android.os.SystemClock.elapsedRealtime() - queuedAt > 4000) {
                            recordRequestFailure("QUEUE_EXPIRED");
                            finishTcp(request, null, exchange, policy, output);
                        } else handleTcp(request, exchange, policy, output);
                    });
                } catch (RejectedExecutionException full) {
                    recordRequestFailure("QUEUE_FULL");
                    emitTcp(tcp.fail(request, android.os.SystemClock.elapsedRealtime()), exchange, policy, output);
                }
            }
        }

        private void finishTcp(DnsTcpServer.Request request, byte[] response,
                DnsExchange exchange, DnsPolicy policy, OutputStream output) {
            if (closed) return;
            try {
                if (response == null) response = policy.failureResponse(request.dns);
                emitTcp(response == null ? tcp.fail(request, android.os.SystemClock.elapsedRealtime())
                        : tcp.complete(request, response, android.os.SystemClock.elapsedRealtime()), exchange, policy, output);
            } catch (IOException | RuntimeException failed) { recordRequestFailure("TCP_OUTPUT"); }
        }

        private void handleTcp(DnsTcpServer.Request request, DnsExchange exchange, DnsPolicy policy, OutputStream output) {
            final long requestEpoch;
            synchronized (CHECK_LOCK) {
                if (closed || session != this || !tcp.isPending(request)) return;
                requestEpoch = networkEpoch;
            }
            try {
                DnsExchange.Resolution resolution = exchange.resolveDetailed(request.dns);
                synchronized (CHECK_LOCK) {
                    if (closed || session != this || !tcp.isPending(request)) return;
                    if (requestEpoch != networkEpoch) throw new IOException("DNS connection invalidated");
                    tcpReplies.incrementAndGet();
                    if (resolution.blocked) blocked.incrementAndGet(); else forwarded.incrementAndGet();
                }
                finishTcp(request, resolution.answer, exchange, policy, output);
            } catch (IOException | IllegalArgumentException failure) {
                if (recordRequestFailure(failureCategory(failure), requestEpoch))
                    finishTcp(request, null, exchange, policy, output);
            }
        }

        private void networkChanged() {
            // Serialize invalidation with check creation/publication. No query is sent.
            synchronized (CHECK_LOCK) {
                if (closed || session != this || activeService != DnsVpnService.this) return;
                networkChanges.incrementAndGet();
                networkEpoch++;
                if (checkProbe != null) { checkProbe.close(); checkProbe = null; }
                connectionCheck = new ConnectionCheck(CheckState.STALE, false, 0, DnsTunnelProbe.Failure.NONE);
                TlsDnsTransport current = transport;
                if (current != null) current.invalidateConnections();
            }
        }

        private void startMonitoring() {
            synchronized (monitorLock) {
                if (closed) return;
                connectivity = getSystemService(ConnectivityManager.class);
                callback = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network network) {
                        synchronized (monitorLock) {
                            if (closed || linkMarks.containsKey(network)) return;
                            linkMarks.put(network, null);
                        }
                        networkChanged();
                    }
                    @Override public void onLost(Network network) {
                        synchronized (monitorLock) {
                            if (closed || !linkMarks.containsKey(network)) return;
                            linkMarks.remove(network); capabilityMarks.remove(network);
                        }
                        networkChanged();
                    }
                    @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                        synchronized (monitorLock) {
                            if (closed || !linkMarks.containsKey(network)) return;
                            LinkProperties previous = linkMarks.put(network, snapshotLinks(properties));
                            if (previous == null || previous.equals(properties)) return;
                        }
                        networkChanged();
                    }
                    @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                        // Ignore signal strength/bandwidth churn; only validation/captive changes matter here.
                        int mark = (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 1 : 0)
                                | (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ? 2 : 0);
                        synchronized (monitorLock) {
                            if (closed || !linkMarks.containsKey(network)) return;
                            Integer previous = capabilityMarks.put(network, mark);
                            if (previous == null || previous == mark) return;
                        }
                        networkChanged();
                    }
                };
                try {
                    NetworkRequest request = new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build();
                    connectivity.registerNetworkCallback(request, callback);
                    synchronized (CHECK_LOCK) {
                        if (!closed && session == this && activeService == DnsVpnService.this) networkMonitoring = true;
                    }
                } catch (RuntimeException unavailable) {
                    callback = null;
                    synchronized (CHECK_LOCK) {
                        if (!closed && session == this) {
                            networkMonitoring = false;
                            recordRequestFailure("MONITOR");
                        }
                    }
                }
            }
        }

        private LinkProperties snapshotLinks(LinkProperties properties) {
            // The SDK copy constructor is hidden; Parcelable provides an independent
            // snapshot using public API, so later mutations cannot defeat comparison.
            Parcel parcel = Parcel.obtain();
            try {
                properties.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                return LinkProperties.CREATOR.createFromParcel(parcel);
            } finally { parcel.recycle(); }
        }

        private boolean recordRequestFailure(String category) {
            return recordRequestFailure(category, -1);
        }

        private boolean recordRequestFailure(String category, long requestEpoch) {
            synchronized (CHECK_LOCK) {
                if (closed || session != this || activeService != DnsVpnService.this) return false;
                if (requestEpoch >= 0 && requestEpoch != networkEpoch) {
                    cancelledNetworkRequests.incrementAndGet();
                    errors.incrementAndGet();
                    return true; // Reply SERVFAIL, but do not contaminate the new network's health.
                }
                failureEpoch++;
                lastDnsFailure = category;
                errors.incrementAndGet(); state = State.DEGRADED;
                if (connectionCheck.state == CheckState.PASSED) {
                    connectionCheck = new ConnectionCheck(CheckState.STALE, false, 0, DnsTunnelProbe.Failure.NONE);
                }
                return true;
            }
        }

        private void stopMonitoring() {
            synchronized (monitorLock) {
                ConnectivityManager.NetworkCallback previous = callback;
                callback = null;
                if (previous != null && connectivity != null) {
                    try { connectivity.unregisterNetworkCallback(previous); }
                    catch (RuntimeException alreadyGone) { /* No permission or live callback left. */ }
                }
                linkMarks.clear(); capabilityMarks.clear();
            }
        }

        void handle(Ipv4DnsPacket packet, DnsExchange exchange, DnsPolicy policy, OutputStream output) {
            final long requestEpoch;
            synchronized (CHECK_LOCK) {
                if (closed || session != this || activeService != DnsVpnService.this) return;
                requestEpoch = networkEpoch;
            }
            try {
                DnsExchange.Resolution resolution = exchange.resolveDetailed(packet.dns);
                synchronized (CHECK_LOCK) {
                    if (requestEpoch != networkEpoch) throw new IOException("DNS connection invalidated");
                }
                byte[] payload = resolution.answer;
                boolean truncated = payload.length > policy.udpResponseLimit(packet.dns);
                if (truncated) {
                    payload = policy.truncatedResponse(packet.dns);
                }
                byte[] response = packet.response(payload);
                synchronized (outputLock) {
                    if (closed) return;
                    output.write(response);
                }
                synchronized (CHECK_LOCK) {
                    if (closed || session != this || activeService != DnsVpnService.this) return;
                    if (truncated) udpTruncated.incrementAndGet();
                    if (resolution.blocked) blocked.incrementAndGet();
                    else forwarded.incrementAndGet();
                }
            } catch (IOException | IllegalArgumentException failure) {
                if (recordRequestFailure(failureCategory(failure), requestEpoch)) sendUdpFailure(packet, policy, output);
            }
        }

        private String failureCategory(Exception failure) {
            return failure instanceof javax.net.ssl.SSLException ? "TLS"
                            : failure instanceof java.net.SocketTimeoutException ? "TIMEOUT"
                            : failure instanceof java.net.ConnectException ? "CONNECT"
                            : "Invalid or truncated DNS reply".equals(failure.getMessage()) ? "REPLY_HEADER"
                            : "DNS question mismatch".equals(failure.getMessage()) ? "REPLY_QUESTION"
                            : "Invalid or unsupported DNS reply structure".equals(failure.getMessage()) ? "REPLY_STRUCTURE"
                            : "Could not protect resolver socket".equals(failure.getMessage()) ? "PROTECT"
                            : "Invalid DNS frame size".equals(failure.getMessage()) ? "FRAME"
                            : failure instanceof java.io.EOFException ? "EOF"
                            : failure instanceof java.net.SocketException ? "SOCKET"
                            : failure instanceof IllegalArgumentException ? "PACKET"
                            : "IO";
        }

        private void sendUdpFailure(Ipv4DnsPacket packet, DnsPolicy policy, OutputStream output) {
            byte[] error = policy.failureResponse(packet.dns);
            if (error == null) return;
            try {
                synchronized (outputLock) {
                    if (!closed) output.write(packet.response(error));
                }
            } catch (IOException | IllegalArgumentException ignored) {
                recordRequestFailure("UDP_OUTPUT");
            }
        }

        void close() {
            synchronized (CHECK_LOCK) {
                closed = true;
                if (checkProbe != null) { checkProbe.close(); checkProbe = null; }
                if (session == this) {
                    session = null;
                    if (activeService == DnsVpnService.this) {
                        activeService = null;
                        connectionCheck = idleCheck();
                        networkMonitoring = false;
                    }
                }
            }
            stopMonitoring();
            browserTest.clear();
            tcp.close();
            checkWorker.shutdownNow();
            workers.shutdownNow();
            TlsDnsTransport currentTransport = transport;
            if (currentTransport != null) currentTransport.close();
            ParcelFileDescriptor current = tunnel;
            if (current != null) try { current.close(); } catch (IOException ignored) { }
        }
    }
}
