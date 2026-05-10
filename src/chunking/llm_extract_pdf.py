import fitz  # PyMuPDF
import json
import requests
import re
import os
import sys

LM_STUDIO_URL = "http://127.0.0.1:1234/v1/chat/completions"
MODEL_NAME = "local-model" 
OUTPUT_JSON_FILE = "src/chunking/database/survival_database_en.json"

def clean_with_llm(raw_text):
    system_prompt = """
    You are an expert data extractor. Your task is to extract and reconstruct survival instructions from the provided raw book text.
    Rules:
    1. Output MUST be in pure English.
    2. Ignore all junk text such as copyrights, publisher names, page numbers, or table of contents.
    3. Fix broken sentences and reconstruct them into clear, cohesive survival facts or instructions.
    4. Write ONLY the extracted points in plain text paragraphs. Do not use bullet points or introductory phrases.
    5. IF the page contains NO useful survival information (e.g., it's just a cover, index, or copyright page), reply with EXACTLY ONE WORD: EMPTY.
    """
    
    payload = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"Raw Text:\n{raw_text}"}
        ],
        "temperature": 0.1, 
        "max_tokens": 10000
    }

    try:
        response = requests.post(LM_STUDIO_URL, json=payload)
        response.raise_for_status()
        result = response.json()
        return result["choices"][0]["message"].get("content", "").strip()
    except Exception as e:
        print(f"API Error: {e}")
        return "EMPTY" 

def load_existing_db(output_json):
    """Load existing database if resuming from a previous run."""
    if os.path.exists(output_json):
        with open(output_json, 'r', encoding='utf-8') as f:
            try:
                database = json.load(f)
                print(f"[RESUME] Found existing database with {len(database['parents'])} pages saved.")
                return database
            except json.JSONDecodeError:
                print("[WARNING] Main JSON file is corrupted! Make sure no data is lost.")
                sys.exit(1)
    return {"parents": {}, "children": []}

def save_atomic(database, output_json):
    """Atomically save the database file to survive power loss / abrupt interruption."""
    temp_file = output_json + ".tmp"
    # 1. Write data to a temporary file (.tmp)
    with open(temp_file, 'w', encoding='utf-8') as f:
        json.dump(database, f, indent=2, ensure_ascii=False)
    # 2. Rename .tmp to the final file (instant/atomic operation at OS level)
    os.replace(temp_file, output_json)

def process_pdf_with_llm(pdf_path, output_json):
    print(f"Starting extraction for {pdf_path}...\n")
    doc = fitz.open(pdf_path)
    database = load_existing_db(output_json)
    
    start_page_index = 0
    if database["parents"]:
        processed_pages = [int(k.split('_')[1]) for k in database["parents"].keys()]
        start_page_index = max(processed_pages) 
        print(f"[RESUME] Continuing from Page {start_page_index + 1}...\n")

    try:
        for page_num in range(start_page_index, doc.page_count): 
            actual_page = page_num + 1
            print(f"Processing Page {actual_page} / {doc.page_count}...")
            
            page = doc.load_page(page_num)
            raw_text = re.sub(r'\s+', ' ', page.get_text("text")).strip()
            
            if len(raw_text) < 50:
                print("  -> Skipped (Text too short)")
                continue
                
            clean_text = clean_with_llm(raw_text)
            
            if len(clean_text) < 20 and "EMPTY" in clean_text.upper():
                print("  -> Skipped (LLM rated as EMPTY)")
                continue
                
            clean_text = clean_text.replace('\n- ', '. ').replace('\n* ', '. ').replace('\n', ' ')
            clean_text = re.sub(r'\s+', ' ', clean_text).strip()

            parent_id = f"page_{actual_page}"
            database["parents"][parent_id] = clean_text
            
            sentences = clean_text.split('. ')
            for i, sentence in enumerate(sentences):
                if len(sentence) > 15: 
                    database["children"].append({
                        "id": f"{parent_id}_chunk_{i}",
                        "parent_id": parent_id,
                        "page_number": actual_page,
                        "search_text": sentence.strip() + "."
                    })
                    
            # Safely save after each page
            save_atomic(database, output_json)
            print(f"  -> [Saved] Data for page {actual_page} saved safely.")
            
    except KeyboardInterrupt:
        # Gracefully handle Ctrl+C — all progress before this page is already saved
        print("\n\n[INTERRUPT] Process forcefully stopped by user (Ctrl+C).")
        print("Don't worry — all progress before this page is already saved safely.")
        print("Just run the script again later to resume.")
        sys.exit(0)

process_pdf_with_llm('book/_OceanofPDF.com_The_book_the_ultimate_guide_to_rebuilding_civilisation_-_Hungry_minds.pdf', OUTPUT_JSON_FILE)
# Execute the extraction