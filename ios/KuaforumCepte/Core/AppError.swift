import Foundation
import FirebaseAuth
import FirebaseFunctions

/// Tüm hataların tek biçimi: sunucunun `details.reason` değeri veya istemci tarafı neden.
struct AppError: Error {
    let reason: String
    var details: [String: Any] = [:]
    /// Teşhis için kısa kod (bilinmeyen hatalarda gösterilir).
    var code: String? = nil

    static func from(_ error: Error) -> AppError {
        if let e = error as? AppError { return e }
        let ns = error as NSError
        if ns.domain == FunctionsErrorDomain {
            let details = ns.userInfo[FunctionsErrorDetailsKey] as? [String: Any] ?? [:]
            if let reason = details["reason"] as? String { return AppError(reason: reason, details: details) }
            switch FunctionsErrorCode(rawValue: ns.code) {
            case .unavailable, .deadlineExceeded: return AppError(reason: "NETWORK")
            case .unauthenticated: return AppError(reason: "UNAUTHENTICATED")
            case .permissionDenied: return AppError(reason: "PERMISSION_DENIED")
            case .resourceExhausted: return AppError(reason: "RATE_LIMITED")
            default: return AppError(reason: "UNKNOWN", code: "functions:\(ns.code)")
            }
        }
        if ns.domain == "FIRAuthErrorDomain", let code = AuthErrorCode(rawValue: ns.code) {
            switch code {
            case .invalidVerificationCode: return AppError(reason: "INVALID_CODE")
            case .sessionExpired: return AppError(reason: "CODE_EXPIRED")
            case .invalidPhoneNumber, .missingPhoneNumber: return AppError(reason: "INVALID_PHONE")
            case .tooManyRequests, .quotaExceeded: return AppError(reason: "RATE_LIMITED")
            case .networkError: return AppError(reason: "NETWORK")
            case .operationNotAllowed: return AppError(reason: "AUTH_PHONE_DISABLED")
            case .appNotAuthorized, .missingAppCredential, .invalidAppCredential, .notificationNotForwarded:
                return AppError(reason: "AUTH_APP_NOT_AUTHORIZED", code: "auth:\(ns.code)")
            default: return AppError(reason: "UNKNOWN", code: "auth:\(ns.code)")
            }
        }
        if ns.domain == NSURLErrorDomain { return AppError(reason: "NETWORK") }
        return AppError(reason: "UNKNOWN", code: "\(ns.domain):\(ns.code)")
    }
}
