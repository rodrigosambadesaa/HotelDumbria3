import Foundation
import Security

/// Solo persiste el refresh token; la contraseña nunca se almacena.
struct CredentialStore {
    private let service = "org.hoteldumbria.ios"

    func save(email: String, refreshToken: String) {
        delete()
        let value = "\(email)\n\(refreshToken)".data(using: .utf8)!
        SecItemAdd([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "remembered-session-v2",
            kSecValueData: value,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ] as CFDictionary, nil)
    }

    func load() -> (email: String, refreshToken: String)? {
        var result: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "remembered-session-v2",
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ] as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data,
              let text = String(data: data, encoding: .utf8) else { return nil }
        let parts = text.split(separator: "\n", maxSplits: 1).map(String.init)
        guard parts.count == 2 else { return nil }
        return (parts[0], parts[1])
    }

    func delete() {
        SecItemDelete([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "remembered-session-v2"
        ] as CFDictionary)
    }
}
