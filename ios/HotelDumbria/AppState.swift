import Foundation

@MainActor
final class AppState: ObservableObject {
    @Published var user: User?
    @Published var isWorking = false
    @Published var errorMessage: String?
    @Published var registrationCompleted = false

    func login(email: String, password: String, remember: Bool) async {
        await perform {
            let user = try await APIClient.shared.login(email: email, password: password)
            guard user.tipo_persona == "usuario" else {
                throw AppError.server("Esta aplicación solo es para clientes.")
            }
            if remember {
                if let refresh = await APIClient.shared.persistedRefreshToken() {
                    CredentialStore().save(email: email, refreshToken: refresh)
                }
            } else {
                CredentialStore().delete()
            }
            self.user = user
        }
    }

    func register(_ draft: RegistrationDraft) async {
        await perform {
            try await APIClient.shared.register(draft)
            self.registrationCompleted = true
        }
    }

    func signOut() {
        CredentialStore().delete()
        Task { await APIClient.shared.signOut() }
        user = nil
    }

    func deleteAccount(password: String) async {
        await perform {
            try await APIClient.shared.deleteProfile(password: password)
            CredentialStore().delete()
            self.user = nil
        }
    }

    func perform(_ operation: @escaping () async throws -> Void) async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await operation()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
