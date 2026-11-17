import XCTest
@testable import Despertador

final class ShakeCounterTests: XCTestCase {

    func testSampleBelowThresholdDoesNotIncrement() {
        var counter = ShakeCounter(target: 5, threshold: 2.5, minInterval: 0.3)

        counter.feed(magnitude: 1.0, at: 0.0)
        counter.feed(magnitude: 2.49, at: 0.5)

        XCTAssertEqual(counter.remaining, 5)
        XCTAssertFalse(counter.isDone)
    }

    func testTwoSamplesAboveThresholdWithinMinIntervalCountAsOne() {
        var counter = ShakeCounter(target: 5, threshold: 2.5, minInterval: 0.3)

        counter.feed(magnitude: 3.0, at: 1.0)
        counter.feed(magnitude: 4.0, at: 1.1)
        counter.feed(magnitude: 3.5, at: 1.25)

        XCTAssertEqual(counter.remaining, 4)
        XCTAssertFalse(counter.isDone)
    }

    func testTwoSamplesAboveThresholdSeparatedByMoreThanMinIntervalCountTwice() {
        var counter = ShakeCounter(target: 5, threshold: 2.5, minInterval: 0.3)

        counter.feed(magnitude: 3.0, at: 1.0)
        counter.feed(magnitude: 3.2, at: 1.35)

        XCTAssertEqual(counter.remaining, 3)
        XCTAssertFalse(counter.isDone)
    }

    func testRemainingNeverBelowZeroAndIsDoneWhenZero() {
        var counter = ShakeCounter(target: 2, threshold: 2.5, minInterval: 0.3)

        XCTAssertFalse(counter.isDone)
        XCTAssertEqual(counter.remaining, 2)

        counter.feed(magnitude: 3.0, at: 0.0)
        XCTAssertEqual(counter.remaining, 1)
        XCTAssertFalse(counter.isDone)

        counter.feed(magnitude: 3.0, at: 0.4)
        XCTAssertEqual(counter.remaining, 0)
        XCTAssertTrue(counter.isDone)

        // Extra shake: remaining should never drop below zero
        counter.feed(magnitude: 3.0, at: 0.8)
        XCTAssertEqual(counter.remaining, 0)
        XCTAssertTrue(counter.isDone)
    }

    func testWorksWithLargeTargetAround1Point8Billion() {
        let target: Int64 = 1_800_000_000
        var counter = ShakeCounter(target: target, threshold: 2.5, minInterval: 0.3)

        XCTAssertEqual(counter.remaining, target)
        XCTAssertFalse(counter.isDone)

        counter.feed(magnitude: 3.0, at: 100.0)
        XCTAssertEqual(counter.remaining, target - 1)
        XCTAssertFalse(counter.isDone)
    }

    func testSampleExactlyAtThresholdCounts() {
        var counter = ShakeCounter(target: 1, threshold: 2.5, minInterval: 0.3)

        counter.feed(magnitude: 2.5, at: 0.0)

        XCTAssertEqual(counter.remaining, 0)
        XCTAssertTrue(counter.isDone)
    }

    func testZeroOrNegativeTargetStartsAsDone() {
        let zeroCounter = ShakeCounter(target: 0, threshold: 2.5, minInterval: 0.3)
        XCTAssertEqual(zeroCounter.remaining, 0)
        XCTAssertTrue(zeroCounter.isDone)

        let negativeCounter = ShakeCounter(target: -10, threshold: 2.5, minInterval: 0.3)
        XCTAssertEqual(negativeCounter.remaining, 0)
        XCTAssertTrue(negativeCounter.isDone)
    }
}
