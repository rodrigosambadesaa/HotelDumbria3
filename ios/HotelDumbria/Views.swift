import PhotosUI
import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var state: AppState
    @State private var email = ""
    @State private var password = ""
    @State private var remember = false
    @State private var showingRegistration = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Correo electrónico", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                    SecureField("Contraseña", text: $password)
                        .textContentType(.password)
                    Toggle("Recordar sesión", isOn: $remember)
                }
                Section {
                    Button("Iniciar sesión") {
                        Task { await state.login(email: email, password: password, remember: remember) }
                    }
                    .frame(maxWidth: .infinity)
                    .disabled(email.isEmpty || password.isEmpty || state.isWorking)
                    Button("Crear una cuenta") { showingRegistration = true }
                        .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Hotel Dumbría")
            .overlay { if state.isWorking { ProgressView() } }
            .sheet(isPresented: $showingRegistration) { RegistrationView() }
            .onAppear {
                if let saved = CredentialStore().load() {
                    email = saved.email
                    remember = true
                    Task {
                        await state.perform {
                            state.user = try await APIClient.shared.restore(
                                refreshToken: saved.refreshToken
                            )
                        }
                    }
                }
            }
        }
    }
}

struct RegistrationView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var state: AppState
    @State private var draft = RegistrationDraft()

    var body: some View {
        NavigationStack {
            Form {
                Section("Cuenta") {
                    TextField("NIF", text: $draft.nif)
                    TextField("Correo", text: $draft.email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                    SecureField("Contraseña", text: $draft.password)
                }
                Section("Datos personales") {
                    TextField("Nombre", text: $draft.firstName)
                    TextField("Primer apellido", text: $draft.firstSurname)
                    TextField("Segundo apellido", text: $draft.secondSurname)
                    TextField("Calle", text: $draft.street)
                    TextField("Número", text: $draft.streetNumber).keyboardType(.numberPad)
                    TextField("Código postal", text: $draft.postalCode).keyboardType(.numberPad)
                }
                Button("Completar registro") {
                    Task {
                        await state.register(draft)
                        if state.registrationCompleted { dismiss() }
                    }
                }
                .disabled(draft.email.isEmpty || draft.password.isEmpty || state.isWorking)
            }
            .navigationTitle("Crear cuenta")
            .toolbar { Button("Cancelar") { dismiss() } }
            .overlay { if state.isWorking { ProgressView() } }
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            ReservationsView()
                .tabItem { Label("Reservar", systemImage: "bed.double.fill") }
            ProfileView()
                .tabItem { Label("Perfil", systemImage: "person.crop.circle") }
        }
    }
}

struct ReservationsView: View {
    @EnvironmentObject private var state: AppState
    @State private var checkIn = Date().addingTimeInterval(86_400)
    @State private var checkOut = Date().addingTimeInterval(172_800)
    @State private var rooms: [Room] = []
    @State private var selected: Set<Room> = []

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                GroupBox("Fechas de estancia") {
                    DatePicker("Entrada", selection: $checkIn, in: Date()...)
                    DatePicker("Salida", selection: $checkOut, in: checkIn...)
                }
                .padding(.horizontal)

                Button("Buscar habitaciones") {
                    Task {
                        await state.perform {
                            rooms = try await APIClient.shared.availableRooms(
                                checkIn: checkIn,
                                checkOut: checkOut
                            )
                            selected.removeAll()
                        }
                    }
                }
                .buttonStyle(.borderedProminent)

                List(rooms) { room in
                    Button {
                        if selected.contains(room) { selected.remove(room) } else { selected.insert(room) }
                    } label: {
                        HStack(spacing: 14) {
                            AsyncImage(url: room.imageURL) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Rectangle().fill(.quaternary)
                            }
                            .frame(width: 100, height: 90)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 5) {
                                Text("Habitación \(room.numero)").font(.headline)
                                Text("\(room.precio as NSDecimalNumber) €").font(.title3.bold())
                                    .foregroundStyle(.tint)
                                Text("\(room.area) m² · \(room.num_huespedes) huéspedes")
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: selected.contains(room) ? "checkmark.circle.fill" : "circle")
                                .font(.title2)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Reserva")
            .safeAreaInset(edge: .bottom) {
                Button("Confirmar \(selected.count) habitación(es)") {
                    guard let user = state.user else { return }
                    Task {
                        await state.perform {
                            try await APIClient.shared.reserve(
                                rooms: selected,
                                user: user,
                                checkIn: checkIn,
                                checkOut: checkOut
                            )
                            selected.removeAll()
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(selected.isEmpty || state.isWorking)
                .padding()
                .frame(maxWidth: .infinity)
                .background(.regularMaterial)
            }
            .overlay { if state.isWorking { ProgressView() } }
        }
    }
}

struct ProfileView: View {
    @EnvironmentObject private var state: AppState
    @State private var item: PhotosPickerItem?
    @State private var preview: Image?
    @State private var deletionPassword = ""
    @State private var confirmingDeletion = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Spacer()
                        Group {
                            if let preview { preview.resizable().scaledToFill() }
                            else { Image(systemName: "person.crop.circle.fill").resizable() }
                        }
                        .frame(width: 140, height: 140)
                        .clipShape(Circle())
                        .foregroundStyle(.secondary)
                        Spacer()
                    }
                    PhotosPicker(selection: $item, matching: .images) {
                        Label("Cambiar foto", systemImage: "photo")
                            .frame(maxWidth: .infinity)
                    }
                }
                Section("Cuenta") {
                    LabeledContent("Nombre", value: state.user?.nombre ?? "")
                    LabeledContent("Correo", value: state.user?.email ?? "")
                }
                Button("Cerrar sesión", role: .destructive) { state.signOut() }
                Section("Eliminar cuenta") {
                    SecureField("Contraseña actual", text: $deletionPassword)
                        .textContentType(.password)
                    Button("Eliminar definitivamente", role: .destructive) {
                        confirmingDeletion = true
                    }
                    .disabled(deletionPassword.isEmpty || state.isWorking)
                }
            }
            .navigationTitle("Perfil")
            .onChange(of: item) { _, selectedItem in
                guard let selectedItem, let user = state.user else { return }
                Task {
                    await state.perform {
                        guard let data = try await selectedItem.loadTransferable(type: Data.self),
                              let image = UIImage(data: data),
                              let jpeg = image.jpegData(compressionQuality: 0.82) else {
                            throw AppError.invalidResponse
                        }
                        preview = Image(uiImage: image)
                        try await APIClient.shared.uploadProfileImage(jpeg, user: user)
                    }
                }
            }
            .overlay { if state.isWorking { ProgressView() } }
            .confirmationDialog(
                "Se eliminarán la persona, la cuenta, las reservas y el avatar.",
                isPresented: $confirmingDeletion,
                titleVisibility: .visible
            ) {
                Button("Eliminar cuenta", role: .destructive) {
                    Task { await state.deleteAccount(password: deletionPassword) }
                }
                Button("Cancelar", role: .cancel) {}
            }
        }
    }
}
