package app.hisn.core;

/** One UDP DNS request the DNS tunnel can carry, independent of IP version. */
public interface UdpDnsPacket {
    byte[] getDns();
    byte[] response(byte[] payload);
}
