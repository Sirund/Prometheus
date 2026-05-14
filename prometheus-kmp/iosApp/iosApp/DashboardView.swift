import SwiftUI

struct DashboardView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        Image(systemName: "checkmark.shield.fill")
                        Text("LOCAL DATA SYNCED")
                            .font(.caption.bold().monospaced())
                        Spacer()
                        Text("V 2.4.0")
                    }
                    .padding(10)
                    .background(Color.prometheusBlue.opacity(0.2))
                    .foregroundColor(.prometheusBlue)

                    Text("DASHBOARD")
                        .font(.title2.bold().monospaced())

                    VStack(spacing: 12) {
                        Button(action: { }) {
                            DashboardRow(title: "INPUT: AUDIO", subtitle: "ASK BY VOICE", icon: "mic")
                        }
                        Button(action: { }) {
                            DashboardRow(title: "INPUT: TEXT", subtitle: "TYPE QUERY", icon: "keyboard")
                        }
                        Button(action: { }) {
                            DashboardRow(title: "INPUT: OPTIC", subtitle: "IDENTIFY PHOTO", icon: "camera")
                        }
                    }
                    .buttonStyle(TacticalButtonStyle())
                }
                .padding()
            }
            .navigationTitle("Prometheus")
            .background(Color.darkBackground)
        }
    }
}

struct DashboardRow: View {
    let title: String; let subtitle: String; let icon: String
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(title).font(.caption.bold()).foregroundColor(.prometheusBlue)
                Text(subtitle).font(.headline).foregroundColor(.white)
            }
            Spacer()
            Image(systemName: icon).font(.title2).foregroundColor(.prometheusBlue)
        }
    }
}
