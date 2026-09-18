import uvicorn
import os
import sys
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

if __name__ == "__main__":
    print("=" * 60)
    print("  O.G SHOP - eKYC AI MICROSERVICE (FastAPI)")
    print("  Algorithms: YOLOv11n + VietOCR Transformer + ArcFace 512-d")
    print("  Address   : http://localhost:8001")
    print("  Docs      : http://localhost:8001/docs")
    print("=" * 60)
    uvicorn.run("app:app", host="0.0.0.0", port=8001, reload=False)
