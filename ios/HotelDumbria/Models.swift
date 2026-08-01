import Foundation

struct User: Codable, Identifiable {
    var id: String { nif }
    let nif: String
    let email: String
    let nombre: String
    let apellido_1: String?
    let apellido_2: String?
    let sexo: String?
    let calle: String?
    let numero: Int?
    let piso: Int?
    let puerta: String?
    let escalera: String?
    let bloque: Int?
    let codigo_postal: String?
    let tipo_persona: String?
    let latitude: Double?
    let longitude: Double?
    var imagen: String?

    init(_ profile: ProfileResponse) {
        nif = profile.nationalId
        email = profile.email
        nombre = profile.firstName
        apellido_1 = profile.lastName1
        apellido_2 = profile.lastName2
        sexo = profile.gender
        calle = profile.street
        numero = Int(profile.streetNumber ?? "")
        piso = nil
        puerta = nil
        escalera = nil
        bloque = nil
        codigo_postal = profile.postalCode
        tipo_persona = "usuario"
        latitude = nil
        longitude = nil
        imagen = profile.hasAvatar ? AppConfiguration.upload.absoluteString : nil
    }
}

struct Room: Codable, Identifiable, Hashable {
    var id: Int { numero }
    let numero: Int
    let precio: Decimal
    let area: Int
    let num_huespedes: Int
    let imagen: String?

    var imageURL: URL? {
        imagen.flatMap(URL.init(string:))
    }

    init(_ wire: RoomWire) {
        numero = wire.id
        precio = Decimal(string: wire.pricePerNight) ?? 0
        area = wire.areaM2
        num_huespedes = wire.maxGuests
        imagen = wire.imageUrl
    }
}

struct SessionResponse: Decodable {
    let accessToken: String
    let refreshToken: String
}

struct ProfileResponse: Decodable {
    let nationalId: String
    let email: String
    let firstName: String
    let lastName1: String
    let lastName2: String?
    let gender: String?
    let street: String?
    let streetNumber: String?
    let postalCode: String?
    let hasAvatar: Bool
}

struct RoomWire: Decodable {
    let id: Int
    let pricePerNight: String
    let areaM2: Int
    let maxGuests: Int
    let imageUrl: String?
}

struct ListResponse<T: Decodable>: Decodable { let data: [T] }
struct MessageResponse: Codable { let message: String }
struct APIErrorResponse: Decodable { let message: String }
struct ReservationResponse: Decodable { let id: String; let status: String; let totalPrice: String }

struct RegistrationRequest: Encodable {
    let nationalId: String
    let email: String
    let password: String
    let firstName: String
    let lastName1: String
    let lastName2: String?
    let gender: String?
    let street: String?
    let streetNumber: String?
    let postalCode: String?
}

struct ReservationRequest: Encodable {
    let roomId: Int
    let checkIn: String
    let checkOut: String
    let guests: Int
}

struct BulkReservationRequest: Encodable {
    let reservations: [ReservationRequest]
}

struct RegistrationDraft {
    var nif = ""
    var email = ""
    var firstName = ""
    var firstSurname = ""
    var secondSurname = ""
    var password = ""
    var street = ""
    var streetNumber = ""
    var postalCode = ""
}

enum AppError: LocalizedError {
    case noInternet
    case invalidResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .noInternet:
            return "No hay acceso real a Internet."
        case .invalidResponse:
            return "El servidor devolvió una respuesta no válida."
        case .server(let message):
            return message
        }
    }
}
