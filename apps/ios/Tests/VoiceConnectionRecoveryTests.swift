import XCTest
@testable import MuralCore

@MainActor final class VoiceConnectionRecoveryTests: XCTestCase {
    private func waitUntil(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        let deadline = Date().addingTimeInterval(5)
        while !condition(), Date() < deadline {
            try? await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertTrue(condition(), file: file, line: line)
    }

    func testBriefHandoffRecoversAndLaterDisconnectFailsOnce() async throws {
        var failures = 0
        let recovery = VoiceConnectionRecovery(timeout: .milliseconds(40)) { failures += 1 }
        recovery.disconnected()
        recovery.connected()
        try await Task.sleep(for: .milliseconds(60))
        XCTAssertEqual(failures, 0)
        recovery.disconnected()
        await waitUntil { failures == 1 }
        XCTAssertEqual(failures, 1)
        recovery.disconnected()
        try await Task.sleep(for: .milliseconds(60))
        XCTAssertEqual(failures, 1)
    }

    func testRepeatedDisconnectDoesNotExtendDeadline() async throws {
        var failures = 0
        let recovery = VoiceConnectionRecovery(timeout: .milliseconds(60)) { failures += 1 }
        recovery.disconnected()
        try await Task.sleep(for: .milliseconds(40))
        recovery.disconnected()
        await waitUntil { failures == 1 }
        XCTAssertEqual(failures, 1)
    }

    func testClosingAndStartingAnotherCallCancelsOldTimer() async throws {
        var failures = 0
        let recovery = VoiceConnectionRecovery(timeout: .milliseconds(40)) { failures += 1 }
        recovery.disconnected()
        recovery.connected() // close / disconnect
        recovery.disconnected() // next call
        recovery.connected()
        try await Task.sleep(for: .milliseconds(70))
        XCTAssertEqual(failures, 0)
    }
}
