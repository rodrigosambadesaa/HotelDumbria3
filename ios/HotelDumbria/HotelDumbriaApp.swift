import SwiftUI

@main
struct HotelDumbriaApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            Group {
                if state.user == nil {
                    LoginView()
                } else {
                    MainTabView()
                }
            }
            .environmentObject(state)
            .alert(
                "Hotel Dumbría",
                isPresented: Binding(
                    get: { state.errorMessage != nil },
                    set: { if !$0 { state.errorMessage = nil } }
                )
            ) {
                Button("Aceptar", role: .cancel) { state.errorMessage = nil }
            } message: {
                Text(state.errorMessage ?? "")
            }
        }
    }
}
