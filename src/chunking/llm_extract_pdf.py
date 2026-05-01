import fitz  # PyMuPDF
import json
import requests
import re

# Konfigurasi LM Studio API
LM_STUDIO_URL = "http://127.0.0.1:1234/v1/chat/completions"
# Ganti dengan nama model yang sedang diload di LM Studio (bebas, bisa model ringan)
MODEL_NAME = "local-model" 

def clean_with_llm(raw_text):
    """Mengirim teks mentah ke LLM untuk dibersihkan dan diekstrak intinya."""
    
    # Prompt sistem yang sangat ketat
    system_prompt = """
    Tugas Anda adalah mengekstrak dan merapikan instruksi bertahan hidup (survival) dari teks mentah buku ini.
    Aturan:
    1. Abaikan semua teks sampah seperti hak cipta, nama penerbit, nomor halaman, atau daftar isi.
    2. Perbaiki kalimat yang terpotong menjadi kalimat yang utuh dan baku.
    3. Jika halaman ini TIDAK berisi instruksi atau informasi bertahan hidup yang berguna, JAWAB HANYA DENGAN KATA: KOSONG
    4. Jangan menambahkan percakapan atau pembukaan seperti 'Berikut adalah instruksinya:'. Langsung tuliskan poin-poinnya.
    """

    payload = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"Teks Mentah:\n{raw_text}"}
        ],
        "temperature": 0.1, # Temperature rendah agar LLM tidak halusinasi
        "max_tokens": 100000
    }

    try:
        response = requests.post(LM_STUDIO_URL, json=payload)
        response.raise_for_status()
        result = response.json()
        return result["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"Error API: {e}")
        return "KOSONG" # Anggap kosong jika API gagal

def process_pdf_with_llm(pdf_path, output_json):
    print(f"Mulai memproses {pdf_path} dengan bantuan LLM...")
    doc = fitz.open(pdf_path)
    
    database = {
        "parents": {},
        "children": []
    }
    
    # Untuk uji coba, mari kita proses 10 halaman pertama saja dulu
    # Ubah 'range(10)' menjadi 'range(len(doc))' jika sudah yakin.
    for page_num in range(10): 
        print(f"Memproses Halaman {page_num + 1}...")
        page = doc.load_page(page_num)
        raw_text = page.get_text("text")
        
        # Bersihkan spasi kosong yang ekstrem sebelum dikirim ke LLM
        raw_text = re.sub(r'\s+', ' ', raw_text).strip()
        
        if len(raw_text) < 50:
            print("  -> Halaman diabaikan (terlalu pendek)")
            continue
            
        # Panggil LLM untuk membersihkan teks
        clean_text = clean_with_llm(raw_text)
        
        if "KOSONG" in clean_text.upper():
            print("  -> Halaman diabaikan (dinilai tidak penting oleh LLM)")
            continue
            
        print("  -> Berhasil mengekstrak informasi berguna.")
        
        parent_id = f"page_{page_num + 1}"
        database["parents"][parent_id] = clean_text
        
        # Pemotongan sederhana per kalimat untuk fitur pencarian
        sentences = clean_text.split('. ')
        for i, sentence in enumerate(sentences):
            if len(sentence) > 10:
                database["children"].append({
                    "id": f"{parent_id}_chunk_{i}",
                    "parent_id": parent_id,
                    "page_number": page_num + 1,
                    "search_text": sentence + "."
                })
                
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(database, f, indent=2, ensure_ascii=False)
        
    print(f"\nSelesai! Teks bersih telah tersimpan di {output_json}")

# Jalankan (Pastikan LM Studio nyala)
process_pdf_with_llm('DK Publishing - Essential Survival Skills_ Key Tips and Techniques for the Great Outdoors (2011).pdf', 'survival_database_clean.json')