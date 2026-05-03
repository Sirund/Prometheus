//
//  ContentView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 01/05/26.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("DASHBOARD", systemImage: "square.grid.2x2") }
            
            AssistantView()
                .tabItem { Label("ASSISTANT", systemImage: "bolt.horizontal.circle") }
            
            VisionView()
                .tabItem { Label("VISION", systemImage: "viewfinder") }
            
            LibraryView()
                .tabItem { Label("LIBRARY", systemImage: "book.closed") }
        }
        .tint(.prometheusBlue) 
        .preferredColorScheme(.dark)
    }
}
