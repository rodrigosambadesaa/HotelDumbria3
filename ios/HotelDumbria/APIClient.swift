import Foundation

actor APIClient {
    static let shared = APIClient()

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 20
        configuration.httpCookieStorage = nil
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()
    private var accessToken: String?
    private var refreshToken: String?

    func login(email: String, password: String) async throws -> User {
        try await InternetReachability.shared.requireInternet()
        let payload = ["email": email, "password": password]
        let response: SessionResponse = try await jsonRequest(
            url: AppConfiguration.login, method: "POST", body: payload, authenticated: false
        )
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        return try await profile()
    }

    func persistedRefreshToken() -> String? { refreshToken }

    func restore(refreshToken: String) async throws -> User {
        let response: SessionResponse = try await jsonRequest(
            url: AppConfiguration.refresh,
            method: "POST",
            body: ["refreshToken": refreshToken],
            authenticated: false
        )
        accessToken = response.accessToken
        self.refreshToken = response.refreshToken
        return try await profile()
    }

    func register(_ draft: RegistrationDraft) async throws {
        try await InternetReachability.shared.requireInternet()
        let request = RegistrationRequest(
            nationalId: draft.nif,
            email: draft.email,
            password: draft.password,
            firstName: draft.firstName,
            lastName1: draft.firstSurname,
            lastName2: draft.secondSurname,
            gender: nil,
            street: draft.street,
            streetNumber: draft.streetNumber,
            postalCode: draft.postalCode
        )
        let _: MessageResponse = try await jsonRequest(
            url: AppConfiguration.register, method: "POST", body: request, authenticated: false
        )
    }

    func availableRooms(checkIn: Date, checkOut: Date) async throws -> [Room] {
        try await InternetReachability.shared.requireInternet()
        var components = URLComponents(url: AppConfiguration.rooms, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "checkIn", value: checkIn.apiDate),
            URLQueryItem(name: "checkOut", value: checkOut.apiDate),
            URLQueryItem(name: "guests", value: "1")
        ]
        let response: ListResponse<RoomWire> = try await request(
            URLRequest(url: components.url!), authenticated: false
        )
        return response.data.map(Room.init)
    }

    func reserve(rooms: Set<Room>, user: User, checkIn: Date, checkOut: Date) async throws {
        try await InternetReachability.shared.requireInternet()
        for room in rooms {
            let payload = ReservationRequest(
                roomId: room.numero,
                checkIn: checkIn.apiDate,
                checkOut: checkOut.apiDate,
                guests: 1
            )
            let _: ReservationResponse = try await jsonRequest(
                url: AppConfiguration.reservations,
                method: "POST",
                body: payload,
                authenticated: true
            )
        }
    }

    func uploadProfileImage(_ imageData: Data, user: User) async throws {
        try await InternetReachability.shared.requireInternet()
        let boundary = "HotelDumbria-\(UUID().uuidString)"
        var request = URLRequest(url: AppConfiguration.upload)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data.multipart(
            name: "fichero", filename: "foto_perfil.jpg",
            mimeType: "image/jpeg", data: imageData, boundary: boundary
        )
        let _: MessageResponse = try await self.request(request, authenticated: true)
    }

    func signOut() async {
        if let refreshToken {
            do {
                let _: MessageResponse = try await jsonRequest(
                    url: AppConfiguration.logout,
                    method: "POST",
                    body: ["refreshToken": refreshToken],
                    authenticated: false
                )
            } catch {
                // La sesión local se elimina incluso si el servidor no responde.
            }
        }
        accessToken = nil
        refreshToken = nil
    }

    func deleteProfile(password: String) async throws {
        try await InternetReachability.shared.requireInternet()
        let _: MessageResponse = try await jsonRequest(
            url: AppConfiguration.deleteProfile,
            method: "DELETE",
            body: ["password": password],
            authenticated: true
        )
        accessToken = nil
        refreshToken = nil
    }

    private func profile() async throws -> User {
        let wire: ProfileResponse = try await request(
            URLRequest(url: AppConfiguration.me), authenticated: true
        )
        return User(wire)
    }

    private func jsonRequest<Body: Encodable, Output: Decodable>(
        url: URL, method: String, body: Body, authenticated: Bool
    ) async throws -> Output {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return try await self.request(request, authenticated: authenticated)
    }

    private func request<Output: Decodable>(
        _ input: URLRequest, authenticated: Bool
    ) async throws -> Output {
        var request = input
        if authenticated {
            guard let accessToken else { throw AppError.server("La sesión ha caducado.") }
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AppError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? JSONDecoder().decode(APIErrorResponse.self, from: data)
            throw AppError.server(apiError?.message ?? "No se pudo completar la operación.")
        }
        if data.isEmpty, Output.self == MessageResponse.self {
            return MessageResponse(message: "OK") as! Output
        }
        return try JSONDecoder().decode(Output.self, from: data)
    }
}

private extension Date {
    var apiDate: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: self)
    }
}

private extension Data {
    static func multipart(
        name: String, filename: String, mimeType: String, data: Data, boundary: String
    ) -> Data {
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        return body
    }
}
