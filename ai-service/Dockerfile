FROM python:3.11-slim

WORKDIR /app

# OpenCV için gerekli sistem kütüphaneleri
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Model ağırlığı büyükse docker-compose'da volume mount tercih edilir
# (bkz. AI_MODEL_PATH env değişkeni ve root docker-compose.yml).

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
