//
//  AssistantView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 03/05/26.
//

import SwiftUI

struct AssistantView: View {
    @State private var manager = InferenceManager()
    @State private var query: String = ""
    @State private var chatHistory: [ChatMessage] = []
    
    var body: some View {
        VStack {
            HStack {
                Circle()
                    .fill(manager.isModelLoaded ? .green : .orange)
                    .frame(width: 8, height: 8)
                Text(manager.statusMessage)
                    .font(.caption.monospaced())
                Spacer()
            }
            .padding(.horizontal)
            .foregroundColor(.prometheusBlue)

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(chatHistory) { message in
                            ChatBubble(message: message)
                        }
                    }
                    .padding()
                }
            }
            
            HStack {
                TextField("TYPE QUERY...", text: $query)
                    .textFieldStyle(.plain)
                    .padding()
                    .background(Color.cardBackground)
                    .border(Color.prometheusBlue.opacity(0.5))
                    .disabled(!manager.isModelLoaded)
                
                Button(action: { sendToGemma() }) {
                    Image(systemName: "bolt.fill")
                        .padding()
                        .background(manager.isModelLoaded ? Color.prometheusBlue : Color.gray)
                        .foregroundColor(.black)
                }
                .disabled(!manager.isModelLoaded || query.isEmpty)
            }
            .padding()
        }
        .background(Color.darkBackground)
        .onAppear {
            Task { await manager.setupGemma() }
        }
    }
    
    func sendToGemma() {
        let userText = query
        query = ""
        
        let userMessage = ChatMessage(text: userText, isUser: true)
        chatHistory.append(userMessage)
        
        let aiMessageIndex = chatHistory.count
        chatHistory.append(ChatMessage(text: "...", isUser: false))
        
        Task {
            await manager.sendMessage(userText) { updatedText in
                chatHistory[aiMessageIndex] = ChatMessage(text: updatedText, isUser: false)
            }
        }
    }
}
