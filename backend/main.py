import base64
import os
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()
app = FastAPI(title="VISTA Gemini Vision Proxy")

GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
GEMINI_API_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    f"{GEMINI_MODEL}:generateContent"
)

class AnalyzeRequest(BaseModel):
    image_base64: str


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/analyze")
async def analyze(request: AnalyzeRequest) -> dict[str, Any]:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="GEMINI_API_KEY is not configured on the backend",
        )

    image_bytes = base64.b64decode(request.image_base64, validate=True)
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Image is empty")

    prompt = (
        "Analyze this camera image for an assistive vision app. "
        "Return only valid JSON with this shape: "
        '{"description": string, "objects": [{"label": string, '
        '"confidence": number, "direction": string}]} . '
        "List only clearly visible objects; do not guess or infer hidden objects. "
        "Use confidence values from 0 to 1 based on visual certainty. "
        "Use common object names, and mention stairs, people, animals, obstacles, "
        "and everyday objects only when visibly present."
    )
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt},
                    {
                        "inline_data": {
                            "mime_type": "image/jpeg",
                            "data": base64.b64encode(image_bytes).decode("ascii"),
                        }
                    },
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.1,
        },
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            GEMINI_API_URL,
            params={"key": api_key},
            json=payload,
        )

    if response.is_error:
        raise HTTPException(
            status_code=502,
            detail=f"Gemini request failed with status {response.status_code}",
        )

    data = response.json()
    try:
        text = data["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError) as exc:
        raise HTTPException(
            status_code=502,
            detail="Gemini returned an unexpected response",
        ) from exc

    import json

    cleaned_text = text.strip()
    if cleaned_text.startswith("```"):
        cleaned_text = cleaned_text.split("\n", 1)[-1].rsplit("```", 1)[0].strip()
    try:
        result = json.loads(cleaned_text)
    except json.JSONDecodeError as exc:
        raise HTTPException(
            status_code=502,
            detail="Gemini returned invalid JSON",
        ) from exc

    if not isinstance(result, dict):
        raise HTTPException(status_code=502, detail="Gemini response was not an object")
    return result
