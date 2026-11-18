import XCTest
@testable import Despertador

final class ChallengeTargetTests: XCTestCase {
    func testKnownDate() {
        XCTAssertEqual(ChallengeTarget.shakes(for: Date(timeIntervalSince1970: 1_800_000_000)), 1_800_000_000)
    }

    func testPositiveAtStartOf2026() {
        // 2026-01-01T00:00:00Z
        let target = ChallengeTarget.shakes(for: Date(timeIntervalSince1970: 1_767_225_600))
        XCTAssertEqual(target, 1_767_225_600)
        XCTAssertGreaterThan(target, 0)
    }

    func testPositiveAtEndOf2100() {
        // 2100-12-31T23:59:59Z
        let target = ChallengeTarget.shakes(for: Date(timeIntervalSince1970: 4_133_980_799))
        XCTAssertEqual(target, 4_133_980_799)
        XCTAssertGreaterThan(target, 0)
    }

    func testTruncatesFractionalSeconds() {
        XCTAssertEqual(ChallengeTarget.shakes(for: Date(timeIntervalSince1970: 1_800_000_000.999)), 1_800_000_000)
    }
}
