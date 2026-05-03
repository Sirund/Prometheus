//
//  AssistantView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 03/05/26.
//

import SwiftUI

struct AssistantView: View {
    @State private var query: String = ""
    
    var body: some View {
        VStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Protocol Response Card
                    VStack(alignment: .leading, spacing: 8) {
                        Text("EMERGENCY PROTOCOL").font(.caption.bold()).foregroundColor(.prometheusBlue)
                        Text("BLEACH PURIFICATION").font(.title3.bold())
                        Divider().background(Color.prometheusBlue)
                        Text("• Clear Water: 8 drops per gallon\n• Wait time: 30 minutes").font(.body.monospaced())
                    }
                    .padding()
                    .background(Color.cardBackground)
                    .border(Color.prometheusBlue, width: 2)
                }
                .padding()
            }
            
            HStack {
                TextField("TYPE QUERY...", text: $query)
                    .padding()
                    .background(Color.cardBackground)
                    .border(Color.prometheusBlue.opacity(0.5))
                
                Button(action: {}) {
                    Image(systemName: "paperplane.fill")
                        .padding()
                        .background(Color.prometheusBlue)
                        .foregroundColor(.black)
                }
            }
            .padding()
        }
        .background(Color.darkBackground)
    }
}
