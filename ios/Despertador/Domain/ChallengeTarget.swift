import Foundation

enum ChallengeTarget {
    /// Número de sacudidas exigido: segundos desde 01/01/1970 no instante em que o alarme toca (truncado, sem mod).
    static func shakes(for ringTime: Date) -> Int64 {
        Int64(ringTime.timeIntervalSince1970)
    }
}
