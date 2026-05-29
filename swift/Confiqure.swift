import Foundation

// =====================================================================
// Confiqure for Swift
//
// V1 ships a runtime protocol-based marker. The Swift macro attribute
// (using `@Confiqure(...)` directly on a class) requires a companion
// `ConfiqureMacros` target — added in a follow-up.
//
// V1 usage:
//
//     final class Notifications: Confiqurable {
//         static let confiqureMetadata = ConfiqureMetadata(
//             end:      "/notifications",
//             type:     "single",
//             tools:    ["Amazon_Connect", "Ebay_Connect"],
//             callback: "https://myapp.com/webhooks/confiqure"
//         )
//     }
//
// The confiqure CLI scans source files for `Confiqurable` conformance
// (and, once the macro lands, for `@Confiqure(...)`). The backend AI parses
// the annotated class and generates the chat playbook.
// =====================================================================

public struct ConfiqureMetadata: Sendable, Hashable {
    public let end: String
    public let type: String
    public let tools: [String]
    public let callback: String

    public init(
        end: String = "",
        type: String = "single",
        tools: [String] = [],
        callback: String = ""
    ) {
        self.end = end
        self.type = type
        self.tools = tools
        self.callback = callback
    }
}

public protocol Confiqurable {
    static var confiqureMetadata: ConfiqureMetadata { get }
}
