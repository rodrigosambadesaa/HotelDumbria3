import Foundation

actor InternetReachability {
    static let shared = InternetReachability()

    private let probes = [
        URL(string: "https://www.google.com/generate_204")!,
        URL(string: "https://www.facebook.com/")!,
        URL(string: "https://www.wolframalpha.com/")!,
        URL(string: "https://www.apple.com/")!,
        URL(string: "https://www.amazon.com/")!
    ]

    /// Checks providers in order and returns immediately after the first response.
    /// A provider later in the list is never contacted after a successful probe.
    func firstReachableProvider() async -> URL? {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 1.8
        configuration.timeoutIntervalForResource = 1.8
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        let session = URLSession(configuration: configuration)

        for provider in probes {
            var request = URLRequest(url: provider)
            request.httpMethod = "HEAD"
            request.setValue("close", forHTTPHeaderField: "Connection")
            do {
                let (_, response) = try await session.data(for: request)
                if response is HTTPURLResponse {
                    session.invalidateAndCancel()
                    return provider
                }
            } catch {
                continue
            }
        }
        session.invalidateAndCancel()
        return nil
    }

    func requireInternet() async throws {
        guard await firstReachableProvider() != nil else {
            throw AppError.noInternet
        }
    }
}
