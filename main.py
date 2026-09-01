from contextlib import asynccontextmanager
from fastapi import FastAPI
import uvicorn

from frontend_loader import FrontendStaticLoader




app = FastAPI(title="VCA‑Mock Service", docs_url="/docs")

app.add_middleware(FrontendStaticLoader)



if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        reload=True
    )
