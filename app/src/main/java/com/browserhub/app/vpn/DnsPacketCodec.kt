package com.browserhub.app.vpn

data class ParsedDnsUdpPacket(
    val ipHeaderLength: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray,
    val queriedDomain: String
)

object DnsPacketCodec {

    fun parseIpv4UdpDns(packet: ByteArray, length: Int): ParsedDnsUdpPacket? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null

        val udpLength = u16(packet, ihl + 4)
        if (udpLength < 12 || ihl + udpLength > length) return null

        val dnsPayload = packet.copyOfRange(ihl + 8, ihl + udpLength)
        val domain = extractQueryDomain(dnsPayload)

        return ParsedDnsUdpPacket(
            ipHeaderLength = ihl,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayload = dnsPayload,
            queriedDomain = domain
        )
    }

    fun extractQueryDomain(dnsPayload: ByteArray): String {
        if (dnsPayload.size < 13) return ""
        val sb = StringBuilder()
        var idx = 12
        while (idx < dnsPayload.size) {
            val labelLen = dnsPayload[idx].toInt() and 0xFF
            if (labelLen == 0) break
            if ((labelLen and 0xC0) != 0) break // Pointer not expected in QNAME start
            idx++
            if (idx + labelLen > dnsPayload.size) break
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dnsPayload, idx, labelLen, Charsets.US_ASCII))
            idx += labelLen
        }
        return sb.toString()
    }

    fun buildNxDomainDnsResponse(queryPayload: ByteArray): ByteArray {
        if (queryPayload.size < 12) return queryPayload
        val resp = queryPayload.copyOf()
        // QR = 1 (response), preserve RD bit, RA = 1, RCODE = 3 (NXDOMAIN)
        val rd = resp[2].toInt() and 0x01
        resp[2] = (0x80 or rd).toByte()
        resp[3] = 0x83.toByte()
        // ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        resp[6] = 0
        resp[7] = 0
        resp[8] = 0
        resp[9] = 0
        resp[10] = 0
        resp[11] = 0
        return resp
    }

    fun buildIpv4UdpResponsePacket(
        request: ParsedDnsUdpPacket,
        dnsResponsePayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponsePayload.size
        val packet = ByteArray(totalLen)

        // IPv4 Header
        packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        packet[1] = 0x00
        writeU16(packet, 2, totalLen)
        packet[4] = 0x00
        packet[5] = 0x01
        packet[6] = 0x40.toByte() // Don't fragment
        packet[7] = 0x00
        packet[8] = 64.toByte()   // TTL
        packet[9] = 17.toByte()   // UDP
        packet[10] = 0
        packet[11] = 0
        // Swap src and dst IP
        System.arraycopy(request.dstIp, 0, packet, 12, 4)
        System.arraycopy(request.srcIp, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipHeaderLen)
        writeU16(packet, 10, ipChecksum)

        // UDP Header
        val udpLen = udpHeaderLen + dnsResponsePayload.size
        writeU16(packet, ipHeaderLen, request.dstPort)
        writeU16(packet, ipHeaderLen + 2, request.srcPort)
        writeU16(packet, ipHeaderLen + 4, udpLen)
        writeU16(packet, ipHeaderLen + 6, 0)

        System.arraycopy(dnsResponsePayload, 0, packet, ipHeaderLen + udpHeaderLen, dnsResponsePayload.size)

        val udpChecksum = computeUdpChecksum(packet, ipHeaderLen, udpLen)
        writeU16(packet, ipHeaderLen + 6, udpChecksum)

        return packet
    }

    private fun computeUdpChecksum(packet: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0L
        // Pseudo-header: srcIp (12..15), dstIp (16..19), zero + protocol (17), udpLen
        sum += u16(packet, 12)
        sum += u16(packet, 14)
        sum += u16(packet, 16)
        sum += u16(packet, 18)
        sum += 17L
        sum += udpLen.toLong()

        var i = 0
        while (i + 1 < udpLen) {
            sum += u16(packet, udpOffset + i)
            i += 2
        }
        if (i < udpLen) {
            sum += ((packet[udpOffset + i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val result = sum.inv().toInt() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    internal fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < length) {
            sum += u16(data, offset + i)
            i += 2
        }
        if (i < length) {
            sum += ((data[offset + i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }
}
