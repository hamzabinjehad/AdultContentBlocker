import XCTest
@testable import Hisn

final class BoundedDataTests: XCTestCase {
    func testAccumulatesExactlyUpToTheCeiling() throws {
        var data = BoundedData(limit: 8)
        try data.append(Data([1, 2, 3]))
        try data.append(Data())
        try data.append(Data([4, 5, 6, 7, 8]))
        XCTAssertEqual(data.data, Data([1, 2, 3, 4, 5, 6, 7, 8]))
    }

    func testRefusesOneByteOverTheCeilingWithoutChangingTheResult() throws {
        var data = BoundedData(limit: 4)
        try data.append(Data([1, 2]))
        XCTAssertThrowsError(try data.append(Data([3, 4, 5]))) { error in
            XCTAssertEqual((error as? URLError)?.code, .dataLengthExceedsMaximum)
        }
        XCTAssertEqual(data.data, Data([1, 2]))
    }

    func testAChunkLargerThanTheWholeCeilingIsRefused() {
        var data = BoundedData(limit: 2)
        XCTAssertThrowsError(try data.append(Data([1, 2, 3])))
        XCTAssertTrue(data.data.isEmpty)
    }
}
