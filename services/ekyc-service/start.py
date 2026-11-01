import uvicorn
import os
import sys
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv(CURRENT_DIR / ".env")
    token = os.getenv("EKYC_INTERNAL_TOKEN", "ogshop_ekyc_internal_secret_token_secure_random_12345")
    print("=" * 60)
    print("  O.G SHOP - eKYC AI MICROSERVICE (FastAPI)")
    print("  Algorithms: YOLOv11n + VietOCR Transformer + ArcFace 512-d")
    print("  Address   : http://localhost:8001")
    print("  Docs      : http://localhost:8001/docs")
    print(f"  Auth Token: {token}")
    print("=" * 60)
    uvicorn.run("app:app", host="0.0.0.0", port=8001, reload=False)
