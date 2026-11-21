package com.browserhub.app

import com.browserhub.app.vpn.DnsPacketCodec
import com.browserhub.app.vpn.TrackerBlocklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnCodecAndBlocklistTest {

    private fun buildDnsQueryPayload(domain: String): ByteArray {
        val labels = domain.split('.').filter { it.isNotEmpty() }
        val qname = mutableListOf<Byte>()
        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            qname.add(bytes.size.toByte())
            bytes.forEach { qname.add(it) }
        }
        qname.add(0)

        val header = byteArrayOf(
            0x12, 0x34, // ID
            0x01, 0x00, // RD = 1
            0x00, 0x01, // QDCOUNT = 1
            0x00, 0x00, // ANCOUNT = 0
            0x00, 0x00, // NSCOUNT = 0
            0x00, 0x00  // ARCOUNT = 0
        )
        val qtypeQclass = byteArrayOf(
            0x00, 0x01, // A
            0x00, 0x01  // IN
        )
        return header + qname.toByteArray() + qtypeQclass
    }

    private fun wrapIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + payload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)
        packet[0] = 0x45.toByte()
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)
        val ipSum = DnsPacketCodec.computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipSum ushr 8) and 0xFF).toByte()
        packet[11] = (ipSum and 0xFF).toByte()

        packet[20] = ((srcPort ushr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort ushr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    @Test
    fun parseIpv4UdpDns_extractsDomainAndPortsAndBuildsValidResponse() {
        val dnsQuery = buildDnsQueryPayload("www.google-analytics.com")
        val packet = wrapIpv4UdpPacket(
            srcIp = byteArrayOf(10, 111, 0, 1),
            dstIp = byteArrayOf(10, 111, 0, 2),
            srcPort = 54321,
            dstPort = 53,
            payload = dnsQuery
        )

        val parsed = DnsPacketCodec.parseIpv4UdpDns(packet, packet.size)
        assertNotNull(parsed)
        assertEquals("www.google-analytics.com", parsed!!.queriedDomain)
        assertEquals(54321, parsed.srcPort)
        assertEquals(53, parsed.dstPort)

        val nxDomain = DnsPacketCodec.buildNxDomainDnsResponse(parsed.dnsPayload)
        assertEquals(0x81, nxDomain[2].toInt() and 0xFF) // QR=1, RD=1
        assertEquals(0x83, nxDomain[3].toInt() and 0xFF) // RA=1, RCODE=3 (NXDOMAIN)

        val responsePacket = DnsPacketCodec.buildIpv4UdpResponsePacket(parsed, nxDomain)
        // Verify swapped IP addresses and ports
        assertEquals(10, responsePacket[12].toInt() and 0xFF)
        assertEquals(2, responsePacket[15].toInt() and 0xFF)
        assertEquals(10, responsePacket[16].toInt() and 0xFF)
        assertEquals(1, responsePacket[19].toInt() and 0xFF)
        assertEquals(0, DnsPacketCodec.computeChecksum(responsePacket, 0, 20))
    }

    @Test
    fun parseIpv4UdpDns_ignoresNonDnsPortPackets() {
        val packet = wrapIpv4UdpPacket(
            srcIp = byteArrayOf(10, 111, 0, 1),
            dstIp = byteArrayOf(10, 111, 0, 2),
            srcPort = 54321,
            dstPort = 443,
            payload = buildDnsQueryPayload("example.com")
        )
        assertNull(DnsPacketCodec.parseIpv4UdpDns(packet, packet.size))
    }

    @Test
    fun trackerBlocklist_blocksTrackersAndAllowsNormalDomains() {
        assertTrue(TrackerBlocklist.isBlockedDomain("doubleclick.net"))
        assertTrue(TrackerBlocklist.isBlockedDomain("ad.doubleclick.net."))
        assertTrue(TrackerBlocklist.isBlockedDomain("www.googletagmanager.com"))
        assertTrue(TrackerBlocklist.isBlockedUrl("https://www.google-analytics.com/analytics.js"))

        assertFalse(TrackerBlocklist.isBlockedDomain("github.com"))
        assertFalse(TrackerBlocklist.isBlockedUrl("https://developer.android.com/guide"))
        assertFalse(TrackerBlocklist.isBlockedDomain(""))
    }
}
