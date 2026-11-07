import Foundation

/// A parsed standard DNS query with exactly one question.
///
/// The DNS proxy only ever handles queries, so everything here is deliberately
/// narrow: no compression in the question, no EDNS answers, no record parsing
/// beyond the OPT record that sets the client's UDP payload size. Bounded
/// parsing matters more than generality: a hostile datagram must not be able to
/// make the extension allocate or loop without limit.
struct DNSQuestion: Equatable {
    let id: UInt16
    /// Lowercased ASCII name without the trailing dot. Empty for a root query.
    let name: String
    let type: UInt16
    let klass: UInt16
    let recursionDesired: Bool
    /// The exact QNAME + QTYPE + QCLASS bytes, echoed unchanged in a reply so a
    /// client that sent an unusual spelling still matches its own question.
    let questionBytes: Data
    /// The largest UDP reply this client accepts, 512...4096.
    let udpPayloadSize: Int
}

enum DNSMessageError: Error, Equatable {
    case tooShort
    case notAQuery
    case unsupportedOpcode
    case questionCount
    case answerCount
    case invalidName
    case nameTooLong
    case compressionPointer
    case truncated
    case additionalSection
}

/// The DNS wire codec shared by the proxy extension, the app's route check and
/// their tests. Pure Foundation, no network state.
enum DNSMessage {
    static let selfCheckName = "self-check.hisn.invalid"
    /// TEST-NET-3, reserved for documentation. Answering this address proves
    /// Hisn's own resolver answered; no real name uses it.
    static let selfCheckAddress: [UInt8] = [203, 0, 113, 7]
    static let minimumUDPSize = 512
    static let maximumUDPSize = 4096
    static let maximumNameLength = 253
    private static let headerLength = 12

    // MARK: - Parsing

    static func parseQuery(_ data: Data) throws -> DNSQuestion {
        let bytes = [UInt8](data)
        guard bytes.count >= headerLength else { throw DNSMessageError.tooShort }
        let flags = uint16(bytes, 2)
        guard flags & 0x8000 == 0 else { throw DNSMessageError.notAQuery }
        guard (flags >> 11) & 0xF == 0 else { throw DNSMessageError.unsupportedOpcode }
        guard uint16(bytes, 4) == 1 else { throw DNSMessageError.questionCount }
        guard uint16(bytes, 6) == 0, uint16(bytes, 8) == 0 else {
            throw DNSMessageError.answerCount
        }
        let additional = Int(uint16(bytes, 10))
        guard additional <= 8 else { throw DNSMessageError.additionalSection }

        let (name, questionEnd) = try readName(bytes, from: headerLength)
        guard questionEnd + 4 <= bytes.count else { throw DNSMessageError.truncated }
        let type = uint16(bytes, questionEnd)
        let klass = uint16(bytes, questionEnd + 2)
        let end = questionEnd + 4
        let payload = try udpPayloadSize(bytes, from: end, records: additional)
        return DNSQuestion(id: uint16(bytes, 0), name: name, type: type, klass: klass,
                           recursionDesired: flags & 0x0100 != 0,
                           questionBytes: Data(bytes[headerLength..<end]),
                           udpPayloadSize: payload)
    }

    /// Reads a name that is not allowed to use compression pointers. A query's
    /// QNAME never needs one, and accepting pointers would mean walking a graph
    /// inside an untrusted datagram.
    private static func readName(_ bytes: [UInt8], from start: Int) throws -> (String, Int) {
        var offset = start
        var wireLength = 1
        var labels: [String] = []
        while true {
            guard offset < bytes.count else { throw DNSMessageError.truncated }
            let length = Int(bytes[offset])
            offset += 1
            if length == 0 { break }
            if length & 0xC0 == 0xC0 { throw DNSMessageError.compressionPointer }
            // The two high bits must be zero for a literal label. 01xxxxxx is
            // neither a label length nor a pointer, so it is its own error.
            guard length & 0xC0 == 0, length <= 63 else {
                throw DNSMessageError.invalidName
            }
            guard offset + length <= bytes.count else { throw DNSMessageError.truncated }
            wireLength += length + 1
            guard wireLength <= maximumNameLength else { throw DNSMessageError.nameTooLong }
            var label = ""
            label.reserveCapacity(length)
            for index in offset..<(offset + length) {
                let byte = bytes[index]
                // Printable ASCII only. Hisn's list and terms are ASCII; a name
                // with any other byte can never equal an entry, so refusing it
                // is not a coverage loss and keeps every comparison simple.
                guard byte > 0x20, byte < 0x7F, byte != UInt8(ascii: ".") else {
                    throw DNSMessageError.invalidName
                }
                label.append(Character(Unicode.Scalar(byte)))
            }
            labels.append(label.lowercased())
            offset += length
        }
        return (labels.joined(separator: "."), offset)
    }

    /// Walks the additional section only far enough to read an OPT record's
    /// class field, which is the client's UDP payload size.
    private static func udpPayloadSize(_ bytes: [UInt8], from start: Int,
                                       records: Int) throws -> Int {
        var offset = start
        var payload = minimumUDPSize
        for _ in 0..<records {
            offset = try skipName(bytes, from: offset)
            guard offset + 10 <= bytes.count else { throw DNSMessageError.additionalSection }
            let type = uint16(bytes, offset)
            let klass = Int(uint16(bytes, offset + 2))
            let length = Int(uint16(bytes, offset + 8))
            offset += 10
            guard offset + length <= bytes.count else { throw DNSMessageError.additionalSection }
            if type == 41 {
                payload = min(max(klass, minimumUDPSize), maximumUDPSize)
            }
            offset += length
        }
        return payload
    }

