# Nexa News

Agregador de notícias de Angola, Portugal, Brasil, África e do Mundo (Flask + feedparser).
O servidor recolhe as fontes em segundo plano, guarda tudo em memória e serve a página web e a API.

## Como funciona a recolha

Para cada fonte tenta, por ordem: feed conhecido, descoberta automática do RSS, leitura dos títulos
do HTML e, por fim, Google News (`site:domínio`). A estratégia que funciona fica memorizada.
O estado de cada fonte aparece no botão ⓘ da página e em `/api/sources`.

## Correr localmente (Termux ou PC)

    pip install -r requirements.txt
    python app.py

Abra `http://localhost:5000`.

## Publicar no Render

1. Crie um repositório no GitHub com todos estes ficheiros.
2. No Render: New > Blueprint e escolha o repositório (usa o `render.yaml`).
   Alternativa: New > Web Service com
   Build `pip install -r requirements.txt` e
   Start `gunicorn app:app --bind 0.0.0.0:$PORT --workers 1 --threads 8 --timeout 120`.
3. Quando o deploy terminar, abra o URL do serviço.

Mantenha `--workers 1`: a cache fica na memória de um único processo.
No plano gratuito o serviço adormece após cerca de 15 minutos sem visitas e o primeiro acesso demora cerca de 1 minuto.

## API

- `GET /api/news?region=AO&category=Economia&source=angop&q=petroleo&lang=pt&limit=30&offset=0`
- `GET /api/sources`
- `POST /api/refresh` (cabeçalho `X-Refresh-Token` se `REFRESH_TOKEN` estiver definido)
- `GET /health`

Regiões: `AO`, `PT`, `BR`, `AF`, `INT`.

## Adicionar ou ajustar fontes

Edite `news_sources.py`. Basta `id`, nome, região, categoria e URL da página inicial;
o campo `feeds` é opcional. Para desativar uma fonte use `enabled=False`.

## Variáveis de ambiente (todas opcionais)

- `REFRESH_SECONDS` (600): intervalo entre atualizações, mínimo 120
- `MAX_WORKERS` (8): fontes recolhidas em paralelo
- `MAX_AGE_DAYS` (7): idade máxima das notícias guardadas
- `MAX_ARTICLES` (3000): total máximo em memória
- `MAX_ITEMS_PER_SOURCE` (40): notícias lidas por fonte em cada ciclo
- `REFRESH_TOKEN`: protege o `POST /api/refresh`
- `CORS_ORIGINS` (*): origem permitida para consumir a API de outras apps
- `GNEWS_FALLBACK` (1): usar Google News como último recurso
- `ALLOW_INSECURE_FALLBACK` (1): repetir sem verificar o certificado quando o site tem SSL inválido
- `USER_AGENT`: identificação usada nos pedidos
- `DISABLE_BACKGROUND` (0): 1 desliga a atualização automática
- `LOG_LEVEL` (INFO)