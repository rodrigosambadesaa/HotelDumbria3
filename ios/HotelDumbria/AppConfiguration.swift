import Foundation

enum AppConfiguration {
    static let apiBase: URL = {
        if let configured = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
           let url = URL(string: configured), !configured.isEmpty {
            return url
        }
        // Solo para el simulador/desarrollo local. Producción debe inyectar HTTPS.
        return URL(string: "http://127.0.0.1:8080/api/v1")!
    }()

    static var login: URL { apiBase.appending(path: "auth/mobile/login") }
    static var refresh: URL { apiBase.appending(path: "auth/mobile/refresh") }
    static var logout: URL { apiBase.appending(path: "auth/mobile/logout") }
    static var register: URL { apiBase.appending(path: "auth/register") }
    static var me: URL { apiBase.appending(path: "users/me") }
    static var rooms: URL { apiBase.appending(path: "rooms/availability") }
    static var reservations: URL { apiBase.appending(path: "reservations") }
    static var bulkReservations: URL { apiBase.appending(path: "reservations/bulk") }
    static var upload: URL { apiBase.appending(path: "users/me/avatar") }
    static var deleteProfile: URL { apiBase.appending(path: "users/me") }
}