    private static func skipName(_ bytes: [UInt8], from start: Int) throws -> Int {
        var offset = start
        var hops = 0
        while true {
            guard offset < bytes.count else { throw DNSMessageError.additionalSection }
            let length = Int(bytes[offset])
            if length == 0 { return offset + 1 }
            if length & 0xC0 == 0xC0 {
                guard offset + 1 < bytes.count else { throw DNSMessageError.additionalSection }
                return offset + 2
            }
            guard length & 0xC0 == 0, length <= 63, offset + 1 + length <= bytes.count else {
                throw DNSMessageError.additionalSection
            }
            offset += 1 + length
            hops += 1
            guard hops <= 128 else { throw DNSMessageError.additionalSection }
        }
    }

    // MARK: - Replies

    static func nxdomain(for question: DNSQuestion) -> Data {
        reply(question, rcode: 3)
    }

    static func servfail(for question: DNSQuestion) -> Data {
        reply(question, rcode: 2)
    }

    static func notImplemented(for question: DNSQuestion) -> Data {
        reply(question, rcode: 4)
    }

    private static func reply(_ question: DNSQuestion, rcode: UInt16) -> Data {
        header(question, flags: 0x8000 | 0x0080 | (question.recursionDesired ? 0x0100 : 0)
            | (rcode & 0xF), answers: 0) + question.questionBytes
    }

    /// The local self-check answer: an A record for `self-check.hisn.invalid`.
    /// Other query types get NOERROR with no answer, so a resolver asking for
    /// AAAA does not see a lie about an address family.
    static func selfCheckReply(for question: DNSQuestion) -> Data {
        guard question.type == 1, question.klass == 1 else {
            return header(question, flags: 0x8000 | 0x0080
                | (question.recursionDesired ? 0x0100 : 0), answers: 0)
                + question.questionBytes
        }
        var answer = Data([0xC0, 0x0C, 0x00, 0x01, 0x00, 0x01])
        appendUInt32(&answer, 60)
        appendUInt16(&answer, UInt16(selfCheckAddress.count))
        answer.append(contentsOf: selfCheckAddress)
        return header(question, flags: 0x8000 | 0x0400 | 0x0080
            | (question.recursionDesired ? 0x0100 : 0), answers: 1)
            + question.questionBytes + answer
    }

    /// A reply carrying only the header and question with TC set, which tells
    /// the client to retry over TCP.
    static func truncated(for question: DNSQuestion) -> Data {
        header(question, flags: 0x8000 | 0x0200 | 0x0080
            | (question.recursionDesired ? 0x0100 : 0), answers: 0)
            + question.questionBytes
    }

    private static func header(_ question: DNSQuestion, flags: UInt16,
                               answers: UInt16) -> Data {
        var data = Data()
        appendUInt16(&data, question.id)
        appendUInt16(&data, flags)
        appendUInt16(&data, 1)
        appendUInt16(&data, answers)
        appendUInt16(&data, 0)
        appendUInt16(&data, 0)
        return data
    }

    /// A UDP reply must fit the client's advertised buffer. Anything larger is
    /// replaced by a TC reply rather than fragmented past what the socket takes.
    static func limited(_ response: Data, for question: DNSQuestion) -> Data {
        response.count <= question.udpPayloadSize ? response : truncated(for: question)
    }

    // MARK: - Response validation

    /// True when `data` is a response to exactly this question: same ID, a
    /// standard reply, one question with the same name, type and class, and a
    /// sensible length. The body itself is not re-parsed; it is forwarded as
    /// the authenticated resolver sent it.
    static func isResponse(_ data: Data, to question: DNSQuestion) -> Bool {
        guard data.count >= headerLength, data.count <= 65_535 else { return false }
        let bytes = [UInt8](data)
        let flags = uint16(bytes, 2)
        guard uint16(bytes, 0) == question.id,
              flags & 0x8000 != 0,
              (flags >> 11) & 0xF == 0,
              uint16(bytes, 4) == 1 else { return false }
        guard let (name, afterName) = try? readName(bytes, from: headerLength),
              afterName + 4 <= bytes.count,
              name == question.name,
              uint16(bytes, afterName) == question.type,
              uint16(bytes, afterName + 2) == question.klass else { return false }
        return true
    }

    // MARK: - Bytes

    private static func uint16(_ bytes: [UInt8], _ offset: Int) -> UInt16 {
        (UInt16(bytes[offset]) << 8) | UInt16(bytes[offset + 1])
    }

    private static func appendUInt16(_ data: inout Data, _ value: UInt16) {
        data.append(UInt8(value >> 8))
        data.append(UInt8(value & 0xFF))
    }

    private static func appendUInt32(_ data: inout Data, _ value: UInt32) {
        data.append(UInt8((value >> 24) & 0xFF))
        data.append(UInt8((value >> 16) & 0xFF))
        data.append(UInt8((value >> 8) & 0xFF))
        data.append(UInt8(value & 0xFF))
    }
}
