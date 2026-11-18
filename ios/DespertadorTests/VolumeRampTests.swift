import XCTest
@testable import Despertador

final class VolumeRampTests: XCTestCase {
    private let ramp = VolumeRamp(start: 0.1, end: 1.0, duration: 60)

    func testStartAtZero() {
        XCTAssertEqual(ramp.level(elapsed: 0), 0.1, accuracy: 0.0001)
    }

    func testEndAtDuration() {
        XCTAssertEqual(ramp.level(elapsed: 60), 1.0, accuracy: 0.0001)
    }

    func testClampsAboveDuration() {
        XCTAssertEqual(ramp.level(elapsed: 120), 1.0, accuracy: 0.0001)
    }

    func testClampsBelowZero() {
        XCTAssertEqual(ramp.level(elapsed: -5), 0.1, accuracy: 0.0001)
    }

    func testMidpointIsAverage() {
        XCTAssertEqual(ramp.level(elapsed: 30), 0.55, accuracy: 0.0001)
    }
}
