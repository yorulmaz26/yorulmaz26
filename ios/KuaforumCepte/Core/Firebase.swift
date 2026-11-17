import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions

/// Firebase yapılandırması: GoogleService-Info.plist yoksa uygulama "kurulum gerekli" ekranını gösterir.
enum FirebaseSetup {
    private(set) static var isConfigured = false

    static func configureIfPossible() {
        guard FirebaseApp.app() == nil,
              Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            isConfigured = FirebaseApp.app() != nil
            return
        }
        FirebaseApp.configure()
        #if DEBUG
        // Yalnızca test derlemesi: Firebase Console'daki test numaraları için uygulama doğrulaması atlanır.
        Auth.auth().settings?.isAppVerificationDisabledForTesting = true
        #endif
        isConfigured = true
    }
}

/// Sunucu fonksiyonları (europe-west1). Yanıt her zaman sözlük olarak döner.
enum Backend {
    static var db: Firestore { Firestore.firestore() }
    static var functions: Functions { Functions.functions(region: "europe-west1") }

    @discardableResult
    static func call(_ name: String, _ data: [String: Any] = [:]) async throws -> [String: Any] {
        do {
            let result = try await functions.httpsCallable(name).call(data)
            return result.data as? [String: Any] ?? [:]
        } catch {
            throw AppError.from(error)
        }
    }
}

/// Firestore anlık dinleyicisini AsyncThrowingStream'e çevirir (ekran kapanınca dinleyici kaldırılır).
extension Query {
    func stream() -> AsyncThrowingStream<QuerySnapshot, Error> {
        AsyncThrowingStream { continuation in
            let reg = addSnapshotListener { snap, err in
                if let err { continuation.finish(throwing: AppError.from(err)) } else if let snap { continuation.yield(snap) }
            }
            continuation.onTermination = { _ in reg.remove() }
        }
    }
}

extension DocumentReference {
    func stream() -> AsyncThrowingStream<DocumentSnapshot, Error> {
        AsyncThrowingStream { continuation in
            let reg = addSnapshotListener { snap, err in
                if let err { continuation.finish(throwing: AppError.from(err)) } else if let snap { continuation.yield(snap) }
            }
            continuation.onTermination = { _ in reg.remove() }
        }
    }
}

extension Dictionary where Key == String, Value == Any {
    func str(_ k: String) -> String? { self[k] as? String }
    func int(_ k: String) -> Int? { (self[k] as? NSNumber)?.intValue }
    func int64(_ k: String) -> Int64? { (self[k] as? NSNumber)?.int64Value }
    func list(_ k: String) -> [[String: Any]] { self[k] as? [[String: Any]] ?? [] }
}
