//
//  LibraryView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 03/05/26.
//

import SwiftUI

struct LibraryView: View {
    let topics = ["Medical", "Shelter", "Navigation", "Food & Water"]
    
    var body: some View {
        NavigationStack {
            List(topics, id: \.self) { topic in
                NavigationLink(destination: Text("Detail for \(topic)")) {
                    HStack {
                        Image(systemName: "folder.fill").foregroundColor(.prometheusBlue)
                        Text(topic).font(.monospaced(.body)())
                    }
                }
                .listRowBackground(Color.cardBackground)
            }
            .navigationTitle("LIBRARY")
            .scrollContentBackground(.hidden)
            .background(Color.darkBackground)
            .searchable(text: .constant(""))
        }
    }
}
