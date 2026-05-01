import fitz  # PyMuPDF
import json
import re

def clean_text(text):
    # Membersihkan karakter ganti baris dan spasi berlebih
    text = re.sub(r'\n+', ' ', text)
    text = re.sub(r'\s+', ' ', text)
    return text.strip()

def create_parent_child_db(pdf_path, output_json):
    print(f"Mulai memproses {pdf_path}...")
    doc = fitz.open(pdf_path)
    
    database = {
        "parents": {}, # Menyimpan teks utuh per halaman (Untuk konteks Gemma)
        "children": [] # Menyimpan kalimat pendek (Untuk fitur pencarian lokal di HP)
    }
    
    for page_num in range(len(doc)):
        page = doc.load_page(page_num)
        text = page.get_text("text")
        cleaned_text = clean_text(text)
        
        # Abaikan halaman kosong atau halaman yang isinya hanya nomor/header pendek
        if len(cleaned_text) < 50: 
            continue
            
        # 1. Simpan Parent (Konteks penuh satu halaman)
        parent_id = f"page_{page_num + 1}"
        database["parents"][parent_id] = cleaned_text
        
        # 2. Potong teks menjadi Children (Potongan per kalimat)
        # Kita menggunakan titik sebagai pemisah kalimat sederhana
        sentences = cleaned_text.split('. ')
        
        for i, sentence in enumerate(sentences):
            if len(sentence) > 15: # Abaikan potongan kalimat yang terlalu pendek/kotor
                database["children"].append({
                    "id": f"{parent_id}_chunk_{i}",
                    "parent_id": parent_id,
                    "page_number": page_num + 1,
                    "search_text": sentence + "."
                })
                
    # Simpan ke dalam file JSON
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(database, f, indent=2, ensure_ascii=False)
        
    print(f"Selesai! Mengekstrak {len(database['parents'])} halaman dan {len(database['children'])} potongan kalimat.")
    print(f"File tersimpan sebagai: {output_json}")

# Jalankan fungsi
create_parent_child_db('DK Publishing - Essential Survival Skills_ Key Tips and Techniques for the Great Outdoors (2011).pdf', 'survival_database.json')