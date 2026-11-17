import Foundation

public struct ShakeCounter {
    public let target: Int64
    public let threshold: Double
    public let minInterval: TimeInterval

    private var count: Int64 = 0
    private var lastShakeTime: TimeInterval?

    public init(target: Int64, threshold: Double = 2.5, minInterval: TimeInterval = 0.3) {
        self.target = target
        self.threshold = threshold
        self.minInterval = minInterval
    }

    public var remaining: Int64 {
        max(0, target - count)
    }

    public var isDone: Bool {
        remaining == 0
    }

    public mutating func feed(magnitude: Double, at time: TimeInterval) {
        guard magnitude >= threshold else {
            return
        }

        if let lastTime = lastShakeTime {
            guard (time - lastTime) >= minInterval else {
                return
            }
        }

        count += 1
        lastShakeTime = time
    }
}
